package org.skepsun.kototoro.explore.data

import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.model.ContentSourceAvailability
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.findCloudFlareException
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.settings.sources.test.SOURCE_CHECK_MAX_AGE_MS
import org.skepsun.kototoro.settings.sources.test.SourceCheck
import org.skepsun.kototoro.settings.sources.test.SourceHealth
import org.skepsun.kototoro.settings.sources.test.SourceTestStep
import org.skepsun.kototoro.settings.sources.test.StepStatus
import org.skepsun.kototoro.settings.sources.test.healthAt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single mapping from a failed availability probe to a stored state: a Cloudflare exception
 * anywhere in the cause chain is CLOUDFLARE, every other failure is EMPTY.
 */
fun classifyAvailability(error: Throwable): ContentSourceAvailability =
    if (error.findCloudFlareException() != null) {
        ContentSourceAvailability.CLOUDFLARE
    } else {
        ContentSourceAvailability.EMPTY
    }

@Singleton
class SourceAvailabilityRepository @Inject constructor(
    private val settings: AppSettings,
) {
    private val invalidatedAt = HashMap<String, Long>()

    fun observeHealth(): Flow<Map<String, SourceHealth>> = settings.observe(KEY_SOURCE_CHECKS)
        .map { readChecks() }
        .distinctUntilChanged()
        .transformLatest { history ->
            val now = System.currentTimeMillis()
            emit(history.mapValues { (_, checks) -> requireNotNull(checks.healthAt(now)) })
            val expirations = history.values.flatten().map { it.checkedAt + SOURCE_CHECK_MAX_AGE_MS }
                .filter { it > now }.distinct().sorted()
            for (expiry in expirations) {
                delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
                val current = System.currentTimeMillis()
                emit(history.mapValues { (_, checks) -> requireNotNull(checks.healthAt(current)) })
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** These reports come from the manual checks. A browse request can only update the quick verdict. */
    @Synchronized
    fun recordChecks(results: Map<String, SourceCheck>) {
        if (results.isEmpty()) return
        val previous = readChecks()
        val history = previous.toMutableMap()
        val catalogAvailability = LinkedHashMap<String, ContentSourceAvailability>()
        for ((name, check) in results) {
            if (check.startedAt <= (invalidatedAt[name] ?: 0L)) continue
            history[name] = (history[name].orEmpty() + check)
                .distinctBy { it.startedAt to it.checkedAt }
                .sortedByDescending { it.checkedAt }
                .take(RECENT_CHECKS_LIMIT)
            if (history[name]?.firstOrNull() == check) {
                catalogAvailability[name] = when (check.steps[SourceTestStep.LIST]?.status) {
                    StepStatus.PASS -> ContentSourceAvailability.AVAILABLE
                    StepStatus.FAIL -> ContentSourceAvailability.EMPTY
                    StepStatus.CLOUDFLARE -> ContentSourceAvailability.CLOUDFLARE
                    StepStatus.SKIP, null -> ContentSourceAvailability.UNKNOWN
                }
            }
        }
        if (history != previous) saveChecks(history)
        setAvailabilities(catalogAvailability)
    }

    private fun readChecks(): Map<String, List<SourceCheck>> {
        val encoded = settings.prefs.getString(KEY_SOURCE_CHECKS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, List<SourceCheck>>>(encoded) }
            .getOrDefault(emptyMap()).filterValues { it.isNotEmpty() }
    }

    private fun saveChecks(checks: Map<String, List<SourceCheck>>) {
        settings.prefs.edit { putString(KEY_SOURCE_CHECKS, Json.encodeToString(checks)) }
    }

    fun observeAvailability(): Flow<Map<String, ContentSourceAvailability>> {
        return settings.observe(KEY_AVAILABLE_SOURCES, KEY_EMPTY_SOURCES, KEY_CLOUDFLARE_SOURCES)
            .map { buildAvailabilityMap() }
    }

    fun getAvailability(source: ContentSource): ContentSourceAvailability {
        return resolveAvailability(
            name = source.name,
            empty = getSet(KEY_EMPTY_SOURCES),
            cloudflare = getSet(KEY_CLOUDFLARE_SOURCES),
            available = getSet(KEY_AVAILABLE_SOURCES),
        )
    }

    /** Forgets a stored CLOUDFLARE verdict (the challenge was solved); EMPTY / AVAILABLE verdicts are left alone. */
    @Synchronized
    fun clearCloudflare(source: ContentSource) {
        val cloudflare = getSet(KEY_CLOUDFLARE_SOURCES)
        if (source.name in cloudflare) {
            settings.prefs.edit { putStringSet(KEY_CLOUDFLARE_SOURCES, cloudflare - source.name) }
        }
    }

    /** Forgets every stored verdict for the named sources (extension updated, reinstalled or removed). */
    @Synchronized
    fun clearAvailability(sourceNames: Collection<String>) {
        if (sourceNames.isEmpty()) return
        setAvailabilities(sourceNames.associateWith { ContentSourceAvailability.UNKNOWN })
        val now = System.currentTimeMillis()
        sourceNames.forEach { invalidatedAt[it] = now }
        val checks = readChecks()
        val remaining = checks - sourceNames.toSet()
        if (remaining.size != checks.size) saveChecks(remaining)
    }

    fun observeAvailability(source: ContentSource): Flow<ContentSourceAvailability> {
        return observeAvailability().map { it[source.name] ?: ContentSourceAvailability.UNKNOWN }
    }

    fun markAvailable(source: ContentSource) {
        // No prefs write when the verdict is already AVAILABLE (called on every successful page).
        if (getAvailability(source) == ContentSourceAvailability.AVAILABLE) return
        setAvailability(source.name, ContentSourceAvailability.AVAILABLE)
    }

    fun markEmpty(source: ContentSource) {
        setAvailability(source.name, ContentSourceAvailability.EMPTY)
    }

    fun setAvailability(source: ContentSource, availability: ContentSourceAvailability) {
        setAvailability(source.name, availability)
    }

    private fun setAvailability(sourceName: String, availability: ContentSourceAvailability) {
        setAvailabilities(mapOf(sourceName to availability))
    }

    /** Applies all [updates] with a single read-modify-write and a single prefs commit. */
    @Synchronized
    fun setAvailabilities(updates: Map<String, ContentSourceAvailability>) {
        if (updates.isEmpty()) return
        val available = getSet(KEY_AVAILABLE_SOURCES).toMutableSet()
        val empty = getSet(KEY_EMPTY_SOURCES).toMutableSet()
        val cloudflare = getSet(KEY_CLOUDFLARE_SOURCES).toMutableSet()
        for ((sourceName, availability) in updates) {
            available.remove(sourceName)
            empty.remove(sourceName)
            cloudflare.remove(sourceName)
            when (availability) {
                ContentSourceAvailability.AVAILABLE -> available += sourceName
                ContentSourceAvailability.EMPTY -> empty += sourceName
                ContentSourceAvailability.CLOUDFLARE -> cloudflare += sourceName
                ContentSourceAvailability.UNKNOWN -> Unit
            }
        }
        settings.prefs.edit {
            putStringSet(KEY_AVAILABLE_SOURCES, available)
            putStringSet(KEY_EMPTY_SOURCES, empty)
            putStringSet(KEY_CLOUDFLARE_SOURCES, cloudflare)
        }
    }

    private fun buildAvailabilityMap(): Map<String, ContentSourceAvailability> {
        val empty = getSet(KEY_EMPTY_SOURCES)
        val available = getSet(KEY_AVAILABLE_SOURCES)
        val cloudflare = getSet(KEY_CLOUDFLARE_SOURCES)
        return buildMap(empty.size + available.size + cloudflare.size) {
            for (name in available + cloudflare + empty) {
                put(name, resolveAvailability(name, empty, cloudflare, available))
            }
        }
    }

    /** The one precedence rule for a name that sits in several sets: EMPTY, then CLOUDFLARE, then AVAILABLE. */
    private fun resolveAvailability(
        name: String,
        empty: Set<String>,
        cloudflare: Set<String>,
        available: Set<String>,
    ): ContentSourceAvailability = when {
        name in empty -> ContentSourceAvailability.EMPTY
        name in cloudflare -> ContentSourceAvailability.CLOUDFLARE
        name in available -> ContentSourceAvailability.AVAILABLE
        else -> ContentSourceAvailability.UNKNOWN
    }

    private fun getSet(key: String): Set<String> {
        return settings.prefs.getStringSet(key, emptySet()).orEmpty()
    }

    private companion object {
        const val KEY_AVAILABLE_SOURCES = AppSettings.KEY_AVAILABLE_SOURCES
        const val KEY_EMPTY_SOURCES = AppSettings.KEY_EMPTY_SOURCES
        const val KEY_CLOUDFLARE_SOURCES = AppSettings.KEY_CLOUDFLARE_SOURCES
        const val KEY_SOURCE_CHECKS = "amuratsu_source_checks"
        const val RECENT_CHECKS_LIMIT = 3
    }
}

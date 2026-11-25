package org.skepsun.kototoro.home.ui

import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import java.util.concurrent.TimeUnit

internal enum class ResumeVisibility { PINNED, SNOOZED, HIDDEN }

internal data class ResumePreference(val visibility: ResumeVisibility, val time: Long = 0) {
    fun isVisible(lastOpenedAt: Long): Boolean = when (visibility) {
        ResumeVisibility.PINNED -> true
        ResumeVisibility.SNOOZED -> time <= System.currentTimeMillis()
        ResumeVisibility.HIDDEN -> lastOpenedAt > time
    }
}

internal class HomeResumePreferences(private val settings: AppSettings) {
    private fun read(): Map<Long, ResumePreference> = settings.prefs.getStringSet(KEY, null).orEmpty().mapNotNull {
        val parts = it.split(':')
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val visibility = ResumeVisibility.entries.firstOrNull { mode -> mode.name == parts.getOrNull(1) }
            ?: return@mapNotNull null
        val time = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
        id to ResumePreference(visibility, time)
    }.toMap()

    fun observe(): Flow<Map<Long, ResumePreference>> = settings.observeAsFlow(KEY) { read() }
        .transformLatest { preferences ->
            val expirations = preferences.values.filter { it.visibility == ResumeVisibility.SNOOZED }
                .map { it.time }.distinct().sorted()
            fun active() = preferences.filterValues {
                it.visibility != ResumeVisibility.SNOOZED || it.time > System.currentTimeMillis()
            }
            emit(active())
            for (expiry in expirations) {
                delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
                emit(active())
            }
        }.distinctUntilChanged()

    fun set(id: Long, value: ResumePreference?): ResumePreference? = synchronized(settings.prefs) {
        val preferences = read().toMutableMap()
        val previous = if (value == null) preferences.remove(id) else preferences.put(id, value)
        settings.prefs.edit {
            putStringSet(KEY, preferences.mapTo(mutableSetOf()) { (key, item) -> "$key:${item.visibility.name}:${item.time}" })
        }
        previous
    }

    companion object {
        private const val KEY = "amuratsu_home_resume"

        fun snooze() = ResumePreference(ResumeVisibility.SNOOZED, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
    }
}

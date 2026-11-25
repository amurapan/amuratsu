package org.skepsun.kototoro.settings.sources.test

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
data class SourceCheck(
    val startedAt: Long,
    val checkedAt: Long,
    val steps: Map<SourceTestStep, StepResult>,
) {
    val canOpenContent: Boolean get() = requiredSteps.all { steps[it]?.status == StepStatus.PASS }

    companion object {
        internal val requiredSteps = listOf(
            SourceTestStep.LIST, SourceTestStep.DETAILS, SourceTestStep.CHAPTERS, SourceTestStep.PAGES,
        )
    }
}

enum class SourceHealthStatus { VERIFIED, INTERMITTENT, PARTIAL, CONTENT_FAILED, UNAVAILABLE, CHALLENGE, STALE }

data class SourceHealth(val latest: SourceCheck, val status: SourceHealthStatus)

internal val SOURCE_CHECK_MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)

internal fun List<SourceCheck>.healthAt(now: Long): SourceHealth? {
    val latest = firstOrNull() ?: return null
    val cutoff = now - SOURCE_CHECK_MAX_AGE_MS
    val status = when {
        latest.checkedAt <= cutoff -> SourceHealthStatus.STALE
        any { it.checkedAt > cutoff && it.canOpenContent != latest.canOpenContent } -> SourceHealthStatus.INTERMITTENT
        latest.canOpenContent -> SourceHealthStatus.VERIFIED
        (latest.steps[SourceTestStep.PAGES]?.passedSamples ?: 0) > 0 -> SourceHealthStatus.PARTIAL
        SourceCheck.requiredSteps.any { latest.steps[it]?.status == StepStatus.CLOUDFLARE } -> SourceHealthStatus.CHALLENGE
        latest.steps[SourceTestStep.LIST]?.status == StepStatus.PASS -> SourceHealthStatus.CONTENT_FAILED
        else -> SourceHealthStatus.UNAVAILABLE
    }
    return SourceHealth(latest, status)
}

internal fun SourceTestResult.toCheck() = SourceCheck(
    startedAt = startedAt,
    checkedAt = checkedAt,
    steps = steps.mapValues { (_, step) -> step.copy(message = "") },
)

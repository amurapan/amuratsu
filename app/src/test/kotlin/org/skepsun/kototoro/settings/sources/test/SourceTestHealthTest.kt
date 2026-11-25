package org.skepsun.kototoro.settings.sources.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceTestHealthTest {

    private val now = 1_800_000_000_000L
    private val pass = StepResult(StepStatus.PASS)
    private val failed = StepResult(StepStatus.FAIL)

    @Test
    fun `catalog responses alone cannot verify a source`() {
        val check = SourceCheck(now, now, mapOf(SourceTestStep.LIST to pass))
        assertFalse(check.canOpenContent)
        assertEquals(SourceHealthStatus.CONTENT_FAILED, listOf(check).healthAt(now)?.status)
    }

    @Test
    fun `a cover failure does not hide a source whose titles open`() {
        val good = check(now, pass)
        val withBadCover = good.copy(steps = good.steps + (SourceTestStep.COVERS to failed))
        assertTrue(withBadCover.canOpenContent)
        assertEquals(SourceHealthStatus.VERIFIED, listOf(withBadCover).healthAt(now)?.status)
    }

    @Test
    fun `a mixture of playable and broken titles is only a partial pass`() {
        val partial = check(now, StepResult(StepStatus.FAIL, sampled = 3, passedSamples = 1))
        assertEquals(SourceHealthStatus.PARTIAL, listOf(partial).healthAt(now)?.status)
    }

    @Test
    fun `a recovered source remains intermittent while its recent failure is relevant`() {
        val recent = check(now, pass)
        val yesterday = check(now - 86_400_000, failed)
        val history = listOf(recent, yesterday)
        assertEquals(SourceHealthStatus.INTERMITTENT, history.healthAt(now)?.status)
        assertEquals(
            SourceHealthStatus.VERIFIED,
            history.healthAt(yesterday.checkedAt + SOURCE_CHECK_MAX_AGE_MS)?.status,
        )
        assertEquals(SourceHealthStatus.STALE, history.healthAt(now + SOURCE_CHECK_MAX_AGE_MS)?.status)
    }

    @Test
    fun `an error page is rejected even when a host declares a video MIME type`() {
        assertFalse(looksLikeVideoStream("video/mp4", "<!DOCTYPE html><html>Broken</html>".toByteArray()))
        assertFalse(looksLikeVideoStream("application/octet-stream", " { \"error\": 404 }".toByteArray()))
        assertTrue(looksLikeVideoStream("text/plain", "#EXTM3U\n#EXTINF:5".toByteArray()))
    }

    private fun check(checkedAt: Long, payload: StepResult) = SourceCheck(
        startedAt = checkedAt - 100,
        checkedAt = checkedAt,
        steps = SourceCheck.requiredSteps.associateWith { if (it == SourceTestStep.PAGES) payload else pass },
    )
}

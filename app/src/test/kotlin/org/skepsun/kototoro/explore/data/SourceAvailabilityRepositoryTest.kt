package org.skepsun.kototoro.explore.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.model.ContentSourceAvailability
import org.skepsun.kototoro.core.model.TestContentSource
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.settings.sources.test.SourceCheck
import org.skepsun.kototoro.settings.sources.test.SourceHealthStatus
import org.skepsun.kototoro.settings.sources.test.SourceTestStep
import org.skepsun.kototoro.settings.sources.test.StepResult
import org.skepsun.kototoro.settings.sources.test.StepStatus
import org.skepsun.kototoro.sync.google.domain.MemorySyncPreferences
import java.io.IOException

class SourceAvailabilityRepositoryTest {

	private val prefs = MemorySyncPreferences()
	private val settings = mockk<AppSettings>().also { settings ->
		every { settings.prefs } returns prefs
		every { settings.observe(any()) } returns flowOf(null)
	}
	private val repository = SourceAvailabilityRepository(settings)

	@Test
	fun `cloudflare anywhere in the cause chain is CLOUDFLARE and everything else EMPTY`() {
		val cloudFlare = CloudFlareProtectedException("https://example.org/", TestContentSource, Headers.headersOf())
		assertEquals(ContentSourceAvailability.CLOUDFLARE, classifyAvailability(IOException("x", cloudFlare)))
		assertEquals(ContentSourceAvailability.EMPTY, classifyAvailability(IOException("boom")))
	}

	@Test
	fun `setAvailabilities moves names between verdict sets in one batch`() {
		repository.setAvailabilities(
			mapOf(
				"a" to ContentSourceAvailability.AVAILABLE,
				"b" to ContentSourceAvailability.EMPTY,
				"c" to ContentSourceAvailability.CLOUDFLARE,
			),
		)
		assertEquals(setOf("a"), prefs.getStringSet(AppSettings.KEY_AVAILABLE_SOURCES, null))
		assertEquals(setOf("b"), prefs.getStringSet(AppSettings.KEY_EMPTY_SOURCES, null))
		assertEquals(setOf("c"), prefs.getStringSet(AppSettings.KEY_CLOUDFLARE_SOURCES, null))

		repository.setAvailabilities(
			mapOf("a" to ContentSourceAvailability.EMPTY, "b" to ContentSourceAvailability.UNKNOWN),
		)
		assertEquals(emptySet<String>(), prefs.getStringSet(AppSettings.KEY_AVAILABLE_SOURCES, null))
		assertEquals(setOf("a"), prefs.getStringSet(AppSettings.KEY_EMPTY_SOURCES, null))
		assertEquals(setOf("c"), prefs.getStringSet(AppSettings.KEY_CLOUDFLARE_SOURCES, null))
	}

	@Test
	fun `empty batch does not touch the prefs`() {
		repository.setAvailabilities(emptyMap())
		assertEquals(false, prefs.contains(AppSettings.KEY_AVAILABLE_SOURCES))
	}

	@Test
	fun `a later quick probe preserves a saved failed content check`() = runBlocking {
		val now = System.currentTimeMillis()
		val check = SourceCheck(now - 100, now, mapOf(
			SourceTestStep.LIST to StepResult(StepStatus.PASS),
			SourceTestStep.PAGES to StepResult(StepStatus.FAIL),
		))
		repository.recordChecks(mapOf(TestContentSource.name to check))
		repository.markAvailable(TestContentSource)
		val reloaded = SourceAvailabilityRepository(settings)
		assertEquals(ContentSourceAvailability.AVAILABLE, reloaded.getAvailability(TestContentSource))
		assertEquals(
			SourceHealthStatus.CONTENT_FAILED,
			reloaded.observeHealth().first()[TestContentSource.name]?.status,
		)
	}

	@Test
	fun `a package update clears the report and ignores a check already in flight`() = runBlocking {
		val now = System.currentTimeMillis()
		val pass = StepResult(StepStatus.PASS)
		val check = SourceCheck(now - 100, now, SourceCheck.requiredSteps.associateWith { pass })
		val name = TestContentSource.name
		repository.recordChecks(mapOf(name to check))
		assertEquals(SourceHealthStatus.VERIFIED, repository.observeHealth().first()[name]?.status)

		repository.clearAvailability(listOf(name))
		repository.recordChecks(mapOf(name to check))
		assertEquals(emptyMap<String, Any>(), repository.observeHealth().first())
		assertEquals(ContentSourceAvailability.UNKNOWN, repository.getAvailability(TestContentSource))
	}
}

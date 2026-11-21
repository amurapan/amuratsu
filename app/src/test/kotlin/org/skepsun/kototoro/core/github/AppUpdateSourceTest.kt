package org.skepsun.kototoro.core.github

import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class AppUpdateSourceTest {

	@Test
	fun `locales default to GitHub while GitCode route is temporarily disabled`() {
		assertEquals(
			AppUpdateSource.GITHUB,
			preferredUpdateSource(Locale.forLanguageTag("zh-Hans-SG")),
		)
		assertEquals(
			AppUpdateSource.GITHUB,
			preferredUpdateSource(Locale.Builder().setLanguage("en").setRegion("CN").build()),
		)
		assertEquals(
			AppUpdateSource.GITHUB,
			preferredUpdateSource(Locale.US),
		)
	}

	@Test
	fun `update source values remain stable for preferences`() {
		assertEquals("github", AppUpdateSource.GITHUB.value)
		assertEquals("gitcode", AppUpdateSource.GITCODE.value)
		assertEquals(AppUpdateSource.GITHUB, AppUpdateSource.fromValue("github"))
		assertEquals(AppUpdateSource.GITCODE, AppUpdateSource.fromValue("gitcode"))
		assertEquals(null, AppUpdateSource.fromValue("unknown"))
	}

	@Test
	fun `GitCode releases use tag version and select matching ABI`() {
		val releases = JSONArray(
			"""
			[
			  {
			    "tag_name": "v2.0.0",
			    "name": "Kototoro 2.0.0",
			    "body": "Release notes",
			    "assets": [
			      {
			        "name": "Kototoro-2.0.0-x86-release.apk",
			        "browser_download_url": "https://example.test/x86.apk"
			      },
			      {
			        "name": "Kototoro-2.0.0-arm64-v8a-release.apk",
			        "browser_download_url": "https://example.test/arm64.apk"
			      }
			    ]
			  }
			]
			""".trimIndent(),
		)

		val version = parseUpdateReleases(
			jsonArray = releases,
			source = AppUpdateSource.GITCODE,
			repository = "2401_87187946/Kototoro",
			supportedAbis = listOf("arm64-v8a"),
		).single()

		assertEquals("2.0.0", version.name)
		assertEquals(AppUpdateSource.GITCODE, version.source)
		assertEquals("https://example.test/arm64.apk", version.apkUrl)
		assertEquals(0L, version.apkSize)
		assertEquals("https://gitcode.com/2401_87187946/Kototoro/releases/v2.0.0", version.url)
	}

	@Test
	fun `GitCode nightly tag maps to nightly app version`() {
		val releases = JSONArray(
			"""
			[
			  {
			    "tag_name": "nightly-20260816",
			    "assets": [
			      {
			        "name": "Kototoro-N20260816-universal-nightly.apk",
			        "browser_download_url": "https://example.test/nightly.apk"
			      }
			    ]
			  }
			]
			""".trimIndent(),
		)

		val version = parseUpdateReleases(
			jsonArray = releases,
			source = AppUpdateSource.GITCODE,
			repository = "2401_87187946/Kototoro-Nightly",
			supportedAbis = listOf("arm64-v8a"),
		).single()

		assertEquals("N20260816", version.name)
		assertEquals("https://example.test/nightly.apk", version.apkUrl)
	}

    @Test
    fun `Amuratsu updates select a public release APK for this ABI and use the tag version`() {
        val repo = "amurapan/amuratsu"
        val tag = "v0.1.0-alpha.4"
        val prefix = "https://github.com/$repo/releases/download/$tag/"
        val releases = JSONArray(
            """
            [{
                "tag_name": "$tag",
                "name": "Amuratsu Alpha",
                "assets": [
                    {"name":"Amuratsu-N20261121-arm64-v8a-nightly.apk","browser_download_url":"${prefix}Amuratsu-N20261121-arm64-v8a-nightly.apk"},
                    {"name":"Amuratsu-0.1.0-alpha.4-arm64-v8a-preview.apk","browser_download_url":"${prefix}Amuratsu-0.1.0-alpha.4-arm64-v8a-preview.apk"},
                    {"name":"Amuratsu-0.1.0-alpha.4-arm64-v8a-release.apk","browser_download_url":"${prefix}Amuratsu-0.1.0-alpha.4-arm64-v8a-release.apk"}
                ]
            }]
            """.trimIndent(),
        )

        val version = parseUpdateReleases(releases, AppUpdateSource.GITHUB, repo, listOf("arm64-v8a")).single()
        assertEquals("0.1.0-alpha.4", version.name)
        assertEquals("${prefix}Amuratsu-0.1.0-alpha.4-arm64-v8a-release.apk", version.apkUrl)
        assertEquals(emptyList<AppVersion>(), parseUpdateReleases(releases, AppUpdateSource.GITHUB, repo, listOf("x86")))
    }

}

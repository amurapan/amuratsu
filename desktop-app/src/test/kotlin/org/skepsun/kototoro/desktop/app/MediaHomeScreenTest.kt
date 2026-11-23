package org.skepsun.kototoro.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.ui.home.*
import org.skepsun.kototoro.core.video.CinemaKind
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class MediaHomeScreenTest {
    @Test
    fun `cinema separates movies series and anime without changing card actions`() {
        for (width in listOf(360, 1000)) runDesktopComposeUiTest(width = width, height = 1000) {
            var selected by mutableStateOf(HomeMediaKind.ALL)
            var cinemaTab by mutableStateOf(CinemaKind.MOVIE)
            var opened: Long? = null
            var resumed: Long? = null
            val videos = listOf(
                MediaHomeItem(10, "Film", HomeMediaKind.CINEMA, cinemaKind = CinemaKind.MOVIE),
                MediaHomeItem(11, "Series", HomeMediaKind.CINEMA, cinemaKind = CinemaKind.SERIES),
                MediaHomeItem(12, "Anime", HomeMediaKind.ANIME),
            )
            setContent {
                DesktopTheme {
                    HomeFixture(
                        selected = selected,
                        recent = videos,
                        onKind = { selected = it },
                        onOpen = { opened = it.id },
                        onResume = { resumed = it.id },
                        cinemaTab = cinemaTab,
                        onCinemaTabSelected = { cinemaTab = it },
                    )
                }
            }
            onNodeWithTag("media-home-filter:CINEMA").performClick().assertIsSelected()
            onNodeWithTag("cinema-tab:MOVIE").assertIsSelected()
            onNodeWithTag("media-home-details:10").assertIsDisplayed().performClick()
            onNodeWithTag("media-home-details:11").assertDoesNotExist()
            onNodeWithTag("media-home-details:12").assertDoesNotExist()
            onNodeWithTag("cinema-tab:SERIES").performClick().assertIsSelected()
            onNodeWithTag("media-home-details:10").assertDoesNotExist()
            onNodeWithTag("media-home-resume:11").assertIsDisplayed().performClick()
            runOnIdle {
                assertEquals(10L, opened)
                assertEquals(11L, resumed)
            }
            onNodeWithTag("media-home-filter:ANIME").performClick().assertIsSelected()
            onNodeWithTag("cinema-tabs").assertDoesNotExist()
            onNodeWithTag("media-home-details:12").assertIsDisplayed()
        }
    }

    @Test
    fun `loading never displays first launch prompt and import remains reachable on small screens`() {
        runDesktopComposeUiTest(width = 320, height = 640) {
            var initialized by mutableStateOf(false)
            var imports = 0
            var sources = 0
            setContent {
                DesktopTheme {
                    HomeFixture(initialized = initialized, onImport = { imports++ }, onSources = { sources++ })
                }
            }
            onNodeWithTag("media-home-loading").assertIsDisplayed()
            onNodeWithTag("media-home-empty").assertDoesNotExist()
            runOnIdle { initialized = true }
            onNodeWithTag("media-home-loading").assertDoesNotExist()
            onNodeWithTag("media-home-empty").assertExists()
            onNodeWithTag("media-home-add").performClick()
            onNodeWithTag("media-home-import").assertIsDisplayed().performClick()
            assertEquals(1, imports)
            onNodeWithTag("media-home-add").performClick()
            onNodeWithTag("media-home-add-sources").performClick()
            assertEquals(1, sources)
        }
    }

    @Test
    fun `format selection search details and resume have independent callbacks`() {
        runDesktopComposeUiTest(width = 420, height = 1000) {
            var selected by mutableStateOf(HomeMediaKind.ALL)
            var searches = 0
            var opened: Long? = null
            var resumed: Long? = null
            setContent {
                DesktopTheme {
                    HomeFixture(selected = selected, recent = examples,
                        onKind = { selected = it }, onSearch = { searches++ },
                        onOpen = { opened = it.id }, onResume = { resumed = it.id })
                }
            }
            onNodeWithTag("media-home-filter:BOOKS").performClick().assertIsSelected()
            assertEquals(HomeMediaKind.BOOKS, selected)
            assertEquals(0, searches)
            onNodeWithTag("media-home-search").performClick()
            assertEquals(1, searches)
            assertEquals(HomeMediaKind.BOOKS, selected)
            onNodeWithTag("media-home-details:1").performClick()
            assertEquals(1L, opened)
            assertNull(resumed)
            onNodeWithTag("media-home-resume:1").performClick()
            assertEquals(1L, resumed)
            onNodeWithTag("media-home-resume:2").performClick()
            assertEquals(2L, resumed)
            assertEquals(1L, opened, "A nested resume button must not also open details")
        }
    }

    @Test
    fun `phone and wide home share content with readable light and dark layouts`() {
        for (appearance in listOf(DesktopAppearance.LIGHT, DesktopAppearance.DARK)) {
            for (width in listOf(360, 1100)) runDesktopComposeUiTest(width = width, height = 1000) {
                setContent {
                    DesktopTheme(appearance) {
                        HomeFixture(recent = examples, updates = examples.map { it.copy(updateText = "+2") })
                    }
                }
                onNodeWithTag("media-home-resume:1").assertIsDisplayed()
                if (width >= 720) {
                    onNodeWithTag("media-home-columns").assertExists()
                    val resume = onNodeWithTag("media-home-details:1").fetchSemanticsNode().boundsInRoot
                    val update = onNodeWithTag("media-home-update:1").fetchSemanticsNode().boundsInRoot
                    assertTrue(update.left > resume.right)
                } else {
                    onNodeWithTag("media-home-columns").assertDoesNotExist()
                    onNodeWithTag("media-home-scroll").performScrollToNode(hasTestTag("media-home-update:1"))
                    onNodeWithTag("media-home-update:1").assertIsDisplayed()
                    onNodeWithTag("media-home-scroll").performScrollToNode(hasTestTag("media-home-search"))
                }
                val path = Path.of("build/reports/desktop-smoke/material-home-$width-${appearance.name.lowercase()}.png")
                Files.createDirectories(path.parent)
                Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).use { image ->
                    image.encodeToData()?.use { Files.write(path, it.bytes) }
                }
            }
        }
    }
}

private val examples = listOf(
    MediaHomeItem(1, "Город за облаками", HomeMediaKind.MANGA, "Стр. 12 · 35%", .35f),
    MediaHomeItem(2, "Письма из далёкого сада", HomeMediaKind.BOOKS, "18%", .18f),
    MediaHomeItem(3, "Последняя станция", HomeMediaKind.ANIME, "На 12:08", .52f),
)

@Composable
private fun HomeFixture(
    initialized: Boolean = true,
    selected: HomeMediaKind = HomeMediaKind.ALL,
    recent: List<MediaHomeItem> = emptyList(),
    updates: List<MediaHomeItem> = emptyList(),
    onKind: (HomeMediaKind) -> Unit = {},
    onSearch: () -> Unit = {},
    onSources: () -> Unit = {},
    onImport: () -> Unit = {},
    onOpen: (MediaHomeItem) -> Unit = {},
    onResume: (MediaHomeItem) -> Unit = {},
    cinemaTab: CinemaKind = CinemaKind.MOVIE,
    onCinemaTabSelected: ((CinemaKind) -> Unit)? = null,
) {
    MediaHomeScreen(selected, recent, updates, emptyList(), initialized,
        onKind, onSearch, {}, {}, {}, {}, onSources, onOpen, onResume,
        cover = { _, modifier -> Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer)) },
        onImport = onImport, cinemaTab = cinemaTab, onCinemaTabSelected = onCinemaTabSelected)
}

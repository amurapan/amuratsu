package org.skepsun.kototoro.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.ui.settings.AuthorSupportCard
import org.skepsun.kototoro.core.ui.settings.authorSupportLinks
import org.skepsun.kototoro.core.ui.text.LocalUiStrings
import org.skepsun.kototoro.core.ui.text.UiStrings

@OptIn(ExperimentalTestApi::class)
class AuthorSupportCardTest {
    @Test
    fun `configured profiles open their corresponding address in narrow and wide layouts`() {
        val koFi = "https://ko-fi.com/test-creator"
        val buyMeACoffee = "https://www.buymeacoffee.com/test-creator"
        for (width in listOf(300, 760)) runDesktopComposeUiTest(width = width, height = 420) {
            val openedUrls = mutableListOf<String>()
            setContent {
                DesktopTheme {
                    CompositionLocalProvider(LocalUiStrings provides supportStrings(koFi, buyMeACoffee)) {
                        AuthorSupportCard(authorSupportLinks(), onOpenLink = { openedUrls.add(it.url) })
                    }
                }
            }
            onNodeWithText("Ko-fi").assertIsDisplayed().performClick()
            onNodeWithText("Buy Me a Coffee").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(listOf(koFi, buyMeACoffee), openedUrls) }
        }
    }

    @Test
    fun `empty or off-site URLs leave the card hidden`() = runDesktopComposeUiTest(width = 380, height = 420) {
        setContent {
            DesktopTheme {
                val strings = supportStrings("", "https://buymeacoffee.com.example.org/test-creator")
                CompositionLocalProvider(LocalUiStrings provides strings) {
                    Box(Modifier.fillMaxSize()) {
                        AuthorSupportCard(authorSupportLinks(), onOpenLink = { error("Unexpected link: $it") })
                    }
                }
            }
        }
        onNodeWithText(DesktopUiStrings.string("about_author_support")).assertDoesNotExist()
        onNodeWithText("Ko-fi").assertDoesNotExist()
        onNodeWithText("Buy Me a Coffee").assertDoesNotExist()
    }

    private fun supportStrings(koFi: String, buyMeACoffee: String): UiStrings = object : UiStrings by DesktopUiStrings {
        override fun string(name: String): String = when (name) {
            "url_author_support_kofi" -> koFi
            "url_author_support_buy_me_a_coffee" -> buyMeACoffee
            else -> DesktopUiStrings.string(name)
        }
    }
}

package org.skepsun.kototoro.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.ui.text.uiString

data class AuthorSupportLink(val title: String, val url: String)

@Composable
fun authorSupportLinks(): List<AuthorSupportLink> = listOfNotNull(
    authorSupportLink("Ko-fi", uiString("url_author_support_kofi"), "ko-fi.com"),
    authorSupportLink("Buy Me a Coffee", uiString("url_author_support_buy_me_a_coffee"), "buymeacoffee.com"),
)

private fun authorSupportLink(title: String, url: String, host: String): AuthorSupportLink? {
    val address = url.trim()
    return if (profileUrl.matchEntire(address)?.groupValues?.get(1) == host) {
        AuthorSupportLink(title, address)
    } else {
        null
    }
}

private val profileUrl = Regex("""https://(?:www\.)?([^/]+)/[a-zA-Z0-9_-]+/?""")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorSupportCard(
    links: List<AuthorSupportLink>,
    onOpenLink: (AuthorSupportLink) -> Unit,
    enabled: Boolean = true,
) {
    if (links.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(uiString("about_author_support"), style = MaterialTheme.typography.titleLarge)
            Text(uiString("about_author_support_summary"), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { link ->
                    FilledTonalButton(onClick = { onOpenLink(link) }, enabled = enabled) {
                        Text(link.title)
                    }
                }
            }
        }
    }
}

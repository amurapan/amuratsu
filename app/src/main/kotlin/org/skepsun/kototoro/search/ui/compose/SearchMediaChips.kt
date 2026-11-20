package org.skepsun.kototoro.search.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.search.MediaFilterChips
import org.skepsun.kototoro.search.domain.SearchContentKind

@Composable
internal fun SearchMediaChips(
    selected: Set<SearchContentKind>,
    onSelect: (Set<SearchContentKind>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaFilterChips(
        options = SearchContentKind.entries,
        selected = selected,
        label = { kind ->
            stringResource(
                when (kind) {
                    SearchContentKind.MANGA -> R.string.content_type_manga
                    SearchContentKind.NOVEL -> R.string.media_books
                    SearchContentKind.VIDEO -> R.string.media_anime_video
                },
            )
        },
        key = { it.name },
        onSelect = onSelect,
        modifier = modifier,
    )
}

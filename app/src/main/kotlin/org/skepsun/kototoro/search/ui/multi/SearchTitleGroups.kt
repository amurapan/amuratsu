package org.skepsun.kototoro.search.ui.multi

import org.skepsun.kototoro.core.model.cinemaKind
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.video.CinemaKind
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.parsers.model.ContentType
import java.text.Normalizer
import java.util.Locale

internal data class SearchTitleGroups(
    val items: List<ListModel> = emptyList(),
    val variants: Map<Long, List<ContentListModel>> = emptyMap(),
    val groupedIds: Set<Long> = emptySet(),
)

private data class SearchTitleKey(val title: String, val type: ContentType, val cinemaKind: CinemaKind?)

private val spaces = Regex("\\s+")

private fun String.normalizedTitle() = Normalizer.normalize(this, Normalizer.Form.NFKC)
    .trim().replace(spaces, " ").lowercase(Locale.ROOT)

internal fun groupSearchTitles(items: List<ListModel>): SearchTitleGroups {
    val candidates = items.filterIsInstance<SearchResultsListModel>()
        .filter { it.titleResId == 0 && it.error == null }.flatMap { it.list }.distinctBy { it.id }
    val matches = candidates.groupBy { item ->
        SearchTitleKey(item.manga.title.normalizedTitle(), item.source.getContentType(), item.manga.cinemaKind())
    }.values.filter { group ->
        if (group.size < 2 || group.first().manga.title.count(Char::isLetterOrDigit) < 3) return@filter false
        // Multiple same-name works from one source, or conflicting author credits, make the match ambiguous.
        val hasOneMatchPerSource = group.map { it.source.name }.distinct().size == group.size
        val knownAuthors = group.map { item -> item.manga.authors.map { it.normalizedTitle() }.toSet() }
            .filter { it.isNotEmpty() }
        hasOneMatchPerSource && knownAuthors.distinct().size <= 1
    }
    return SearchTitleGroups(items, matches.associateBy { it.first().id },
        matches.flatten().mapTo(HashSet()) { it.id })
}

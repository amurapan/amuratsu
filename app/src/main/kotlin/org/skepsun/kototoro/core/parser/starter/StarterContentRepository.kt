package org.skepsun.kototoro.core.parser.starter

import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.parsers.InternalParsersApi
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.SortOrder

@OptIn(InternalParsersApi::class)
internal class StarterContentRepository(
    override val source: StarterSource,
    private val catalog: StarterCatalog,
) : ContentRepository {
    override val sortOrders = setOf(SortOrder.ALPHABETICAL)
    override var defaultSortOrder = SortOrder.ALPHABETICAL
    override val filterCapabilities = ContentListFilterCapabilities(isSearchSupported = true)

    override suspend fun getFilterOptions() = ContentListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> {
        val terms = filter?.query.orEmpty().trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return catalog.list(source).filter { title ->
            val searchText = (title.altTitles + title.title + title.author).joinToString(" ")
            terms.all { searchText.contains(it, ignoreCase = true) }
        }.sortedBy(StarterTitle::title).drop(offset.coerceAtLeast(0)).map { it.toContent() }
    }

    override suspend fun getDetails(manga: Content): Content =
        catalog.list(source).first { it.id == manga.id }.toContent(includeChapters = true)

    override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> {
        if (source.contentType == ContentType.NOVEL) return emptyList()
        return findChapter(chapter).files.mapIndexed { index, file ->
            ContentPage(chapter.id * 100 + index, file, null, source)
        }
    }

    override suspend fun getChapterContent(chapter: ContentChapter, nextChapterUrl: String?): NovelChapterContent? {
        if (source.contentType != ContentType.NOVEL) return null
        return NovelChapterContent(catalog.text(source, findChapter(chapter).files.single()))
    }

    override suspend fun getPageUrl(page: ContentPage): String = catalog.localUrl(source, page.url)

    override suspend fun fetchPageResponse(pageUrl: String, page: ContentPage) = catalog.response(source, page.url)

    suspend fun fetchCoverResponse(url: String) = catalog.response(source, requireNotNull(url.starterCoverFile()))

    override suspend fun getRelated(seed: Content): List<Content> =
        catalog.list(source).filter { it.id != seed.id }.map { it.toContent() }

    override fun isSlowdownEnabled() = false

    private suspend fun findChapter(chapter: ContentChapter): StarterChapter {
        val title = catalog.list(source).first { it.id == chapter.id / 1000 }
        return title.chapters[(chapter.id % 1000).toInt() - 1]
    }

    private fun StarterTitle.toContent(includeChapters: Boolean = false) = Content(
        id = id,
        title = title,
        altTitles = altTitles,
        url = "starter://title/$id",
        publicUrl = url,
        rating = -1f,
        contentRating = null,
        coverUrl = cover?.let(::starterCoverUrl),
        tags = emptySet(),
        state = null,
        authors = setOf(author),
        description = credits,
        chapters = chapters.takeIf { includeChapters }?.mapIndexed { index, chapter ->
            ContentChapter(
                id = id * 1000 + index + 1,
                title = chapter.title,
                number = (index + 1).toFloat(),
                volume = 0,
                url = "starter://chapter/$id/${index + 1}",
                scanlator = null,
                uploadDate = 0,
                branch = null,
                source = source,
            )
        },
        source = source,
    )
}

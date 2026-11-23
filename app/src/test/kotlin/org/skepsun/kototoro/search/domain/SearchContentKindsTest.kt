package org.skepsun.kototoro.search.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

class SearchContentKindsTest {
    @Test
    fun `general search keeps all video results and cinema filters narrow a mixed provider`() {
        val titles = listOf(video("Movie"), video("TvSeries"), video("Anime"), video(null))

        assertEquals(titles, titles.filter { content -> ALL_SEARCH_CONTENT_KINDS.any { it.matches(content) } })
        assertEquals(listOf(titles[0]), titles.filter(SearchContentKind.MOVIE::matches))
        assertEquals(listOf(titles[1]), titles.filter(SearchContentKind.SERIES::matches))
        assertTrue(SearchContentKind.MOVIE.matches(mixedVideoSource))
        assertTrue(SearchContentKind.SERIES.matches(mixedVideoSource))
    }

    private val mixedVideoSource = object : ContentSource {
        override val name = "CLOUDSTREAM_fixture_Mixed"
        override val locale = "en"
        override val contentType = ContentType.VIDEO
    }

    private fun video(type: String?) = Content(
        id = type.hashCode().toLong(),
        title = type ?: "Untyped video",
        altTitles = emptySet(),
        url = "https://example.invalid/$type",
        publicUrl = "https://example.invalid/$type",
        rating = 0f,
        contentRating = null,
        coverUrl = null,
        tags = emptySet(),
        state = null,
        authors = emptySet(),
        source = mixedVideoSource,
        sourceData = type?.let { """{"type":"$it"}""" },
    )
}

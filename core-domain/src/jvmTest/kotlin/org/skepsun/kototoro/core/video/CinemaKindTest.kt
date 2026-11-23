package org.skepsun.kototoro.core.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CinemaKindTest {

    @Test
    fun `only declared film and series kinds enter cinema`() {
        val source = "CLOUDSTREAM_test_video"
        assertEquals(CinemaKind.MOVIE, CinemaKind.fromSourceData(source, """{"type":"Movie"}"""))
        assertEquals(CinemaKind.SERIES, CinemaKind.fromSourceData(source, """{"type":"TvSeries"}"""))
        assertEquals(CinemaKind.SERIES, CinemaKind.fromSourceData(source, """{"type":"AsianDrama"}"""))
        for (type in listOf("Anime", "AnimeMovie", "OVA", "Live", "Video")) {
            assertNull(CinemaKind.fromSourceData(source, """{"type":"$type"}"""))
        }
        assertNull(CinemaKind.fromSourceData(source, "malformed"))
        assertNull(CinemaKind.fromSourceData(source, "{}"))
        assertNull(CinemaKind.fromSourceData("ANIYOMI_test", """{"type":"Movie"}"""))
    }

    @Test
    fun `cinema and anime share video playback with separate library rows`() {
        for (kind in CinemaKind.entries) {
            assertTrue(CinemaFilter.ONLY.accepts(kind))
            assertFalse(CinemaFilter.EXCLUDE.accepts(kind))
        }
        assertFalse(CinemaFilter.ONLY.accepts(null))
        assertTrue(CinemaFilter.EXCLUDE.accepts(null))
    }
}

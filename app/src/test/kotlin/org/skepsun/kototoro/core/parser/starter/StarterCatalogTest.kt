package org.skepsun.kototoro.core.parser.starter

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.model.ContentType

class StarterCatalogTest {
    private val directory = File("src/main/assets/starter")
    private val entries = Json.decodeFromString<List<StarterTitle>>(File(directory, "catalog.json").readText())

    @Test
    fun `each collection contains complete local entries with stable ids`() {
        assertEquals(StarterSource.entries.toSet(), entries.map { it.source }.toSet())
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id in 1..Long.MAX_VALUE / 100_000 })
        for (entry in entries) {
            assertTrue(entry.credits.isNotBlank() && entry.url.startsWith("https://"), entry.title)
            assertTrue(entry.chapters.size in 1..999, entry.title)
            val expectedExtension = when (entry.source.contentType) {
                ContentType.VIDEO -> "mp4"
                ContentType.NOVEL -> "html"
                else -> "jpg"
            }
            for (chapter in entry.chapters) {
                assertTrue(chapter.files.isNotEmpty(), entry.title)
                for (file in chapter.files) assertAsset(file, expectedExtension)
            }
            assertAsset(requireNotNull(entry.cover), "jpg")
        }
    }

    @Test
    fun `the starter series has all three distinct episodes`() {
        val series = entries.single { it.source == StarterSource.AMURATSU_STARTER_SERIES }
        assertEquals(listOf("Llama Drama", "Gran Dillama", "Llamigos"), series.chapters.map { it.title })
        assertEquals(3, series.chapters.flatMap { it.files }.distinct().size)
    }

    private fun assertAsset(name: String, extension: String) {
        assertTrue('/' !in name && '\\' !in name && name.endsWith(".$extension"), name)
        val file = File(directory, name)
        assertTrue(file.isFile && file.length() > 32, name)
    }
}

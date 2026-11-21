package org.skepsun.kototoro.local.data.importer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.history.data.HistoryEntity
import org.skepsun.kototoro.parsers.util.longHashCode

class EpubReimportProgressTest {

    private val previousPath = "/storage/emulated/0/Android/data/org.skepsun.kototoro.debug/files/novel/Evening-station.epub"
    private val previous = HistoryEntity(
        mangaId = previousPath.longHashCode(),
        createdAt = 10,
        updatedAt = 20,
        chapterId = "$previousPath#chapter1".longHashCode(),
        page = 3,
        scroll = 2500f,
        percent = 0.125f,
        deletedAt = 0,
        chaptersCount = 2,
        parentChapterId = null,
        furthestChapterId = "$previousPath#chapter1".longHashCode(),
    )

    @Test
    fun `reimport carries the position and read through to the new chapter ids`() {
        val actual = remapEpubPosition(previous, previousPath, 12, mapOf(0 to 101L, 1 to 102L))
        assertEquals(previous.copy(mangaId = 12, chapterId = 102, furthestChapterId = 102), actual)
    }

    @Test
    fun `different chapter layout cannot inherit a saved position`() {
        assertNull(remapEpubPosition(previous, previousPath, 12, mapOf(0 to 101L)))
        assertNull(remapEpubPosition(previous, previousPath, 12, mapOf(0 to 101L, 2 to 103L)))
    }

    @Test
    fun `only imports in the public novel directory probe the previous packages`() {
        assertTrue(previousEpubPaths("/storage/shared/Evening-station.epub").isEmpty())
        assertEquals(
            listOf(previousPath, previousPath.replace("kototoro.debug", "kototoro")),
            previousEpubPaths(previousPath.replace("org.skepsun.kototoro.debug", "io.github.amurapan.amuratsu")),
        )
    }
}

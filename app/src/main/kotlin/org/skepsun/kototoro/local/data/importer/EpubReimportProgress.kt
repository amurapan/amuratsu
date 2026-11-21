package org.skepsun.kototoro.local.data.importer

import android.net.Uri
import androidx.room.withTransaction
import dagger.Reusable
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toEntity
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.history.data.HistoryEntity
import org.skepsun.kototoro.local.data.input.LocalContentParser
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.local.epub.parseEpubChapterIndex
import org.skepsun.kototoro.parsers.util.longHashCode
import org.skepsun.kototoro.reader.ui.FULLY_READ_CHAPTER_ID
import java.io.File
import javax.inject.Inject
import kotlin.time.Clock

/** Restores the position of an EPUB imported from the old Android app's private novel directory. */
@Reusable
class EpubReimportProgress @Inject constructor(
    private val database: MangaDatabase,
    private val contentRepository: ContentDataRepository,
) {
    suspend fun restore(content: LocalContent, requestedName: String) {
        val book = content.manga
        val file = content.toUri().takeIf { it.scheme == "file" }?.path?.let(::File) ?: return
        if (book.source != LocalNovelSource || file.name != requestedName) return
        val previousPaths = previousEpubPaths(file.path)
        if (previousPaths.isEmpty()) return

        val storedBook = book.toEntity()
        val historyDao = database.getHistoryDao()
        if (historyDao.findIncludingDeleted(book.id) != null) return
        val candidates = previousPaths.mapNotNull { path ->
            val id = path.longHashCode()
            val previous = database.getMangaDao().find(id)?.manga ?: return@mapNotNull null
            val previousPath = Uri.parse(previous.url).takeIf { it.scheme == "file" }?.path
            if (previous.source != storedBook.source || previousPath != path ||
                previous.title != storedBook.title || previous.authors != storedBook.authors
            ) {
                return@mapNotNull null
            }
            historyDao.find(id)?.let { path to it }
        }
        val (previousPath, previousHistory) = candidates.singleOrNull() ?: return
        val chapters = LocalContentParser(file).getContent(withDetails = true).manga.chapters.orEmpty()
        val chapterIds = chapters.mapNotNull { chapter ->
            parseEpubChapterIndex(chapter.url)?.let { index -> index to chapter.id }
        }.toMap()
        val recovered = remapEpubPosition(previousHistory, previousPath, book.id, chapterIds) ?: return

        database.withTransaction {
            if (historyDao.findIncludingDeleted(book.id) == null && historyDao.find(previousHistory.mangaId) == previousHistory) {
                contentRepository.storeContent(book, replaceExisting = false)
                historyDao.upsert(recovered.copy(updatedAt = Clock.System.now().toEpochMilliseconds()))
            }
        }
    }
}

internal fun previousEpubPaths(path: String): List<String> {
    val marker = "/Android/data/io.github.amurapan.amuratsu/files/novel/"
    val prefix = path.substringBefore(marker, "")
    val name = path.substringAfter(marker, "")
    if (!prefix.startsWith('/') || '/' in name || !name.endsWith(".epub", ignoreCase = true)) return emptyList()
    return listOf("org.skepsun.kototoro.debug", "org.skepsun.kototoro").map { oldPackage ->
        "$prefix/Android/data/$oldPackage/files/novel/$name"
    }
}

internal fun remapEpubPosition(
    previous: HistoryEntity,
    previousPath: String,
    currentId: Long,
    chapterIds: Map<Int, Long>,
): HistoryEntity? {
    if (chapterIds.isEmpty() || previous.chaptersCount != chapterIds.size) return null
    val newIdsByOldId = chapterIds.mapKeys { (index, _) -> "$previousPath#chapter$index".longHashCode() }
    val active = newIdsByOldId[previous.chapterId]
        ?: previous.chapterId.takeIf { it == FULLY_READ_CHAPTER_ID }
        ?: return null
    val parent = previous.parentChapterId?.let { newIdsByOldId[it] ?: return null }
    val furthest = when (previous.furthestChapterId) {
        0L, FULLY_READ_CHAPTER_ID -> previous.furthestChapterId
        else -> newIdsByOldId[previous.furthestChapterId] ?: return null
    }
    return previous.copy(mangaId = currentId, chapterId = active, parentChapterId = parent, furthestChapterId = furthest)
}

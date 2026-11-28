package org.skepsun.kototoro.core.parser.starter

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import org.skepsun.kototoro.BuildConfig

@Serializable
internal data class StarterTitle(
    val id: Long,
    val source: StarterSource,
    val title: String,
    val altTitles: Set<String> = emptySet(),
    val author: String,
    val credits: String,
    val url: String,
    val cover: String? = null,
    val chapters: List<StarterChapter>,
)

@Serializable
internal data class StarterChapter(val title: String? = null, val files: List<String>)

@Singleton
class StarterCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    private val titles by lazy {
        context.assets.open("starter/catalog.json").bufferedReader().use {
            Json.decodeFromString<List<StarterTitle>>(it.readText())
        }
    }
    private val filesBySource by lazy {
        titles.groupBy(StarterTitle::source).mapValues { (_, titles) ->
            titles.flatMap { title -> listOfNotNull(title.cover) + title.chapters.flatMap(StarterChapter::files) }.toSet()
        }
    }
    private val copyMutex = Mutex()

    internal suspend fun list(source: StarterSource): List<StarterTitle> = withContext(Dispatchers.IO) {
        titles.filter { it.source == source }
    }

    internal suspend fun text(source: StarterSource, file: String): String = withContext(Dispatchers.IO) {
        open(source, file).bufferedReader().use { it.readText() }
    }

    internal suspend fun localUrl(source: StarterSource, file: String): String = withContext(Dispatchers.IO) {
        copyMutex.withLock {
            requireFile(source, file)
            val directory = File(context.cacheDir, "starter-${BuildConfig.VERSION_CODE}")
            val destination = File(directory, file)
            if (!destination.isFile) {
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot prepare starter cache")
                val pending = File.createTempFile("starter-", ".tmp", directory)
                try {
                    open(source, file).use { input -> pending.outputStream().use { input.copyTo(it) } }
                    if (!pending.renameTo(destination)) throw IOException("Cannot materialize starter content")
                } finally {
                    pending.delete()
                }
            }
            Uri.fromFile(destination).toString()
        }
    }

    internal suspend fun response(source: StarterSource, file: String): Response = withContext(Dispatchers.IO) {
        val mime = when (file.substringAfterLast('.')) {
            "jpg" -> "image/jpeg"
            "mp4" -> "video/mp4"
            else -> "text/html"
        }.toMediaType()
        val request = Request.Builder().url("https://starter.invalid/$file").build()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").header("Content-Type", mime.toString())
            .body(open(source, file).source().buffer().asResponseBody(mime)).build()
    }

    private fun open(source: StarterSource, file: String) =
        context.assets.open("starter/${file.also { requireFile(source, it) }}")

    private fun requireFile(source: StarterSource, file: String) {
        require(file in filesBySource[source].orEmpty() && '/' !in file && '\\' !in file) { "Unknown starter asset" }
    }
}

internal fun starterCoverUrl(file: String): String = "file:///android_asset/starter/$file"

internal fun String.starterCoverFile(): String? {
    val uri = toUri()
    return uri.lastPathSegment?.takeIf { this == starterCoverUrl(it) }
}

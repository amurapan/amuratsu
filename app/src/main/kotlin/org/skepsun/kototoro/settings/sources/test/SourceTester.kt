package org.skepsun.kototoro.settings.sources.test

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.model.ContentSourceAvailability
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.explore.data.classifyAvailability
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the reader flow (list, covers, details, chapters, pages, random, search) for one source.
 * Works for every source type through [ContentRepository.Factory].
 */
@Singleton
class SourceTester @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val repositoryFactory: ContentRepository.Factory,
	@ContentHttpClient private val okHttp: OkHttpClient,
) {

	/**
	 * [onStep] is called as soon as each step finishes, so callers can show partial results.
	 * Steps that never ran because the source deadline expired are recorded as FAIL.
	 */
	suspend fun test(
		source: ContentSource,
		onStep: (SourceTestStep, StepResult) -> Unit = { _, _ -> },
	): SourceTestResult {
		val startedAt = System.nanoTime()
		val startedAtMillis = System.currentTimeMillis()
		val steps = LinkedHashMap<SourceTestStep, StepResult>()
		val record: (SourceTestStep, StepResult) -> Unit = { step, result ->
			steps[step] = result
			onStep(step, result)
		}
		val repo = runCatchingCancellable { repositoryFactory.create(source) }.getOrNull()
		if (repo == null) {
			SourceTestStep.entries.forEach { record(it, failed(R.string.source_test_msg_no_repo)) }
		} else {
			val completed = withTimeoutOrNull(SOURCE_TEST_SOURCE_TIMEOUT_MS) { runFlow(repo, record) } != null
			if (!completed) {
				SourceTestStep.entries.forEach { step ->
					if (step !in steps) {
						record(
							step,
							failed(R.string.source_test_msg_total_timeout, SOURCE_TEST_SOURCE_TIMEOUT_MS / 1000),
						)
					}
				}
			}
		}
		return SourceTestResult(
			sourceKey = source.name,
			title = source.getTitle(context),
			steps = steps.toMap(),
			durationMs = elapsedMs(startedAt),
			startedAt = startedAtMillis,
		)
	}

	private suspend fun runFlow(repo: ContentRepository, record: (SourceTestStep, StepResult) -> Unit) {
		var items: List<Content> = emptyList()
		val listOk = runStep(SourceTestStep.LIST, record) {
			items = repo.getList(0, repo.defaultSortOrder, ContentListFilter.EMPTY)
			if (items.isEmpty()) {
				failed(R.string.source_test_msg_empty)
			} else {
				passed(R.string.source_test_msg_count, items.size)
			}
		}

		val candidates = items.take(SOURCE_TEST_COVER_SAMPLE)
		runOrSkip(SourceTestStep.COVERS, record, listOk) {
			if (candidates.isEmpty()) return@runOrSkip failed(R.string.source_test_msg_no_items)
			val client = repo.getImageClient() ?: okHttp
			val errors = candidates.mapNotNull { content ->
				val url = content.coverUrl ?: return@mapNotNull text(R.string.source_test_msg_no_cover)
				val response = client.newCall(repo.createCoverRequest(url)).await()
				verifyImage(response)
			}
			if (errors.isEmpty()) {
				passed(R.string.source_test_msg_covers, candidates.size, candidates.size)
			} else {
				failed(
					R.string.source_test_msg_covers_failed,
					candidates.size - errors.size,
					candidates.size,
					errors.first(),
				)
			}
		}

		val details = ArrayList<Content>()
		runOrSkip(SourceTestStep.DETAILS, record, listOk) {
			val sample = items.distinctBy { it.id }.take(SOURCE_TEST_DETAILS_SAMPLE)
			testSample(sample) { item ->
				val loaded = repo.getDetails(item, ContentRepository.DetailsFetchMode.FORCE_REFRESH)
				details += loaded
				passed(R.string.source_test_msg_details, loaded.title)
			}
		}

		val chapters = details.mapNotNull { it.chapters?.firstOrNull() }
		runOrSkip(SourceTestStep.CHAPTERS, record, details.isNotEmpty()) {
			summarizeSamples(details.map { item ->
				val count = item.chapters.orEmpty().size
				if (count == 0) {
					failed(R.string.source_test_msg_no_chapters)
				} else {
					passed(R.string.source_test_msg_chapters, count)
				}
			})
		}

		runOrSkip(SourceTestStep.PAGES, record, chapters.isNotEmpty()) {
			testSample(chapters) { chapter -> checkPayload(repo, chapter) }
		}

		// No per-source random capability exists in the parser API (only app-level random suggestions).
		record(SourceTestStep.RANDOM, skipped(R.string.source_test_msg_unsupported))

		if (!repo.filterCapabilities.isSearchSupported) {
			record(SourceTestStep.SEARCH, skipped(R.string.source_test_msg_search_unsupported))
		} else {
			runStep(SourceTestStep.SEARCH, record) {
				val hit = SOURCE_TEST_SEARCH_QUERIES.firstNotNullOfOrNull { query ->
					val count = repo.getList(0, repo.defaultSortOrder, ContentListFilter(query = query)).size
					if (count > 0) query to count else null
				}
				if (hit == null) {
					failed(R.string.source_test_msg_search_empty, SOURCE_TEST_SEARCH_QUERIES.joinToString())
				} else {
					passed(R.string.source_test_msg_search_hit, hit.first, hit.second)
				}
			}
		}
	}

	private suspend fun checkPayload(repo: ContentRepository, chapter: ContentChapter): StepResult {
		val pages = repo.getPages(chapter)
		val page = pages.firstOrNull() ?: return if (repo.getChapterContent(chapter)?.html.isNullOrBlank()) {
			failed(R.string.source_test_msg_no_pages)
		} else {
			passed(R.string.source_test_msg_text_chapter)
		}
		val pageUrl = repo.getPageUrl(page)
		val response = repo.fetchPageResponse(pageUrl, page)
			?: (repo.getImageClient() ?: okHttp).newCall(repo.createPageRequest(pageUrl, page)).await()
		if (repo.source.contentType.isVideo()) {
			val error = verifyStream(response)
			return if (error == null) passed(R.string.source_test_msg_video_stream, pages.size)
			else failed(R.string.source_test_msg_stream_failed, error)
		}
		val error = verifyImage(response)
		return if (error == null) passed(R.string.source_test_msg_pages, pages.size)
		else failed(R.string.source_test_msg_image_failed, error)
	}

	private suspend fun <T> testSample(sample: List<T>, test: suspend (T) -> StepResult): StepResult {
		if (sample.isEmpty()) return failed(R.string.source_test_msg_no_items)
		val perTitleTimeout = (SOURCE_TEST_STEP_TIMEOUT_MS - 1_000) / sample.size
		return summarizeSamples(sample.map { item -> check(perTitleTimeout) { test(item) } })
	}

	private fun summarizeSamples(results: List<StepResult>): StepResult {
		val passed = results.count { it.status == StepStatus.PASS }
		val problem = results.firstOrNull { it.status == StepStatus.FAIL }
			?: results.firstOrNull { it.status == StepStatus.CLOUDFLARE }
		val sampleLabel = text(R.string.source_test_sample_progress, passed, results.size)
		val reason = (problem ?: results.singleOrNull())?.message.orEmpty()
		return StepResult(
			status = problem?.status ?: StepStatus.PASS,
			message = listOf(sampleLabel, reason).filter(String::isNotBlank).joinToString("\n"),
			sampled = results.size,
			passedSamples = passed,
		)
	}

	/** Runs [block] under the per-step timeout, records the timed result and returns true on PASS. */
	private suspend fun runStep(
		step: SourceTestStep,
		record: (SourceTestStep, StepResult) -> Unit,
		block: suspend () -> StepResult,
	): Boolean {
		val startedAt = System.nanoTime()
		val result = check(SOURCE_TEST_STEP_TIMEOUT_MS, block)
		val timed = result.copy(durationMs = elapsedMs(startedAt))
		record(step, timed)
		return timed.status == StepStatus.PASS
	}

	private suspend fun check(timeoutMs: Long, block: suspend () -> StepResult): StepResult =
		withTimeoutOrNull(timeoutMs) {
			runCatchingCancellable { block() }.getOrElse { e ->
				if (isCloudflareBlocked(e)) {
					StepResult(StepStatus.CLOUDFLARE, text(R.string.source_test_msg_cloudflare))
				} else {
					StepResult(StepStatus.FAIL, describe(e))
				}
			}
		} ?: failed(R.string.source_test_msg_timeout, timeoutMs / 1000)

	private suspend fun runOrSkip(
		step: SourceTestStep,
		record: (SourceTestStep, StepResult) -> Unit,
		ready: Boolean,
		block: suspend () -> StepResult,
	): Boolean {
		if (ready) {
			return runStep(step, record, block)
		}
		record(step, skipped(R.string.source_test_msg_prev_failed))
		return false
	}

	/** Returns null when the response is HTTP 200 and holds an image, otherwise a short reason. */
	private fun verifyImage(response: Response): String? = response.use { r ->
		if (!r.isSuccessful) return@use "HTTP ${r.code}"
		// Only the header bytes are needed to tell an image from an error page; downloading whole pages
		// made slow hosts hit the step timeout when ten sources ran at once.
		val bytes = runCatching { r.peekBody(VERIFY_PEEK_BYTES).bytes() }.getOrNull()
		if (bytes == null || bytes.isEmpty()) return@use text(R.string.source_test_msg_empty_body)
		val contentType = r.header("Content-Type").orEmpty()
		if (contentType.startsWith("image/", ignoreCase = true)) return@use null
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
		if (bounds.outWidth > 0 && bounds.outHeight > 0) {
			null
		} else {
			"Content-Type: ${contentType.ifEmpty { "-" }}"
		}
	}

	private fun verifyStream(response: Response): String? = response.use { r ->
		if (!r.isSuccessful) return@use "HTTP ${r.code}"
		val head = runCatching { r.peekBody(VERIFY_STREAM_PEEK_BYTES).bytes() }.getOrNull()
		if (head == null || head.isEmpty()) return@use text(R.string.source_test_msg_empty_body)
		val contentType = r.header("Content-Type").orEmpty()
		if (looksLikeVideoStream(contentType, head)) null else "Content-Type: ${contentType.ifEmpty { "-" }}"
	}

	private companion object {
		const val VERIFY_PEEK_BYTES = 64L * 1024
		const val VERIFY_STREAM_PEEK_BYTES = 4L * 1024
	}

	/** Same decision as the bulk availability probes: any Cloudflare exception in the cause chain. */
	private fun isCloudflareBlocked(e: Throwable): Boolean =
		classifyAvailability(e) == ContentSourceAvailability.CLOUDFLARE

	private fun describe(e: Throwable): String {
		val message = e.message?.take(160)
		return if (message.isNullOrBlank()) e.javaClass.simpleName else "${e.javaClass.simpleName}: $message"
	}

	private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

	private fun passed(id: Int, vararg args: Any) = StepResult(StepStatus.PASS, text(id, *args))

	private fun failed(id: Int, vararg args: Any) = StepResult(StepStatus.FAIL, text(id, *args))

	private fun skipped(id: Int, vararg args: Any) = StepResult(StepStatus.SKIP, text(id, *args))

	private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

private fun ContentType.isVideo(): Boolean = this == ContentType.VIDEO || this == ContentType.HENTAI_VIDEO

/**
 * Whether a response looks like a playable video stream (progressive MP4/WebM, HLS or DASH manifest, or
 * an opaque binary stream) rather than an HTML error page.
 */
internal fun looksLikeVideoStream(contentType: String, head: ByteArray): Boolean {
	val type = contentType.substringBefore(';').trim().lowercase()
	val text = head.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
	if (text.startsWith("<html", ignoreCase = true) || text.startsWith("<!doctype html", ignoreCase = true) ||
		text.startsWith("{")
	) return false
	if (type.startsWith("video/") || type.startsWith("audio/")) return true
	if (type.contains("mpegurl") || type.contains("dash+xml")) return true
	if (text.startsWith("#EXTM3U")) return true
	if (head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp") return true
	if (head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
		head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
	) {
		return true // Matroska / WebM
	}
	return type == "application/octet-stream" || type == "binary/octet-stream"
}

package org.skepsun.kototoro.details.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.ContentIntent
import org.skepsun.kototoro.details.ui.model.DetailsOrigin
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

class DetailsOriginContentTest {

	@Test
	fun `local content origin keeps the content seed for initial details loading`() {
		val content = content("MH1234")
		val origin = DetailsOrigin.LocalMangaContent(
			org.skepsun.kototoro.core.model.parcelable.ParcelableContent(content),
		)

		val intent = origin.initialContentIntentOrNull()

		assertSame(content, intent?.manga)
		assertEquals(content.id, intent?.mangaId)
	}

	@Test
	fun `details can load from a link without a numeric path segment`() {
		val link = mockk<Uri> { every { lastPathSegment } returns "manga" }
		val intent = ContentIntent(SavedStateHandle(mapOf(AppRouter.KEY_DATA to link)))

		assertEquals(ContentIntent.ID_NONE, intent.mangaId)
		assertSame(link, intent.uri)
		assertTrue(intent.hasTarget)
		assertFalse(ContentIntent(SavedStateHandle()).hasTarget)
	}

	private fun content(sourceName: String) = Content(
		id = 1L,
		title = "Title",
		altTitles = emptySet(),
		url = "",
		publicUrl = "",
		rating = 0f,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		source = object : ContentSource {
			override val name = sourceName
			override val locale = ""
			override val contentType = ContentType.MANGA
		},
	)
}

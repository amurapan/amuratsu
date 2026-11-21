package org.skepsun.kototoro.home.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ContentHistory
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.looksLikeLocalNovelContent
import org.skepsun.kototoro.core.model.looksLikeLocalVideoContent
import org.skepsun.kototoro.core.ui.compose.ScrollToTopEffect
import org.skepsun.kototoro.core.ui.home.HomeMediaKind
import org.skepsun.kototoro.core.ui.home.MediaHomeItem
import org.skepsun.kototoro.core.ui.home.MediaHomeScreen
import org.skepsun.kototoro.home.ui.HomeContentTab
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.home.ui.toHomeTab
import org.skepsun.kototoro.parsers.model.Content

@Composable
fun HomeScreen(
    state: HomeSummaryState,
    contentPadding: PaddingValues,
    onKindSelected: (HomeContentTab?) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onUpdates: () -> Unit,
    onRecommendations: () -> Unit,
    onManageSources: () -> Unit,
    onImport: () -> Unit,
    onOpen: (Content) -> Unit,
    onResume: (Content) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val stateHolder = rememberSaveableStateHolder()
    val contents = remember(state.resumeState.content, state.recentHistoryItems, state.recentUpdates, state.recommendations) {
        buildList {
            state.resumeState.content?.let(::add)
            state.recentHistoryItems.forEach { add(it.content) }
            state.recentUpdates.forEach { add(it.content) }
            state.recommendations.forEach { add(it.content) }
        }.associateBy { it.id }
    }
    fun item(content: Content, history: ContentHistory? = null, newChapters: Int = 0): MediaHomeItem {
        val kind = content.homeMediaKind()
        val progress = (history?.percent ?: state.resumeState.progressPercent
            ?.takeIf { state.resumeState.content?.id == content.id }?.div(100f))
            ?.takeIf { it.isFinite() && it in 0f..1f }
        val supportingText = when {
            kind == HomeMediaKind.ANIME && history != null -> {
                val seconds = history.page.coerceAtLeast(0) / 1000
                context.getString(R.string.media_home_video_position, seconds / 60, seconds % 60)
            }
            progress != null && kind == HomeMediaKind.MANGA && history != null -> context.getString(
                R.string.media_home_page_progress, history.page + 1, (progress * 100).toInt(),
            )
            progress != null -> context.getString(R.string.home_resume_progress, (progress * 100).toInt())
            else -> ""
        }
        return MediaHomeItem(
            id = content.id,
            title = content.title,
            kind = kind,
            progress = progress,
            supportingText = supportingText,
            subtitle = if (kind == HomeMediaKind.BOOKS) content.authors.firstOrNull().orEmpty() else "",
            updateText = if (newChapters > 0) context.getString(
                if (kind == HomeMediaKind.ANIME) R.string.media_home_new_episodes else R.string.media_home_new_chapters,
                newChapters,
            ) else "",
        )
    }
    val recent = remember(state.recentHistoryItems, state.resumeState, configuration) {
        state.recentHistoryItems.map { item(it.content, it.history) }
    }
    val updates = remember(state.recentUpdates, state.resumeState, configuration) {
        state.recentUpdates.map { item(it.content, newChapters = it.newChapters) }
    }
    val recommendations = remember(state.recommendations, state.resumeState, configuration) {
        state.recommendations.map { item(it.content) }
    }
    val kind = state.selectedTab.toMediaKind()
    stateHolder.SaveableStateProvider(kind.name) {
        val listState = rememberLazyListState()
        ScrollToTopEffect { listState.scrollToItem(0) }
        MediaHomeScreen(
            selectedKind = kind,
            recent = recent,
            updates = updates,
            recommendations = recommendations,
            initialized = state.isInitialized,
            contentPadding = contentPadding,
            listState = listState,
            onKindSelected = { onKindSelected(it.toContentTab()) },
            onSearch = onSearch,
            onSettings = onSettings,
            onHistory = onHistory,
            onLibrary = onLibrary,
            onUpdates = onUpdates,
            onRecommendations = onRecommendations,
            onManageSources = onManageSources,
            onImport = onImport,
            onOpen = { model -> contents[model.id]?.let(onOpen) },
            onResume = { model -> contents[model.id]?.let(onResume) },
            cover = { model, modifier ->
                Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(model.title.take(1), style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    contents[model.id]?.let { content ->
                        AsyncImage(
                            model = rememberHomeCoverRequest(context, content, false, "media-home"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
        )
    }
}

internal fun Content.homeMediaKind(): HomeMediaKind = when {
    looksLikeLocalVideoContent() -> HomeMediaKind.ANIME
    looksLikeLocalNovelContent() -> HomeMediaKind.BOOKS
    else -> source.getContentType().toHomeTab().toMediaKind()
}

internal fun HomeContentTab?.toMediaKind(): HomeMediaKind = when (this) {
    HomeContentTab.MANGA -> HomeMediaKind.MANGA
    HomeContentTab.NOVEL -> HomeMediaKind.BOOKS
    HomeContentTab.VIDEO -> HomeMediaKind.ANIME
    null -> HomeMediaKind.ALL
}

internal fun HomeMediaKind.toContentTab(): HomeContentTab? = when (this) {
    HomeMediaKind.MANGA -> HomeContentTab.MANGA
    HomeMediaKind.BOOKS -> HomeContentTab.NOVEL
    HomeMediaKind.ANIME -> HomeContentTab.VIDEO
    HomeMediaKind.ALL -> null
}

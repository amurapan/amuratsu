package org.skepsun.kototoro.desktop.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.skepsun.kototoro.core.library.filter
import org.skepsun.kototoro.core.source.SourceContent
import org.skepsun.kototoro.core.ui.home.HomeMediaKind
import org.skepsun.kototoro.core.ui.home.MediaHomeItem
import org.skepsun.kototoro.core.ui.home.MediaHomeScreen
import org.skepsun.kototoro.core.ui.text.LocalUiStrings
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab

/** The same personal home as Android. Runtime, source browsing and readers stay with the desktop host. */
@Composable
internal fun DesktopHomePanel(controller: DesktopController, state: DesktopAppState, enabled: Boolean) {
    val strings = LocalUiStrings.current
    val ecosystems = remember(state.sources) { state.sources.associate { it.source.name to it.ecosystem } }
    val filter = state.homeFilter
    val history = remember(state.homeHistory, filter, ecosystems) {
        state.homeHistory.entries
            .filter { filter.accepts(ecosystems[it.content.source.name], it.content.source.contentType) }
            .sortedByDescending { it.lastReadAt ?: it.updatedAt }
    }
    val blacklist = remember(state.sourceSettings.tagBlacklist) { state.sourceSettings.tagBlacklistRules() }
    val updates = remember(state.feed, state.feedContents, filter, ecosystems, blacklist) {
        state.feed.updateRowsByOwnerId.values
            .sortedByDescending { it.lastChapterDate ?: it.lastCheckTime }
            .mapNotNull { row -> state.feedContents[row.displayMangaId ?: row.mangaId]?.let { it to row.newChapters } }
            .filter { (content, _) -> filter.accepts(ecosystems[content.source.name], content.source.contentType) }
            .filterNot { (content, _) -> content.tags.any { blacklist.containsTagTitle(it.title) } }
    }
    val recommendations = remember(state.suggestions, state.suggestionSettings, filter, ecosystems, blacklist) {
        state.suggestionSettings.preferences().filter(state.suggestions)
            .filter { filter.accepts(ecosystems[it.source.name], it.source.contentType) }
            .filterNot { content -> content.tags.any { blacklist.containsTagTitle(it.title) } }
    }
    val contents = (history.map { it.content } + updates.map { it.first } + recommendations).associateBy { it.id }
    fun model(content: SourceContent, progress: Float? = null, count: Int = 0): MediaHomeItem {
        val type = DesktopSourceFilter.contentTypeOf(content.source.contentType)
        val kind = when {
            BrowseGroupTab.Video.matchesContentType(type) -> HomeMediaKind.ANIME
            BrowseGroupTab.Novel.matchesContentType(type) -> HomeMediaKind.BOOKS
            else -> HomeMediaKind.MANGA
        }
        val validProgress = progress?.takeIf { it.isFinite() && it in 0f..1f }
        return MediaHomeItem(
            id = content.id,
            title = content.title,
            kind = kind,
            progress = validProgress,
            supportingText = validProgress?.let { strings.format("home_resume_progress", (it * 100).toInt()) }.orEmpty(),
            updateText = if (count > 0) strings.format(
                if (kind == HomeMediaKind.ANIME) "media_home_new_episodes" else "media_home_new_chapters", count,
            ) else "",
        )
    }
    MediaHomeScreen(
        selectedKind = when (filter.tab) {
            BrowseGroupTab.Content -> HomeMediaKind.MANGA
            BrowseGroupTab.Novel -> HomeMediaKind.BOOKS
            BrowseGroupTab.Video -> HomeMediaKind.ANIME
            else -> HomeMediaKind.ALL
        },
        recent = history.map { model(it.content, it.progressPercent) },
        updates = updates.map { (content, count) -> model(content, count = count) },
        recommendations = recommendations.map { model(it) },
        initialized = !state.busy || contents.isNotEmpty(),
        onKindSelected = { kind ->
            controller.homeFilter(filter.copy(tab = when (kind) {
                HomeMediaKind.MANGA -> BrowseGroupTab.Content
                HomeMediaKind.BOOKS -> BrowseGroupTab.Novel
                HomeMediaKind.ANIME -> BrowseGroupTab.Video
                HomeMediaKind.ALL -> BrowseGroupTab.All
            }))
        },
        // Desktop currently searches within a chosen source. Name that capability accurately.
        searchLabel = strings.string("media_home_browse_sources"),
        onSearch = { controller.exitSource() },
        onSettings = { controller.more() },
        onHistory = { controller.library(true) },
        onUpdates = { controller.showUpdates() },
        onRecommendations = { controller.showSuggestions() },
        onManageSources = { controller.showExtensions() },
        onOpen = { model -> contents[model.id]?.let { controller.details(it) } },
        onResume = { model -> contents[model.id]?.let { controller.resumeHomeContent(it) } },
        cover = { model, modifier ->
            contents[model.id]?.let { DesktopCover(it, controller.session.covers, modifier) }
        },
        contentPadding = PaddingValues(top = LocalDesktopTopInset.current),
        modifier = Modifier.fillMaxSize().testTag("home-panel"),
        enabled = enabled,
    )
}

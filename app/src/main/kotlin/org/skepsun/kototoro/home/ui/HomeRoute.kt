package org.skepsun.kototoro.home.ui

import android.view.View
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseComposeActivity
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import org.skepsun.kototoro.home.ui.compose.homeMediaKind
import org.skepsun.kototoro.home.ui.compose.toContentTab
import org.skepsun.kototoro.core.ui.home.HomeMediaKind
import org.skepsun.kototoro.core.video.CinemaKind
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.main.ui.navigation3.TopLevelNavKey
import org.skepsun.kototoro.local.ui.compose.ImportDialog
import org.skepsun.kototoro.main.ui.navigation3.FavoritesNavKey
import org.skepsun.kototoro.main.ui.navigation3.HistoryNavKey
import org.skepsun.kototoro.main.ui.navigation3.MainNavigator
import org.skepsun.kototoro.main.ui.navigation3.SearchHomeNavKey
import org.skepsun.kototoro.main.ui.navigation3.SuggestionsNavKey
import org.skepsun.kototoro.main.ui.navigation3.UpdatedNavKey
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.search.ui.compose.SearchNavigationRequest
import org.skepsun.kototoro.space.domain.BuiltInSpaces
import org.skepsun.kototoro.space.domain.SpaceId
import org.skepsun.kototoro.space.ui.LocalBrowseSpaceId

@Composable
internal fun HomeRoute(
    animatedVisibilityScope: AnimatedVisibilityScope,
    appRouter: AppRouter,
    rootView: View,
    contentPadding: PaddingValues,
    mainNavigator: MainNavigator,
    onOpenSearch: (SearchNavigationRequest) -> Unit,
    navigateToDetailsWithContent: (Content, String?) -> Unit,
    onSelectSpace: ((SpaceId) -> Unit)? = null,
    cinemaTab: CinemaKind = CinemaKind.MOVIE,
    onCinemaTabSelected: (CinemaKind) -> Unit = {},
    isRouteVisible: Boolean = true,
) {
    val isCinema = LocalBrowseSpaceId.current == BuiltInSpaces.Cinema
    val viewModel = hiltViewModel<HomeViewModel>(key = "cinema-home".takeIf { isCinema })
    val state by viewModel.summaryState.collectAsStateWithLifecycle()
    val pinnedResumeIds by viewModel.pinnedResumeIds.collectAsStateWithLifecycle()
    val starterSources by viewModel.starterSources.collectAsStateWithLifecycle(initialValue = emptyList())
    val activity = LocalContext.current as? BaseComposeActivity
    var showImport by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCinema, viewModel) {
        if (isCinema) viewModel.setSelectedTab(HomeContentTab.VIDEO)
    }
    LaunchedEffect(viewModel.onError, activity, isRouteVisible) {
        if (!isRouteVisible) return@LaunchedEffect
        val observer = SnackbarErrorObserver(rootView, activity?.exceptionResolver, null)
        viewModel.onError.collect { event -> event?.consume(observer) }
    }
    fun openShelf(route: TopLevelNavKey) {
        viewModel.applyMediaShelfFilter(state.selectedTab)
        mainNavigator.openTopLevel(route)
    }
    HomeScreen(
        state = state,
        contentPadding = contentPadding,
        onKindSelected = { kind ->
            if (kind == HomeMediaKind.CINEMA) {
                onSelectSpace?.invoke(BuiltInSpaces.Cinema)
            } else if (isCinema) {
                onSelectSpace?.invoke(when (kind) {
                    HomeMediaKind.BOOKS -> BuiltInSpaces.Novel
                    HomeMediaKind.ANIME -> BuiltInSpaces.Anime
                    else -> BuiltInSpaces.Manga
                })
            } else {
                viewModel.setSelectedTab(kind.toContentTab())
            }
        },
        onSearch = { mainNavigator.openTopLevel(SearchHomeNavKey) },
        onSettings = appRouter::openSettings,
        onHistory = { openShelf(HistoryNavKey) },
        onLibrary = { openShelf(FavoritesNavKey) },
        onUpdates = { openShelf(UpdatedNavKey) },
        onRecommendations = { openShelf(SuggestionsNavKey) },
        onManageSources = { appRouter.openSourceRepositoryCatalog(cinema = isCinema) },
        starterSources = starterSources,
        onOpenStarter = { appRouter.openList(it, null, null) },
        onImport = { showImport = true },
        onOpen = { navigateToDetailsWithContent(it, null) },
        onResume = { content ->
            val history = state.recentHistoryItems.firstOrNull { it.content.id == content.id }?.history
            if (content.homeMediaKind().isVideo && history != null) {
                appRouter.openVideo(content, chapterId = history.chapterId, positionMs = history.page.toLong())
            } else {
                appRouter.openReader(content, state = history?.let(::ReaderState))
            }
        },
        selectedCinemaTab = cinemaTab.takeIf { isCinema },
        onCinemaTabSelected = onCinemaTabSelected.takeIf { onSelectSpace != null || isCinema },
        pinnedResumeIds = pinnedResumeIds,
        onResumeAction = { id, action ->
            val history = state.recentHistoryItems.firstOrNull { it.content.id == id }?.history
            val undo = viewModel.updateResumeItem(id, history, action)
            Snackbar.make(rootView, R.string.media_home_resume_changed, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) { undo() }.show()
        },
    )
    if (showImport && isRouteVisible) ImportDialog(onDismissRequest = { showImport = false })
}

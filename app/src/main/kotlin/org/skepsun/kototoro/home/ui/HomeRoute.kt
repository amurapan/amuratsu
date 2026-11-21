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
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseComposeActivity
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import org.skepsun.kototoro.home.ui.compose.homeMediaKind
import org.skepsun.kototoro.core.ui.home.HomeMediaKind
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

@Composable
internal fun HomeRoute(
    animatedVisibilityScope: AnimatedVisibilityScope,
    appRouter: AppRouter,
    rootView: View,
    contentPadding: PaddingValues,
    mainNavigator: MainNavigator,
    onOpenSearch: (SearchNavigationRequest) -> Unit,
    navigateToDetailsWithContent: (Content, String?) -> Unit,
    isRouteVisible: Boolean = true,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val state by viewModel.summaryState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? BaseComposeActivity
    var showImport by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel.onError, activity) {
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
        onKindSelected = viewModel::setSelectedTab,
        onSearch = { mainNavigator.openTopLevel(SearchHomeNavKey) },
        onSettings = appRouter::openSettings,
        onHistory = { openShelf(HistoryNavKey) },
        onLibrary = { openShelf(FavoritesNavKey) },
        onUpdates = { openShelf(UpdatedNavKey) },
        onRecommendations = { openShelf(SuggestionsNavKey) },
        onManageSources = appRouter::openSourceRepositoryCatalog,
        onImport = { showImport = true },
        onOpen = { navigateToDetailsWithContent(it, null) },
        onResume = { content ->
            val history = state.recentHistoryItems.firstOrNull { it.content.id == content.id }?.history
            if (content.homeMediaKind() == HomeMediaKind.ANIME && history != null) {
                appRouter.openVideo(content, chapterId = history.chapterId, positionMs = history.page.toLong())
            } else {
                appRouter.openReader(content, state = history?.let(::ReaderState))
            }
        },
    )
    if (showImport) ImportDialog(onDismissRequest = { showImport = false })
}

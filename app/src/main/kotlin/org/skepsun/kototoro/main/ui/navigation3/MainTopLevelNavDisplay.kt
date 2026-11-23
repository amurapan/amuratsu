package org.skepsun.kototoro.main.ui.navigation3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import org.skepsun.kototoro.core.prefs.ListToDetailsTransition
import org.skepsun.kototoro.core.ui.compose.LocalNavAnimatedVisibilityScope

internal val LocalMainTabActive = compositionLocalOf { true }

private val retainedMainTabs = setOf(HomeNavKey, SearchHomeNavKey, FavoritesNavKey, LocalNavKey)

/**
 * Owns a [NavDisplay] per visible or retained tab. Previously visited primary tabs can
 * keep their root composition while offscreen, without measuring, drawing, or collecting
 * lifecycle-aware flows. Only the active tab can show an immersive back stack.
 *
 * The innermost entry of the selected tab's stack is shown as the current scene:
 * a [TopLevelNavKey] renders the tab content, while a [DetailsNavKey],
 * [ContentListNavKey] or [SearchNavKey] on top renders the immersive destination
 * (details / source list / search) over the tab.
 *
 * Navigation-3 shared-element semantics are used for the hero transition: every
 * entry's content receives `LocalNavAnimatedVisibilityScope` = the NavDisplay's
 * inner `AnimatedContent` scope (`LocalNavAnimatedContentScope`), so both the
 * leaving list scene and the entering details scene register their shared bounds
 * against the same active scene transition and the cover hero animates.
 *
 * In-tab scene motion is owned by [KototoroMotionCatalog] and the per-entry
 * [NavDisplay.TransitionKey] metadata overrides (content list / details /
 * search). See docs/architecture/navigation3-motion-system.md.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainTopLevelNavDisplay(
    navState: MainNavState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    detailsTransitionStyle: ListToDetailsTransition = ListToDetailsTransition.HERO_EXPAND,
    renderEntry: @Composable (MainNavKey) -> Unit,
) {
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "MainTopLevelNavDisplay requires a ViewModelStoreOwner"
    }
    val saveableStateHolder = rememberSaveableStateHolder()
    val currentRenderEntry = rememberUpdatedState(renderEntry)
    // Decorate each top-level stack independently so switching tabs does not clear its state.
    val decoratedEntriesByTopLevel: Map<TopLevelNavKey, List<NavEntry<MainNavKey>>> = allTopLevelNavKeys.associateWith { key ->
        val backStack: List<MainNavKey> = navState.stackFor(key).toList()
        val savedState = rememberSaveableStateHolderNavEntryDecorator<MainNavKey>(saveableStateHolder)
        val viewModels = rememberViewModelStoreNavEntryDecorator<MainNavKey>(viewModelStoreOwner)
        val entryDecorators = remember(savedState, viewModels) { listOf(savedState, viewModels) }
        val entryProvider: (MainNavKey) -> NavEntry<MainNavKey> =
            remember(backStack, detailsTransitionStyle) {
                { entryKey ->
                    navEntry(
                        key = entryKey,
                        prev = backStack.getOrNull(backStack.indexOf(entryKey) - 1),
                        detailsTransitionStyle = detailsTransitionStyle,
                        renderEntry = { key -> currentRenderEntry.value(key) },
                    )
                }
            }
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = entryDecorators,
            entryProvider = entryProvider,
        )
    }
    val visitedTabs = remember(navState) { mutableStateSetOf<TopLevelNavKey>() }
    val displayedTabs = remember(navState, visitedTabs) {
        derivedStateOf(structuralEqualityPolicy()) {
            allTopLevelNavKeys.filter { tab ->
                tab == navState.selectedTopLevel || (tab in visitedTabs && navState.stackFor(tab).size == 1)
            }
        }
    }.value
    val onTabVisited: (TopLevelNavKey) -> Unit = remember(visitedTabs) {
        { tab -> visitedTabs.add(tab) }
    }
    Layout(
        modifier = modifier,
        content = {
            displayedTabs.forEach { tab ->
                key(tab) {
                    MainTab(
                        navState = navState,
                        tab = tab,
                        entries = decoratedEntriesByTopLevel.getValue(tab),
                        sharedTransitionScope = sharedTransitionScope,
                        onTabVisited = onTabVisited,
                    )
                }
            }
        },
    ) { tabs, constraints ->
        // A switch between cached screens only invalidates placement and the two affected tabs.
        val selectedIndex = displayedTabs.indexOf(navState.selectedTopLevel)
        val visibleTab = tabs[selectedIndex].measure(constraints)
        layout(visibleTab.width, visibleTab.height) {
            visibleTab.placeRelative(0, 0)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainTab(
    navState: MainNavState,
    tab: TopLevelNavKey,
    entries: List<NavEntry<MainNavKey>>,
    sharedTransitionScope: SharedTransitionScope?,
    onTabVisited: (TopLevelNavKey) -> Unit,
) {
    val active by remember(navState, tab) {
        derivedStateOf(structuralEqualityPolicy()) { navState.selectedTopLevel == tab }
    }
    if (active && tab in retainedMainTabs) {
        SideEffect { onTabVisited(tab) }
    }
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
    )
    val sceneStrategies: List<SceneStrategy<MainNavKey>> = remember { listOf(SinglePaneSceneStrategy()) }
    val fadeThrough = remember { KototoroMotionCatalog.preset(KototoroMotion.FadeThrough) }
    CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalMainTabActive provides active,
    ) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                entries = entries,
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart,
                sceneStrategies = sceneStrategies,
                sceneDecoratorStrategies = emptyList<SceneDecoratorStrategy<MainNavKey>>(),
                sharedTransitionScope = sharedTransitionScope,
                transitionSpec = { fadeThrough.enter(this) },
                popTransitionSpec = { fadeThrough.pop(this) },
                predictivePopTransitionSpec = { progress -> fadeThrough.predictivePop(this, progress) },
                onBack = { if (navState.selectedTopLevel == tab) navState.pop() },
            )
        }
    }
}

private fun navEntry(
    key: MainNavKey,
    prev: MainNavKey?,
    detailsTransitionStyle: ListToDetailsTransition,
    renderEntry: @Composable (MainNavKey) -> Unit = {},
): NavEntry<MainNavKey> {
    return NavEntry(
        key = key,
        metadata = transitionMetadata(key, prev, detailsTransitionStyle),
    ) { entryKey ->
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        ) {
            renderEntry(entryKey)
        }
    }
}

/**
 * Navigation-relation -> scene metadata. Sibling entries fade through
 * (also the NavDisplay default); source lists use Hierarchical; search uses the
 * Z-axis workspace; details use HeroExpand by default, or the selectable legacy
 * full-width page turn when the user opts into it.
 */
private fun transitionMetadata(
    key: MainNavKey,
    prev: MainNavKey?,
    detailsTransitionStyle: ListToDetailsTransition,
): Map<String, Any> {
    val preset = if (key is DetailsNavKey && detailsTransitionStyle == ListToDetailsTransition.LEGACY_SLIDE) {
        KototoroMotionCatalog.legacySlide
    } else {
        KototoroMotionCatalog.preset(KototoroMotionCatalog.forRelation(prev, key))
    }
    return metadata {
        put(NavDisplay.TransitionKey, preset.enter)
        put(NavDisplay.PopTransitionKey, preset.pop)
        put(NavDisplay.PredictivePopTransitionKey, preset.predictivePop)
    }
}

package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.main.ui.navigation3.HomeNavKey
import org.skepsun.kototoro.main.ui.navigation3.MainNavKey
import org.skepsun.kototoro.main.ui.navigation3.TopLevelNavKey
import org.skepsun.kototoro.main.ui.navigation3.allTopLevelNavKeys

internal data class MainPageInsets(
    val homeTop: Dp,
    val top: Dp,
    val bottom: Dp,
    val scrolledPage: TopLevelNavKey?,
    val scrolledTop: Dp,
    val scrolledBottom: Dp,
)

@Composable
internal fun rememberMainPagePadding(insets: MainPageInsets): (MainNavKey) -> PaddingValues {
    val currentInsets = rememberUpdatedState(insets)
    return remember {
        val empty = PaddingValues(0.dp)
        val paddingByPage = allTopLevelNavKeys.associateWith { PagePadding(it, currentInsets) }
        val paddingForPage: (MainNavKey) -> PaddingValues = { key -> paddingByPage[key] ?: empty }
        paddingForPage
    }
}

/** Each page keeps one padding object. Layout reads the scroll offset of its own chrome. */
@Stable
private class PagePadding(
    private val page: TopLevelNavKey,
    private val insets: State<MainPageInsets>,
) : PaddingValues {
    override fun calculateTopPadding(): Dp {
        val value = insets.value
        return when (page) {
            HomeNavKey -> value.homeTop
            value.scrolledPage -> value.scrolledTop
            else -> value.top
        }
    }

    override fun calculateBottomPadding(): Dp {
        val value = insets.value
        return if (page == value.scrolledPage) value.scrolledBottom else value.bottom
    }

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

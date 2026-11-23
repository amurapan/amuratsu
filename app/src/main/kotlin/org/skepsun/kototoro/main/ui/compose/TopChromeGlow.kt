package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import org.skepsun.kototoro.core.ui.theme.LocalBackgroundStyle
import org.skepsun.kototoro.core.ui.theme.isDarkTheme
import org.skepsun.kototoro.main.ui.navigation3.DiscoverNavKey
import org.skepsun.kototoro.main.ui.navigation3.ExploreNavKey
import org.skepsun.kototoro.main.ui.navigation3.FavoritesNavKey
import org.skepsun.kototoro.main.ui.navigation3.FeedNavKey
import org.skepsun.kototoro.main.ui.navigation3.HistoryNavKey
import org.skepsun.kototoro.main.ui.navigation3.LocalNavKey
import org.skepsun.kototoro.main.ui.navigation3.SuggestionsNavKey
import org.skepsun.kototoro.main.ui.navigation3.TopLevelNavKey
import org.skepsun.kototoro.main.ui.navigation3.UpdatedNavKey
import kotlin.math.max

@Composable
internal fun BoxScope.TopChromeGlow(
    route: TopLevelNavKey?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (firstAccent, secondAccent) = when (route) {
        FavoritesNavKey, DiscoverNavKey, SuggestionsNavKey -> scheme.tertiary to scheme.primary
        HistoryNavKey, UpdatedNavKey -> scheme.secondary to scheme.tertiary
        ExploreNavKey, FeedNavKey -> scheme.primary to scheme.secondary
        LocalNavKey -> scheme.secondary to scheme.primary
        else -> scheme.primary to scheme.tertiary
    }
    val strength = when {
        LocalBackgroundStyle.current.usesArtworkBackdrop -> 0.13f
        scheme.isDarkTheme() -> 0.32f
        else -> 0.23f
    }
    // The vertical radius fades each glow to transparent at the bottom of the band. Scaling
    // horizontally keeps the tablet layout soft without an offscreen mask or a tab color animation.
    Spacer(
        modifier = modifier
            .height(height)
            .drawWithCache {
                val firstCenter = Offset(size.width * 0.16f, -size.height * 0.18f)
                val secondCenter = Offset(size.width * 0.88f, size.height * 0.12f)
                val firstRadius = (size.height - firstCenter.y).coerceAtLeast(1f)
                val secondRadius = (size.height - secondCenter.y).coerceAtLeast(1f)
                val firstWidth = max(size.width * 0.84f, firstRadius)
                val secondWidth = max(size.width * 0.67f, secondRadius)
                val firstBrush = Brush.radialGradient(
                    colors = listOf(firstAccent.copy(alpha = strength * 0.94f), Color.Transparent),
                    center = firstCenter,
                    radius = firstRadius,
                )
                val secondBrush = Brush.radialGradient(
                    colors = listOf(secondAccent.copy(alpha = strength * 0.73f), Color.Transparent),
                    center = secondCenter,
                    radius = secondRadius,
                )
                onDrawBehind {
                    clipRect {
                        scale(firstWidth / firstRadius, 1f, firstCenter) {
                            drawCircle(firstBrush, firstRadius, firstCenter)
                        }
                        scale(secondWidth / secondRadius, 1f, secondCenter) {
                            drawCircle(secondBrush, secondRadius, secondCenter)
                        }
                    }
                }
            },
    )
}

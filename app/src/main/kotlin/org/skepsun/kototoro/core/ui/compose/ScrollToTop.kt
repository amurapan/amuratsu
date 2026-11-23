package org.skepsun.kototoro.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

val LocalScrollToTopEvents = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

@Composable
fun ScrollToTopEffect(onScrollToTop: suspend () -> Unit) {
    val events = LocalScrollToTopEvents.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)

    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collectLatest {
                currentOnScrollToTop()
            }
        }
    }
}

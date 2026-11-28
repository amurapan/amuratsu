package org.skepsun.kototoro.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.NotificationSettingsScreen
import org.skepsun.kototoro.settings.compose.NotificationSettingsUiState
import org.skepsun.kototoro.settings.utils.RingtonePickContract
import org.skepsun.kototoro.tracker.work.TrackerNotificationHelper
import javax.inject.Inject

@Composable
fun NotificationSettingsRoute(
    settings: AppSettings,
    onNotificationSoundClick: () -> Unit,
    onNotificationVibrateClick: () -> Unit,
) {
    val context = LocalContext.current
    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED
    var permissionGranted by remember(context) { mutableStateOf(hasNotificationPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionGranted = hasNotificationPermission() }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) settings.isTrackerNotificationsEnabled = true
    }
    val isTrackerNotificationsEnabled = settings.observeAsState(
        AppSettings.KEY_TRACKER_NOTIFICATIONS,
    ) { isTrackerNotificationsEnabled }.value
    val notificationSound = settings.observeAsState(
        AppSettings.KEY_NOTIFICATIONS_SOUND,
    ) { notificationSound }.value
    val notificationLight = settings.observeAsState(
        AppSettings.KEY_NOTIFICATIONS_LIGHT,
    ) { notificationLight }.value
    val snackbarHostState = remember { SnackbarHostState() }
    val ringtoneSummary = RingtoneManager.getRingtone(context, notificationSound)
        ?.getTitle(context)
        ?: context.getString(R.string.silent)

    val state = NotificationSettingsUiState(
        isTrackerNotificationsEnabled = isTrackerNotificationsEnabled && permissionGranted,
        ringtoneSummary = ringtoneSummary,
        isNotificationLightEnabled = notificationLight,
        isNotificationsInfoVisible = !isTrackerNotificationsEnabled || !permissionGranted,
    )

    NotificationSettingsScreen(
        notificationsTitle = context.getString(R.string.notifications),
        state = state,
        snackbarHostState = snackbarHostState,
        onTrackerNotificationsEnabledChange = { enabled ->
            if (enabled && !permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settings.isTrackerNotificationsEnabled = enabled
            }
        },
        onNotificationSoundClick = onNotificationSoundClick,
        onNotificationVibrateClick = onNotificationVibrateClick,
        onNotificationLightChange = { settings.notificationLight = it },
    )
}

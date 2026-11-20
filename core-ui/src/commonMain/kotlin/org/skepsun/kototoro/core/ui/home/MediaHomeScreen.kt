package org.skepsun.kototoro.core.ui.home

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.ui.gestures.mouseDragScroll
import org.skepsun.kototoro.core.ui.text.uiString
import org.skepsun.kototoro.reader.ui.compose.design.ReaderPanelIcons

enum class HomeMediaKind(val labelKey: String) {
    ALL("all"),
    MANGA("manga"),
    BOOKS("media_books"),
    ANIME("media_home_anime"),
}

/** Presentation data only. Hosts resolve covers, persisted progress and reader/player launches. */
@Immutable
data class MediaHomeItem(
    val id: Long,
    val title: String,
    val kind: HomeMediaKind,
    val supportingText: String = "",
    val progress: Float? = null,
    val updateText: String = "",
)

/** Android and desktop render this same personal home; platform services enter through callbacks. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaHomeScreen(
    selectedKind: HomeMediaKind,
    recent: List<MediaHomeItem>,
    updates: List<MediaHomeItem>,
    recommendations: List<MediaHomeItem>,
    initialized: Boolean,
    onKindSelected: (HomeMediaKind) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onUpdates: () -> Unit,
    onRecommendations: () -> Unit,
    onManageSources: () -> Unit,
    onOpen: (MediaHomeItem) -> Unit,
    onResume: (MediaHomeItem) -> Unit,
    cover: @Composable (MediaHomeItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    onImport: (() -> Unit)? = null,
    searchLabel: String = uiString("search_all_media"),
    enabled: Boolean = true,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val hasContent = recent.isNotEmpty() || updates.isNotEmpty() || recommendations.isNotEmpty()
    val fontScale = LocalDensity.current.fontScale
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding)) {
            val twoColumns = maxWidth >= 720.dp && fontScale <= 1.4f
            val horizontalPadding = if (twoColumns) 28.dp else 16.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 1200.dp)
                    .fillMaxSize().mouseDragScroll(listState, Orientation.Vertical).testTag("media-home-scroll"),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 16.dp,
                    bottom = 100.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiString("home"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiString("media_home_title"), style = MaterialTheme.typography.headlineMedium)
                        }
                        FilledTonalIconButton(onClick = onSettings, enabled = enabled,
                            modifier = Modifier.testTag("media-home-settings")) {
                            Icon(ReaderPanelIcons.Palette, uiString("settings"))
                        }
                    }
                }
                item(key = "search") {
                    Surface(
                        onClick = onSearch,
                        enabled = enabled,
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().testTag("media-home-search"),
                    ) {
                        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(ReaderPanelIcons.Search, null)
                            Text(searchLabel, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "formats") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeMediaKind.entries.forEach { kind ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { onKindSelected(kind) },
                                enabled = enabled,
                                label = { Text(uiString(kind.labelKey)) },
                                leadingIcon = if (selectedKind == kind) {
                                    { Icon(ReaderPanelIcons.Check, null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.testTag("media-home-filter:${kind.name}"),
                            )
                        }
                    }
                }
                if (!initialized) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().height(180.dp).testTag("media-home-loading"),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (!hasContent) {
                    item(key = "empty") {
                        HomeEmpty(selectedKind, onSearch, { showAdd = true }, onImport, enabled)
                    }
                } else {
                    item(key = "shelves") {
                        val primary: @Composable () -> Unit = {
                            HomeResumeSection(recent, onHistory, onOpen, onResume, cover, enabled)
                        }
                        val secondary: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                if (updates.isNotEmpty()) HomeUpdatesSection(updates, onUpdates, onOpen, cover, enabled)
                                if (recommendations.isNotEmpty()) HomeRecommendationsSection(
                                    recommendations, onRecommendations, onOpen, cover, enabled,
                                )
                            }
                        }
                        if (twoColumns && recent.isNotEmpty() && (updates.isNotEmpty() || recommendations.isNotEmpty())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp),
                                modifier = Modifier.testTag("media-home-columns")) {
                                Box(Modifier.weight(1.25f)) { primary() }
                                Box(Modifier.weight(1f)) { secondary() }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                primary()
                                secondary()
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { if (enabled) showAdd = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("media-home-add"),
            ) {
                Icon(ReaderPanelIcons.Add, uiString("media_home_add"))
            }
        }
    }
    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                Text(uiString("media_home_add"), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp))
                HomeSheetAction(ReaderPanelIcons.Add, uiString("manage_sources"), "media-home-add-sources") {
                    showAdd = false
                    onManageSources()
                }
                onImport?.let { import ->
                    HomeSheetAction(ReaderPanelIcons.Import, uiString("media_home_import"), "media-home-import") {
                        showAdd = false
                        import()
                    }
                }
                HomeSheetAction(ReaderPanelIcons.Search, searchLabel, "media-home-add-search") {
                    showAdd = false
                    onSearch()
                }
            }
        }
    }
}

@Composable
private fun HomeEmpty(
    selectedKind: HomeMediaKind,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onImport: (() -> Unit)?,
    enabled: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("media-home-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Icon(ReaderPanelIcons.BookPage, null, Modifier.size(40.dp))
            }
        }
        Text(uiString(if (selectedKind == HomeMediaKind.ALL) "media_home_empty_title" else "media_home_filtered_empty"),
            style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(uiString("media_home_empty_description"), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = if (selectedKind == HomeMediaKind.ALL) onAdd else onSearch, enabled = enabled) {
            Text(uiString(if (selectedKind == HomeMediaKind.ALL) "media_home_add" else "search"))
        }
        if (onImport != null) TextButton(onClick = onImport, enabled = enabled) { Text(uiString("media_home_import")) }
    }
}

@Composable
private fun HomeResumeSection(
    items: List<MediaHomeItem>,
    onHistory: () -> Unit,
    onOpen: (MediaHomeItem) -> Unit,
    onResume: (MediaHomeItem) -> Unit,
    cover: @Composable (MediaHomeItem, Modifier) -> Unit,
    enabled: Boolean,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaHomeHeading(uiString("continue_action"), uiString("history"), "media-home-history", onHistory, enabled)
        val featured = items.first()
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { onOpen(featured) }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(80.dp, 112.dp).semantics { contentDescription = featured.title }
                        .testTag("media-home-details:${featured.id}")) {
                    cover(featured, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiString(featured.kind.labelKey), style = MaterialTheme.typography.labelSmall)
                    Text(featured.title, style = MaterialTheme.typography.titleLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    if (featured.supportingText.isNotBlank()) Text(featured.supportingText,
                        style = MaterialTheme.typography.bodySmall)
                    HomeProgress(featured)
                    Button(onClick = { onResume(featured) }, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("media-home-resume:${featured.id}")) {
                        Icon(resumeIcon(featured), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(uiString("continue_action"))
                    }
                }
            }
        }
        val next = items.drop(1).take(2)
        if (next.isNotEmpty()) {
            BoxWithConstraints {
                if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        next.forEach { HomeSmallResume(it, onOpen, onResume, cover, enabled, Modifier.fillMaxWidth()) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        next.forEach { HomeSmallResume(it, onOpen, onResume, cover, enabled, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun resumeIcon(item: MediaHomeItem): ImageVector =
    if (item.kind == HomeMediaKind.ANIME) ReaderPanelIcons.Play else ReaderPanelIcons.BookPage

@Composable
private fun HomeSmallResume(
    item: MediaHomeItem,
    onOpen: (MediaHomeItem) -> Unit,
    onResume: (MediaHomeItem) -> Unit,
    cover: @Composable (MediaHomeItem, Modifier) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Surface(onClick = { onOpen(item) }, enabled = enabled, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.testTag("media-home-details:${item.id}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                cover(item, Modifier.size(42.dp, 58.dp).clip(RoundedCornerShape(8.dp)))
                FilledTonalIconButton(onClick = { onResume(item) }, enabled = enabled,
                    modifier = Modifier.testTag("media-home-resume:${item.id}")) {
                    Icon(resumeIcon(item), uiString("space_continue_content_description", item.title))
                }
            }
            Text(uiString(item.kind.labelKey), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            if (item.supportingText.isNotBlank()) Text(item.supportingText,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HomeProgress(item)
        }
    }
}

@Composable
private fun HomeProgress(item: MediaHomeItem) {
    item.progress?.takeIf { it.isFinite() && it in 0f..1f }?.let { progress ->
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {})
    }
}

@Composable
private fun HomeUpdatesSection(
    items: List<MediaHomeItem>,
    onAll: () -> Unit,
    onOpen: (MediaHomeItem) -> Unit,
    cover: @Composable (MediaHomeItem, Modifier) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaHomeHeading(uiString("media_home_updates"), uiString("all"), "media-home-updates", onAll, enabled)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                items.take(3).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(onClick = { onOpen(item) }, enabled = enabled,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.testTag("media-home-update:${item.id}")) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            cover(item, Modifier.size(36.dp, 50.dp).clip(RoundedCornerShape(7.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Text(uiString(item.kind.labelKey), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.updateText.isNotBlank()) Surface(shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(item.updateText, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRecommendationsSection(
    items: List<MediaHomeItem>,
    onAll: () -> Unit,
    onOpen: (MediaHomeItem) -> Unit,
    cover: @Composable (MediaHomeItem, Modifier) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaHomeHeading(uiString("media_home_recommendations"), uiString("all"), "media-home-recommendations", onAll, enabled)
        items.take(3).forEach { item ->
            Surface(onClick = { onOpen(item) }, enabled = enabled, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("media-home-recommendation:${item.id}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    cover(item, Modifier.size(45.dp, 63.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(uiString(item.kind.labelKey), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaHomeHeading(title: String, action: String, tag: String, onClick: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) { Text(action) }
    }
}

@Composable
private fun HomeSheetAction(icon: ImageVector, title: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f))
    }
}

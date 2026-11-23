package org.skepsun.kototoro.search.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.source.SourceListRow
import org.skepsun.kototoro.main.ui.navigation3.LocalMainTabActive
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.matches
import org.skepsun.kototoro.search.domain.searchContentKindsFromNames

/** Android's global search entry. Its query and scope belong to this back stack, never to a media space. */
@Composable
internal fun SearchHomeRoute(
    contentPadding: PaddingValues,
    onSearch: (SearchNavigationRequest) -> Unit,
    onManageSources: () -> Unit,
    onOpenSource: (ContentSource) -> Unit,
    initialContentKinds: Set<SearchContentKind> = ALL_SEARCH_CONTENT_KINDS,
    viewModel: SearchHomeViewModel = hiltViewModel(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val initialNames = initialContentKinds.map { it.name }
    var mediaNames by key(initialNames) {
        rememberSaveable { mutableStateOf(initialNames) }
    }
    val kinds = searchContentKindsFromNames(mediaNames) ?: ALL_SEARCH_CONTENT_KINDS
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mainTabActive = LocalMainTabActive.current
    val enabledSources by viewModel.sources.collectAsStateWithLifecycle(initialValue = null)
    val visibleSources = remember(enabledSources, kinds) {
        enabledSources?.filter { info -> kinds.any { it.matches(info.mangaSource) } }
    }
    fun submit() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        keyboard?.hide()
        onSearch(
            SearchNavigationRequest(
                query = trimmed,
                kind = SearchKind.SIMPLE,
                sourceTypes = ALL_SOURCE_TYPES,
                contentKinds = kinds,
                advancedQuery = null,
                pinnedOnly = false,
                hideEmpty = false,
                requestId = System.nanoTime(),
            ),
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusProperties { canFocus = mainTabActive }
                    .testTag("global-search-query"),
                label = {
                    Text(stringResource(when (kinds.singleOrNull()) {
                        SearchContentKind.MOVIE -> R.string.cinema_search_movies
                        SearchContentKind.SERIES -> R.string.cinema_search_series
                        null -> R.string.search_all_media
                        else -> R.string.media_search_hint
                    }))
                },
                trailingIcon = {
                    IconButton(
                        onClick = ::submit,
                        enabled = query.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.testTag("global-search-submit"),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )
            SearchMediaChips(
                selected = kinds,
                onSelect = { mediaNames = it.map { kind -> kind.name } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 80.dp),
            ) {
                item(key = "source_heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.enabled_sources),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onManageSources, modifier = Modifier.testTag("global-search-sources")) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.manage_sources),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when {
                    visibleSources == null -> item(key = "sources_loading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    }
                    visibleSources.isEmpty() -> item(key = "sources_empty") {
                        Text(
                            text = stringResource(
                                if (enabledSources.isNullOrEmpty()) R.string.search_no_enabled_sources
                                else R.string.search_no_sources_for_format,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> items(visibleSources, key = { it.name }) { info ->
                        val source = info.mangaSource
                        SourceListRow(
                            title = source.getTitle(context),
                            summary = source.getSummary(context),
                            icon = { ContentSourceIcon(source, it, throttleNetworkLoad = true) },
                            expressive = true,
                            onClick = {
                                keyboard?.hide()
                                onOpenSource(source)
                            },
                        )
                    }
                }
            }
        }
    }
}

package org.skepsun.kototoro.search.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.buttons.Button
import org.skepsun.kototoro.core.ui.buttons.TextButton
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.searchContentKindsFromNames

/** Android's global search entry. Its query and scope belong to this back stack, never to a media space. */
@Composable
internal fun SearchHomeRoute(
    contentPadding: PaddingValues,
    onSearch: (SearchNavigationRequest) -> Unit,
    onManageSources: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var mediaNames by rememberSaveable { mutableStateOf(ALL_SEARCH_CONTENT_KINDS.map { it.name }) }
    val kinds = searchContentKindsFromNames(mediaNames) ?: ALL_SEARCH_CONTENT_KINDS
    val keyboard = LocalSoftwareKeyboardController.current
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
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.media_search_hint), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("global-search-query"),
            label = { Text(stringResource(R.string.search_all_media)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        SearchMediaChips(selected = kinds, onSelect = { mediaNames = it.map { kind -> kind.name } })
        Button(
            onClick = ::submit,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("global-search-submit"),
        ) {
            Text(stringResource(R.string.search))
        }
        Text(
            stringResource(R.string.media_search_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onManageSources, modifier = Modifier.testTag("global-search-sources")) {
            Text(stringResource(R.string.manage_sources))
        }
    }
}

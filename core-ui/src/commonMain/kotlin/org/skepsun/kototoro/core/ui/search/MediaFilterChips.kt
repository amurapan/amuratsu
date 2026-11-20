package org.skepsun.kototoro.core.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.ui.chips.CompactChoicePill
import org.skepsun.kototoro.core.ui.text.uiString

/** One tap selects a media type; All restores the mixed scope. Advanced filters may select several types. */
@Composable
fun <T> MediaFilterChips(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    key: (T) -> String,
    onSelect: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOptionEnabled: (T) -> Boolean = { true },
    tagPrefix: String = "media-filter",
) {
    val all = options.toSet()
    val isAllSelected = selected.isEmpty() || selected.containsAll(all)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactChoicePill(
            selected = isAllSelected,
            onClick = { onSelect(all) },
            label = uiString("all"),
            enabled = enabled,
            modifier = Modifier.testTag("$tagPrefix:all"),
        )
        options.forEach { option ->
            CompactChoicePill(
                selected = !isAllSelected && option in selected,
                onClick = { onSelect(setOf(option)) },
                label = label(option),
                enabled = enabled && isOptionEnabled(option),
                modifier = Modifier.testTag("$tagPrefix:${key(option)}"),
            )
        }
    }
}

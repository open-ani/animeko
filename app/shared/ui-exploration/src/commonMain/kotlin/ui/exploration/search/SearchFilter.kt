/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random

/**
 * @see me.him188.ani.app.data.models.subject.CanonicalTagKind
 */
@Immutable
data class SearchFilterState(
    val chips: List<SearchFilterChipState>,
) {
    companion object {
        val DEFAULT_TAG_KINDS = listOf(
            // order matters
            CanonicalTagKind.Genre,
            CanonicalTagKind.Setting,
            CanonicalTagKind.Character,
            CanonicalTagKind.Region,
            CanonicalTagKind.Emotion,
            CanonicalTagKind.Source,
            CanonicalTagKind.Audience,
            CanonicalTagKind.Rating,
            CanonicalTagKind.Category,
        )
    }
}

@Immutable
data class SearchFilterChipState(
    val kind: CanonicalTagKind?, // null for custom chips
    val values: List<String>,
    val selected: List<String>,
) {
    val hasSelection: Boolean
        get() = selected.isNotEmpty()
}

@Immutable
data class SearchTimeFilterState(
    val selectedYear: Int?,
    val selectedSeason: AnimeSeason?,
    val availableYears: List<Int> = defaultSearchYearOptions(),
)

@Composable
fun SearchFilterChipsRow(
    state: SearchFilterState,
    onClickItemText: (SearchFilterChipState, value: String) -> Unit,
    onCheckedChange: (SearchFilterChipState, value: String) -> Unit,
    timeFilterState: SearchTimeFilterState? = null,
    onYearSelected: ((Int?) -> Unit)? = null,
    onSeasonSelected: ((AnimeSeason?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (chipState in state.chips) {
            SearchFilterChip(
                chipState,
                { onClickItemText(chipState, it) },
                { onCheckedChange(chipState, it) },
            )
            if (chipState.kind == CanonicalTagKind.Region && timeFilterState != null) {
                SearchYearFilterChip(
                    state = timeFilterState,
                    onYearSelected = { onYearSelected?.invoke(it) },
                )
                SearchSingleSelectChip(
                    label = timeFilterState.selectedSeason?.displayName() ?: "\u5B63\u5EA6",
                    options = listOf(null) + AnimeSeason.entries,
                    enabled = timeFilterState.selectedYear != null,
                    isSelected = { it == timeFilterState.selectedSeason },
                    renderOption = { it?.displayName() ?: "\u5168\u90E8\u5B63\u5EA6" },
                    onSelect = { onSeasonSelected?.invoke(it) },
                )
            }
        }
    }
}

@Composable
fun SearchFilterChip(
    state: SearchFilterChipState,
    onClickItemText: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val textLayout = rememberTextMeasurer(1)
    val density = LocalDensity.current
    val styleLabelLarge = MaterialTheme.typography.labelLarge
    val maxWidth = remember(textLayout, density, styleLabelLarge) {
        with(density) {
            textLayout.measure(
                "占位,占位位",
                softWrap = false,
                maxLines = 1,
                style = styleLabelLarge,
            ).size.width.toDp()
        }
    }
    Box(modifier) {
        InputChip(
            state.hasSelection,
            onClick = { showDropdown = true },
            label = {
                Text(
                    renderChipLabel(state),
                    Modifier.widthIn(max = maxWidth.coerceAtLeast(128.dp)),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            modifier,
            trailingIcon = {
                Icon(
                    Icons.Rounded.ArrowDropDown, null,
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            for (value in state.values) {
                DropdownMenuItem(
                    text = { Text(value) },
                    {
                        onClickItemText(value)
                        showDropdown = false
                    },
                    leadingIcon = {
                        Checkbox(
                            checked = value in state.selected,
                            onCheckedChange = { onCheckedChange(value) },
                        )
                    },
                    contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchYearFilterChip(
    state: SearchTimeFilterState,
    onYearSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxYear = state.availableYears.firstOrNull()
    val minYear = state.availableYears.lastOrNull()
    Row(modifier) {
        InputChip(
            selected = state.selectedYear != null,
            onClick = {
                onYearSelected(
                    if (state.selectedYear == null) {
                        defaultSearchYearSelection(state.availableYears)
                    } else {
                        null
                    },
                )
            },
            label = {
                Text(
                    state.selectedYear?.let { "\u5E74\u4EFD\uff1a$it" } ?: "\u5E74\u4EFD\uff1a\u5168\u90E8",
                    Modifier.widthIn(min = 88.dp, max = 112.dp),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            leadingIcon = {
                IconButton(
                    enabled = state.selectedYear != minYear,
                    onClick = {
                        val current = state.selectedYear ?: defaultSearchYearSelection(state.availableYears)
                        if (current != null) {
                            onYearSelected((current - 1).coerceAtLeast(minYear ?: current))
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "\u51CF\u5C11\u5E74\u4EFD",
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                }
            },
            trailingIcon = {
                IconButton(
                    enabled = state.selectedYear != maxYear,
                    onClick = {
                        val current = state.selectedYear ?: defaultSearchYearSelection(state.availableYears)
                        if (current != null) {
                            onYearSelected((current + 1).coerceAtMost(maxYear ?: current))
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "\u589E\u52A0\u5E74\u4EFD",
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                }
            },
        )
    }
}

@Composable
private fun <T> SearchSingleSelectChip(
    label: String,
    options: List<T>,
    isSelected: (T) -> Boolean,
    renderOption: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        InputChip(
            selected = enabled && options.any(isSelected),
            onClick = { if (enabled) showDropdown = true },
            label = {
                Text(
                    label,
                    Modifier.widthIn(max = 128.dp),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            enabled = enabled,
            trailingIcon = {
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    null,
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(renderOption(option)) },
                    onClick = {
                        onSelect(option)
                        showDropdown = false
                    },
                    leadingIcon = {
                        Checkbox(
                            checked = isSelected(option),
                            onCheckedChange = null,
                        )
                    },
                    contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
                )
            }
        }
    }
}

private fun AnimeSeason.displayName(): String = when (this) {
    AnimeSeason.WINTER -> "\u4E00\u6708\u756A"
    AnimeSeason.SPRING -> "\u56DB\u6708\u756A"
    AnimeSeason.SUMMER -> "\u4E03\u6708\u756A"
    AnimeSeason.AUTUMN -> "\u5341\u6708\u756A"
}

private fun defaultSearchYearOptions(): List<Int> {
    val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    return (currentYear + 1 downTo currentYear - 40).toList()
}

private fun defaultSearchYearSelection(availableYears: List<Int>): Int? {
    val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    return availableYears.firstOrNull { it == currentYear } ?: availableYears.firstOrNull()
}

private fun renderChipLabel(
    state: SearchFilterChipState,
): String {
    if (state.hasSelection) {
        return state.selected.joinToString(",")
    }
    return when (state.kind) {
        CanonicalTagKind.Audience -> "受众"
        CanonicalTagKind.Category -> "分类"
        CanonicalTagKind.Character -> "角色"
        CanonicalTagKind.Emotion -> "情感"
        CanonicalTagKind.Genre -> "类型"
        CanonicalTagKind.Rating -> "分级"
        CanonicalTagKind.Region -> "地区"
        CanonicalTagKind.Series -> "系列"
        CanonicalTagKind.Setting -> "设定"
        CanonicalTagKind.Source -> "来源"
        CanonicalTagKind.Technology -> "技术"
        null -> "自定义"
    }
}


@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSearchFilterChipsRow() {
    ProvideCompositionLocalsForPreview {
        Surface {
            SearchFilterChipsRow(
                createTestSearchFilterState(),
                { _, _ -> },
                { _, _ -> },
            )
        }
    }
}

@TestOnly
fun createTestSearchFilterState(): SearchFilterState {
    val random = Random(42)
    return SearchFilterState(
        chips = SearchFilterState.DEFAULT_TAG_KINDS.map { kind ->
            SearchFilterChipState(
                kind = kind,
                values = kind.values,
                selected = if (random.nextBoolean()) {
                    kind.values.take(random.nextInt(1, kind.values.size))
                } else {
                    emptyList()
                },
            )
        },
    )
}

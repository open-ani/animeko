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
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_audience
import me.him188.ani.app.ui.lang.exploration_search_filter_category
import me.him188.ani.app.ui.lang.exploration_search_filter_character
import me.him188.ani.app.ui.lang.exploration_search_filter_custom
import me.him188.ani.app.ui.lang.exploration_search_filter_emotion
import me.him188.ani.app.ui.lang.exploration_search_filter_genre
import me.him188.ani.app.ui.lang.exploration_search_filter_rating
import me.him188.ani.app.ui.lang.exploration_search_filter_region
import me.him188.ani.app.ui.lang.exploration_search_filter_series
import me.him188.ani.app.ui.lang.exploration_search_filter_setting
import me.him188.ani.app.ui.lang.exploration_search_filter_source
import me.him188.ani.app.ui.lang.exploration_search_filter_technology
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
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
    val labels = rememberSearchFilterLabels()
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val textLayout = rememberTextMeasurer(1)
    val density = LocalDensity.current
    val styleLabelLarge = MaterialTheme.typography.labelLarge
    val maxWidth = remember(textLayout, density, styleLabelLarge) {
        with(density) {
            textLayout.measure(
                "placeholder",
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
                    renderChipLabel(state, labels),
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
    labels: SearchFilterLabels,
): String {
    if (state.hasSelection) {
        return state.selected.joinToString(",")
    }
    return when (state.kind) {
        CanonicalTagKind.Audience -> labels.audience
        CanonicalTagKind.Category -> labels.category
        CanonicalTagKind.Character -> labels.character
        CanonicalTagKind.Emotion -> labels.emotion
        CanonicalTagKind.Genre -> labels.genre
        CanonicalTagKind.Rating -> labels.rating
        CanonicalTagKind.Region -> labels.region
        CanonicalTagKind.Series -> labels.series
        CanonicalTagKind.Setting -> labels.setting
        CanonicalTagKind.Source -> labels.source
        CanonicalTagKind.Technology -> labels.technology
        null -> labels.custom
    }
}

@Immutable
private data class SearchFilterLabels(
    val audience: String,
    val category: String,
    val character: String,
    val emotion: String,
    val genre: String,
    val rating: String,
    val region: String,
    val series: String,
    val setting: String,
    val source: String,
    val technology: String,
    val custom: String,
)

@Composable
private fun rememberSearchFilterLabels(): SearchFilterLabels = SearchFilterLabels(
    audience = stringResource(Lang.exploration_search_filter_audience),
    category = stringResource(Lang.exploration_search_filter_category),
    character = stringResource(Lang.exploration_search_filter_character),
    emotion = stringResource(Lang.exploration_search_filter_emotion),
    genre = stringResource(Lang.exploration_search_filter_genre),
    rating = stringResource(Lang.exploration_search_filter_rating),
    region = stringResource(Lang.exploration_search_filter_region),
    series = stringResource(Lang.exploration_search_filter_series),
    setting = stringResource(Lang.exploration_search_filter_setting),
    source = stringResource(Lang.exploration_search_filter_source),
    technology = stringResource(Lang.exploration_search_filter_technology),
    custom = stringResource(Lang.exploration_search_filter_custom),
)


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

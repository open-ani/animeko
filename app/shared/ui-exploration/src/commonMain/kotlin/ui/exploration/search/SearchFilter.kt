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
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import me.him188.ani.app.ui.lang.exploration_search_filter_season_all
import me.him188.ani.app.ui.lang.exploration_search_filter_series
import me.him188.ani.app.ui.lang.exploration_search_filter_setting
import me.him188.ani.app.ui.lang.exploration_search_filter_source
import me.him188.ani.app.ui.lang.exploration_search_filter_technology
import me.him188.ani.app.ui.lang.exploration_search_filter_year_all
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

@Composable
fun SearchFilterChipsRow(
    state: SearchFilterState,
    onClickItemText: (SearchFilterChipState, value: String) -> Unit,
    onCheckedChange: (SearchFilterChipState, value: String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 渲染在标签 chips 之前的自定义内容 (如年份/季度筛选), 与标签 chips 共处同一 FlowRow.
     */
    leadingContent: @Composable FlowRowScope.() -> Unit = {},
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leadingContent()
        for (chipState in state.chips) {
            SearchFilterChip(
                chipState,
                { onClickItemText(chipState, it) },
                { onCheckedChange(chipState, it) },
            )
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

private data class DropdownChipOption(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 单选下拉筛选 chip 的共享骨架: InputChip + DropdownMenu.
 *
 * [label] 显示在 chip 上; 下拉菜单逐项渲染 [options].
 */
@Composable
private fun SingleSelectFilterChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    options: List<DropdownChipOption>,
    modifier: Modifier = Modifier,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        InputChip(
            selected = selected,
            onClick = { showDropdown = true },
            enabled = enabled,
            label = {
                Text(
                    label,
                    Modifier.widthIn(max = 120.dp),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Rounded.ArrowDropDown, null,
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        option.onClick()
                        showDropdown = false
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                )
            }
        }
    }
}

/**
 * 番剧索引的年份筛选 chip. [selectedYear] 为 null 表示"全部年份".
 */
@Composable
fun YearFilterChip(
    years: List<Int>,
    selectedYear: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allYearsText = stringResource(Lang.exploration_search_filter_year_all)
    SingleSelectFilterChip(
        selected = selectedYear != null,
        enabled = true,
        label = selectedYear?.toString() ?: allYearsText,
        options = buildList {
            add(DropdownChipOption(allYearsText) { onSelect(null) })
            for (year in years) {
                add(DropdownChipOption(year.toString()) { onSelect(year) })
            }
        },
        modifier = modifier,
    )
}

/**
 * 番剧索引的季度筛选 chip. 季度从属于年份: [enabled] 为 false 时 (未选年份) 禁用.
 *
 * [selectedSeason] 为 null 表示"全部季度"; 选项按 [AnimeSeason] 显示为 Q1..Q4
 * (即冬/春/夏/秋档).
 */
@Composable
fun SeasonFilterChip(
    selectedSeason: AnimeSeason?,
    onSelect: (AnimeSeason?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val allSeasonsText = stringResource(Lang.exploration_search_filter_season_all)
    SingleSelectFilterChip(
        selected = selectedSeason != null,
        enabled = enabled,
        label = selectedSeason?.let { "Q${it.quarterNumber}" } ?: allSeasonsText,
        options = buildList {
            add(DropdownChipOption(allSeasonsText) { onSelect(null) })
            for (season in AnimeSeason.entries) {
                add(DropdownChipOption("Q${season.quarterNumber}") { onSelect(season) })
            }
        },
        modifier = modifier,
    )
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

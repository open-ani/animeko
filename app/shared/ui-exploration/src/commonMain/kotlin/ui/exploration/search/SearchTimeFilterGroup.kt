/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.ui.foundation.lists.LazyListVerticalScrollbar
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_all
import me.him188.ani.app.ui.lang.exploration_search_filter_season
import me.him188.ani.app.ui.lang.exploration_search_filter_time
import me.him188.ani.app.ui.lang.exploration_search_filter_year
import me.him188.ani.app.ui.lang.exploration_search_filter_year_all
import org.jetbrains.compose.resources.stringResource

internal const val SearchFilterTimeGroupKey = "time"

@Composable
internal fun SearchTimeFilterGroup(
    years: List<Int>,
    selectedYear: Int?,
    selectedSeason: AnimeSeason?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onYearChange: (Int?) -> Unit,
    onSeasonChange: (AnimeSeason?) -> Unit,
) {
    SearchFilterGroupSection(
        label = stringResource(Lang.exploration_search_filter_time),
        groupKey = SearchFilterTimeGroupKey,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(SearchTimeFilterTestTags.Content),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Lang.exploration_search_filter_year),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchSidebarYearSelector(years, selectedYear, onYearChange)
            Text(
                stringResource(Lang.exploration_search_filter_season),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SearchFilterSidePanelChip(
                    selected = selectedSeason == null,
                    onClick = { onSeasonChange(null) },
                    label = stringResource(Lang.exploration_search_filter_all),
                    modifier = Modifier.testTag(SearchTimeFilterTestTags.season(null)),
                    enabled = selectedYear != null,
                )
                for (season in AnimeSeason.entries) {
                    SearchFilterSidePanelChip(
                        selected = selectedSeason == season,
                        onClick = { onSeasonChange(season) },
                        label = "Q${season.quarterNumber}",
                        modifier = Modifier.testTag(SearchTimeFilterTestTags.season(season)),
                        enabled = selectedYear != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSidebarYearSelector(
    years: List<Int>,
    selectedYear: Int?,
    onSelect: (Int?) -> Unit,
) {
    var showPopup by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    val allYearsText = stringResource(Lang.exploration_search_filter_year_all)
    val density = LocalDensity.current
    val spacingPx = with(density) { 6.dp.roundToPx() }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }) {
        Surface(
            onClick = { showPopup = !showPopup },
            modifier = Modifier.fillMaxWidth().testTag(SearchTimeFilterTestTags.YearSelector),
            shape = MaterialTheme.shapes.small,
            color = if (selectedYear != null) colors.primaryContainer else colors.surfaceContainerLowest,
            contentColor = if (selectedYear != null) colors.onPrimaryContainer else colors.onSurfaceVariant,
            border = BorderStroke(
                1.dp,
                if (showPopup || selectedYear != null) colors.primary else colors.outlineVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedYear?.toString() ?: allYearsText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    if (showPopup) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showPopup) {
            val sortedYears = remember(years, selectedYear) {
                (years + listOfNotNull(selectedYear)).distinct().sortedDescending()
            }
            val yearRows = remember(sortedYears) { sortedYears.chunked(3) }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex =
                    ((selectedYear?.let { sortedYears.indexOf(it) } ?: 0) / 3 - 2).coerceAtLeast(0),
            )
            Popup(
                popupPositionProvider = remember(spacingPx) { SearchYearPopupPositionProvider(spacingPx) },
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .testTag(SearchTimeFilterTestTags.YearPopup),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchFilterSidePanelChip(
                            selected = selectedYear == null,
                            onClick = {
                                showPopup = false
                                onSelect(null)
                            },
                            label = allYearsText,
                            modifier = Modifier.fillMaxWidth().testTag(SearchTimeFilterTestTags.year(null)),
                        )
                        Box(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(yearRows, key = { it.first() }) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        for (year in row) {
                                            SearchFilterSidePanelChip(
                                                selected = selectedYear == year,
                                                onClick = {
                                                    showPopup = false
                                                    onSelect(year)
                                                },
                                                label = year.toString(),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag(SearchTimeFilterTestTags.year(year)),
                                            )
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                            Box(Modifier.matchParentSize()) {
                                LazyListVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SearchYearPopupPositionProvider(private val spacing: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val below = anchorBounds.bottom + spacing
        val above = anchorBounds.top - spacing - popupContentSize.height
        val y = if (below + popupContentSize.height <= windowSize.height - spacing) below else above
        return IntOffset(
            x.coerceIn(spacing, (windowSize.width - popupContentSize.width - spacing).coerceAtLeast(spacing)),
            y.coerceIn(spacing, (windowSize.height - popupContentSize.height - spacing).coerceAtLeast(spacing)),
        )
    }
}

internal object SearchTimeFilterTestTags {
    const val Content = "search-time-filter-content"
    const val YearSelector = "search-time-filter-year-selector"
    const val YearPopup = "search-time-filter-year-popup"

    fun year(year: Int?): String = "search-time-filter-year-${year ?: "all"}"
    fun season(season: AnimeSeason?): String = "search-time-filter-season-${season?.quarterNumber ?: "all"}"
}

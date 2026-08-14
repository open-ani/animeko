/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.ui.foundation.lists.LazyListVerticalScrollbar
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_all
import me.him188.ani.app.ui.lang.subject_episode_collapse
import me.him188.ani.app.ui.lang.subject_episode_expand
import org.jetbrains.compose.resources.stringResource

internal val SearchFilterSidePanelWidth = 330.dp

@Composable
internal fun SearchFilterSidePanel(
    state: SearchFilterState,
    collapsedGroupKeys: Collection<String>,
    onToggleGroup: (groupKey: String) -> Unit,
    onCheckedChange: (SearchFilterChipState, value: String) -> Unit,
    onClearGroup: (SearchFilterChipState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier.testTag(SearchFilterSidePanelTestTags.Panel),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = state.chips.size,
                    key = { index -> state.chips[index].groupKey },
                ) { index ->
                    val chipState = state.chips[index]
                    val groupKey = chipState.groupKey
                    SearchFilterGroup(
                        state = chipState,
                        expanded = groupKey !in collapsedGroupKeys,
                        onToggleExpanded = { onToggleGroup(groupKey) },
                        onCheckedChange = { onCheckedChange(chipState, it) },
                        onClear = { onClearGroup(chipState) },
                    )
                }
            }

            LazyListVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun SearchFilterGroup(
    state: SearchFilterChipState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCheckedChange: (value: String) -> Unit,
    onClear: () -> Unit,
) {
    val groupLabel = searchFilterGroupLabel(state)
    val expandText = stringResource(Lang.subject_episode_expand)
    val collapseText = stringResource(Lang.subject_episode_collapse)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onToggleExpanded,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SearchFilterSidePanelTestTags.group(state.groupKey)),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = groupLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = if (expanded) collapseText else expandText,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val allText = stringResource(Lang.exploration_search_filter_all)
                SearchFilterSidePanelChip(
                    selected = !state.hasSelection,
                    onClick = {
                        if (state.hasSelection) {
                            onClear()
                        }
                    },
                    label = allText,
                    modifier = Modifier.testTag(SearchFilterSidePanelTestTags.all(state.groupKey)),
                )

                for (value in state.values) {
                    val selected = value in state.selected
                    SearchFilterSidePanelChip(
                        selected = selected,
                        onClick = { onCheckedChange(value) },
                        label = value,
                        modifier = Modifier.testTag(
                            SearchFilterSidePanelTestTags.value(state.groupKey, value),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterSidePanelChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        selected && hovered -> colors.primaryContainer.copy(alpha = 0.78f)
        selected -> colors.primaryContainer
        hovered -> colors.primaryContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val contentColor = when {
        selected || hovered -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    val borderColor = when {
        selected -> colors.primary
        hovered -> colors.primary.copy(alpha = 0.55f)
        else -> colors.outlineVariant
    }

    Surface(
        modifier = modifier
            .hoverable(interactionSource)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal val SearchFilterChipState.groupKey: String
    get() = when (kind) {
        CanonicalTagKind.Audience -> "audience"
        CanonicalTagKind.Category -> "category"
        CanonicalTagKind.Character -> "character"
        CanonicalTagKind.Emotion -> "emotion"
        CanonicalTagKind.Genre -> "genre"
        CanonicalTagKind.Rating -> "rating"
        CanonicalTagKind.Region -> "region"
        CanonicalTagKind.Series -> "series"
        CanonicalTagKind.Setting -> "setting"
        CanonicalTagKind.Source -> "source"
        CanonicalTagKind.Technology -> "technology"
        null -> "custom"
    }

internal object SearchFilterSidePanelTestTags {
    const val Panel = "search-filter-side-panel"

    fun group(groupKey: String): String = "search-filter-group-$groupKey"

    fun all(groupKey: String): String = "search-filter-all-$groupKey"

    fun value(groupKey: String, value: String): String = "search-filter-value-$groupKey-$value"
}

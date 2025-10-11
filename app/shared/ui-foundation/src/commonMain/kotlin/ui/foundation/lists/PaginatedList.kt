/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 通用分页列表组件，支持分组显示和分页导航
 *
 * @param T 列表项数据类型
 * @param state 分页列表状态
 * @param modifier 修饰符
 * @param contentPadding 内容内边距
 * @param itemContent 列表项内容Composable
 * @param headerContent 分组标题内容Composable（可选）
 * @param onItemClick 列表项点击回调（可选）
 */
@Composable
fun <T> PaginatedList(
    state: PaginatedListState<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    headerContent: @Composable (group: PaginatedGroup<T>) -> Unit = { DefaultGroupHeader(title = it.title) },
    onItemClick: ((T) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {

    Column(modifier = modifier) {
        // 分页导航条
        PaginatedListNavigation(
            state = state,
            modifier = Modifier.fillMaxWidth(),
        )

        // 分组列表内容
        LazyColumn(
            state = state.listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        ) {
            state.groups.forEach { group ->
                item(key = "PaginatedList_header_${group.groupIndex}") {
                    headerContent(group)
                }

                items(
                    items = group.items,
                    key = { "PaginatedList_${group.groupIndex}_${it.hashCode()}" },
                ) { item ->
                    if (onItemClick != null) {
                        Surface(
                            onClick = { onItemClick(item) },
                            color = Color.Transparent,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            itemContent(item)
                        }
                    } else {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

/**
 * 分页列表导航组件
 */
@Composable
private fun <T> PaginatedListNavigation(
    state: PaginatedListState<T>,
    modifier: Modifier = Modifier,
) {
    var showGroupSelector by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 上一组按钮
        IconButton(
            onClick = { state.navigateToPreviousGroup() },
            enabled = state.canNavigateToPreviousGroup,
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = "上一组",
                tint = if (state.canNavigateToPreviousGroup) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }

        // 分组选择器
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { showGroupSelector = true },
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.currentGroup?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = "选择分组",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 分组下拉菜单
            DropdownMenu(
                expanded = showGroupSelector,
                onDismissRequest = { showGroupSelector = false },
            ) {
                state.groups.forEachIndexed { index, group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = group.title,
                                color = if (index == state.currentGroupIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            state.navigateToGroup(index)
                            showGroupSelector = false
                        },
                    )
                }
            }
        }

        // 下一组按钮
        IconButton(
            onClick = { state.navigateToNextGroup() },
            enabled = state.canNavigateToNextGroup,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "下一组",
                tint = if (state.canNavigateToNextGroup) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

/**
 * 默认分组标题组件
 */
@Composable
fun DefaultGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
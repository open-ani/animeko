/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.lists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


/**
 * 可分页列表的分组配置
 */
interface PaginatedGroupConfig<T> {
    /**
     * 每个分组的最大条目数
     */
    val itemsPerGroup: Int

    /**
     * 生成指定分组的标题
     */
    fun generateGroupTitle(groupIndex: Int, itemsInGroup: List<T>): String
}

/**
 * 分页列表分组数据
 */
@Stable
data class PaginatedGroup<T>(
    val title: String,
    val items: List<T>,
    val startIndex: Int,
    val groupIndex: Int,
)

/**
 * 分页列表状态管理类
 *
 * 负责管理分页列表的分组、导航、定位等核心逻辑
 */
@Stable
class PaginatedListState<T>(
    private val items: List<T>,
    private val config: PaginatedGroupConfig<T>,
    val listState: LazyListState = LazyListState(),
    private val coroutineScope: CoroutineScope,
) {
    /**
     * 分组后的数据
     */
    val groups: List<PaginatedGroup<T>> by derivedStateOf {
        items.chunked(config.itemsPerGroup).mapIndexed { groupIndex, chunk ->
            val startItemIndex = groupIndex * config.itemsPerGroup
            PaginatedGroup(
                title = config.generateGroupTitle(groupIndex, chunk),
                items = chunk,
                startIndex = startItemIndex,
                groupIndex = groupIndex,
            )
        }
    }

    /**
     * 当前分组索引
     */
    var currentGroupIndex by mutableStateOf(0)

    /**
     * 导航到指定分组，并自动滚动到对应位置
     */
    fun navigateToGroup(groupIndex: Int) {
        if (groupIndex in 0 until totalGroupsCount) {
            currentGroupIndex = groupIndex
            // 分组变化时自动滚动到对应位置
            coroutineScope.launch {
                val targetIndex = groupStartIndices.getOrNull(groupIndex) ?: 0
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    /**
     * 导航到上一组
     */
    fun navigateToPreviousGroup() {
        if (canNavigateToPreviousGroup) {
            navigateToGroup(currentGroupIndex - 1)
        }
    }

    /**
     * 导航到下一组
     */
    fun navigateToNextGroup() {
        if (canNavigateToNextGroup) {
            navigateToGroup(currentGroupIndex + 1)
        }
    }

    /**
     * 每个分组在列表中的起始索引
     */
    val groupStartIndices: List<Int> by derivedStateOf {
        var index = 0
        groups.map { group ->
            val startIndex = index
            index++ // group header
            index += group.items.size // items
            startIndex
        }
    }

    /**
     * 总分组数
     */
    val totalGroupsCount: Int get() = groups.size

    /**
     * 当前分组数据
     */
    val currentGroup: PaginatedGroup<T>?
        get() = groups.getOrNull(currentGroupIndex)

    /**
     * 是否可以导航到上一组
     */
    val canNavigateToPreviousGroup: Boolean
        get() = currentGroupIndex > 0

    /**
     * 是否可以导航到下一组
     */
    val canNavigateToNextGroup: Boolean
        get() = currentGroupIndex < totalGroupsCount - 1

    /**
     * 计算指定条目在列表中的精确位置
     */
    fun calculateItemPosition(itemIndexInCurrentGroup: Int): Int? {
        if (itemIndexInCurrentGroup < 0 || itemIndexInCurrentGroup >= items.size) return null

        val targetGroupIndex = itemIndexInCurrentGroup / config.itemsPerGroup
        val positionInGroup = itemIndexInCurrentGroup % config.itemsPerGroup

        var listIndex = 0
        // Add all previous groups (header + items)
        for (i in 0 until targetGroupIndex) {
            listIndex += 1 + (groups.getOrNull(i)?.items?.size ?: 0)
        }
        // Add current group header + item position
        listIndex += 1 + positionInGroup

        return listIndex
    }

    /**
     * 将指定条目滚动到可视区域
     *
     * @param itemIndex 要滚动到的条目索引
     * @param animate 是否使用动画滚动
     */
    suspend fun bringIntoView(itemIndex: Int, animate: Boolean = true) {
        if (itemIndex < 0 || itemIndex >= items.size) return

        val targetGroupIndex = itemIndex / config.itemsPerGroup
        // 导航到对应的分组
        currentGroupIndex = targetGroupIndex

        // 计算条目在列表中的精确位置并滚动
        val itemPosition = calculateItemPosition(itemIndex)
        if (itemPosition != null) {
            if (animate) {
                listState.animateScrollToItem(itemPosition)
            } else {
                listState.scrollToItem(itemPosition)
            }
        }
    }

}

/**
 * 记住并创建分页列表状态
 */
@Composable
fun <T> rememberPaginatedListState(
    items: List<T>,
    config: PaginatedGroupConfig<T>,
    key: Any? = null,
    listState: LazyListState = rememberLazyListState(),
): PaginatedListState<T> {
    val coroutineScope = rememberCoroutineScope()
    return remember(key) {
        PaginatedListState(items, config, listState, coroutineScope)
    }
}
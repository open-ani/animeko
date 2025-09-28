/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 分组数据接口，定义分组的基本属性
 */
interface GroupData {
    val title: String
}

/**
 * 可分页列表的分组配置
 */
interface PaginatedGroupConfig<T> {
    /**
     * 每个分组的最大条目数
     */
    val itemsPerGroup: Int

    /**
     * 检查某个条目是否属于指定的播放状态
     */
    fun isPlaying(item: T): Boolean = false

    /**
     * 生成指定分组的标题
     */
    fun generateGroupTitle(groupIndex: Int, itemsInGroup: List<T>): String
}

/**
 * 默认的分组配置实现
 */
class DefaultPaginatedGroupConfig<T>(
    override val itemsPerGroup: Int,
    private val playingChecker: ((T) -> Boolean)? = null,
    private val titleGenerator: (Int, List<T>) -> String,
) : PaginatedGroupConfig<T> {
    override fun isPlaying(item: T): Boolean = playingChecker?.invoke(item) ?: false
    override fun generateGroupTitle(groupIndex: Int, itemsInGroup: List<T>): String =
        titleGenerator(groupIndex, itemsInGroup)
}

/**
 * 分页列表分组数据
 */
@Stable
data class PaginatedGroup<T>(
    override val title: String,
    val items: List<T>,
    val startIndex: Int,
    val groupIndex: Int,
) : GroupData

/**
 * 分页列表状态管理类
 *
 * 负责管理分页列表的分组、导航、定位等核心逻辑
 */
@Stable
class PaginatedListState<T>(
    private val items: List<T>,
    private val config: PaginatedGroupConfig<T>,
    initialPlayingIndex: Int = -1,
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
     * 当前播放条目的索引
     */
    val playingIndex: Int by derivedStateOf {
        items.indexOfFirst { config.isPlaying(it) }
    }

    /**
     * 当前分组索引
     */
    var currentGroupIndex by mutableStateOf(
        if (initialPlayingIndex >= 0) initialPlayingIndex / config.itemsPerGroup else 0,
    )

    /**
     * 是否显示分组选择器
     */
    var showGroupSelector by mutableStateOf(false)

    /**
     * 是否处于初始定位状态
     */
    var isInitialPositioning by mutableStateOf(true)

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
     * 总条目数
     */
    val totalItemsCount: Int get() = items.size

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
    val canNavigateToPrevious: Boolean
        get() = currentGroupIndex > 0

    /**
     * 是否可以导航到下一组
     */
    val canNavigateToNext: Boolean
        get() = currentGroupIndex < totalGroupsCount - 1

    /**
     * 导航到上一组
     */
    fun navigateToPrevious() {
        if (canNavigateToPrevious) {
            currentGroupIndex--
        }
    }

    /**
     * 导航到下一组
     */
    fun navigateToNext() {
        if (canNavigateToNext) {
            currentGroupIndex++
        }
    }

    /**
     * 导航到指定分组
     */
    fun navigateToGroup(groupIndex: Int) {
        if (groupIndex in 0 until totalGroupsCount) {
            currentGroupIndex = groupIndex
        }
    }

    /**
     * 计算指定条目在列表中的精确位置
     */
    fun calculateItemPosition(targetItemIndex: Int): Int? {
        if (targetItemIndex < 0 || targetItemIndex >= items.size) return null

        val targetGroupIndex = targetItemIndex / config.itemsPerGroup
        val positionInGroup = targetItemIndex % config.itemsPerGroup

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
     * 根据播放状态自动设置初始分组
     */
    fun updateInitialGroupFromPlayingState() {
        if (isInitialPositioning && playingIndex >= 0) {
            val targetGroupIndex = playingIndex / config.itemsPerGroup
            currentGroupIndex = targetGroupIndex
        }
    }

    /**
     * 获取指定分组的标题
     */
    fun getGroupTitle(groupIndex: Int): String? {
        return groups.getOrNull(groupIndex)?.title ?: config.generateGroupTitle(groupIndex, emptyList())
    }

    companion object {
        /**
         * 创建简单的数字范围分组配置
         */
        fun <T> createRangeConfig(
            itemsPerGroup: Int,
            playingChecker: ((T) -> Boolean)? = null,
        ): PaginatedGroupConfig<T> = DefaultPaginatedGroupConfig(
            itemsPerGroup = itemsPerGroup,
            playingChecker = playingChecker,
            titleGenerator = { groupIndex, items ->
                val startItem = groupIndex * itemsPerGroup + 1
                val endItem = startItem + items.size - 1
                "第 $startItem-$endItem 话"
            },
        )
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
): PaginatedListState<T> = remember(key) {
    PaginatedListState(items, config)
}

/**
 * 记住并创建基于播放状态的分页列表状态
 */
@Composable
fun <T> rememberPaginatedListStateWithPlaying(
    items: List<T>,
    itemsPerGroup: Int,
    playingChecker: (T) -> Boolean = { false },
    key: Any? = null,
): PaginatedListState<T> = remember(key) {
    PaginatedListState(items, PaginatedListState.createRangeConfig(itemsPerGroup, playingChecker))
}
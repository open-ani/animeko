/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/*
 * 网格"聚焦第 N 项" + "边缘横向切换"原语 —— 协议都在本文件内, 全事件驱动 (§14.4-8):
 *
 * 1. 页面 [rememberTvGridFocus] 创建状态 (挂在页面的 TvFocusScope 上); 创建处同时装有
 *    交互取消观察: pending 在途时用户再按键 (快照事件) 即取消 —— 不用超时兜底.
 * 2. 每张网格卡挂 [tvGridFocusItem]: 追踪当前聚焦下标 + "待聚焦目标"的动态锚点
 *    (目标下标钳到列表末项; 送达即清 pending —— 焦点事件).
 * 3. 网格组合内装 [TvGridFocusState.SendFocusEffect]: pending 出现后等数据就绪
 *    (快照事件) -> 目标滚进视口 -> [TvFocusScope.request] (锚点附着事件送达).
 * 4. 程序化聚焦第 N 项: [TvGridFocusState.focusItem]. 边缘切换在此之上:
 *    网格容器挂 [tvGridEdgeSwitchKeys], 左/右缘按键时算出"对应位置" (同一行、进入
 *    方向的近缘列) 作为目标, 再回调 onSwitch 让页面切换数据源 (相邻 tab/分类).
 *
 * pending 的三个事件出口: 目标聚焦 (清) / 用户再交互 (取消) / 调用方确定目标不会出现
 * (如分页确定空列表, 调 [TvGridFocusState.cancel]). 切换期间调用方应冻结"聚焦即选中"
 * 类副作用 (读 [TvGridFocusState.switching]): 旧网格聚焦卡销毁瞬间焦点会跌落到布局
 * 首个可聚焦节点, 其副作用会抢走刚选的目标 (TV 模拟器实测: 不冻结会连跳两个分类).
 */

/**
 * 网格焦点状态 (聚焦第 N 项 / 边缘切换). 经 [rememberTvGridFocus] 创建; 协议见文件头.
 */
@Stable
class TvGridFocusState internal constructor(internal val scope: TvFocusScope) {
    /** "待聚焦目标"的动态锚点 key (身份唯一, 不与页面 enum 冲突). */
    internal val entryKey: TvFocusKey = object : TvFocusKey {
        override fun toString(): String = "TvGridFocusEntry"
    }

    /** 当前聚焦卡下标 (由 [tvGridFocusItem] 上报; 仅按键判定读, 非 snapshot 状态). */
    internal var focusedIndex: Int = -1

    /** 已解析的待聚焦目标下标 (锚点匹配用); null = 无已解析目标. */
    var pendingIndex: Int? by mutableStateOf(null)
        private set

    /** 待按目标网格列数解析的 (row, direction) 目标 (边缘切换: 同行近缘列). */
    private var pendingRowEdge: Pair<Int, Int>? by mutableStateOf(null)

    /** 请求代数 ([SendFocusEffect] 的重启键: 同目标连续请求也能重新触发). */
    private var requestGeneration by mutableStateOf(0)

    /** 发起请求时的用户交互代数: 之后的交互 (代数变化) = 用户接管, 取消请求. */
    internal var navGenerationAtRequest: Int = 0
        private set

    /** 是否有在途的送焦请求 (期间调用方应冻结"聚焦即选中"类副作用, 见文件头). */
    val switching: Boolean get() = pendingIndex != null || pendingRowEdge != null

    /** 程序化聚焦第 [index] 项 (越界会钳到末项; 由 [SendFocusEffect] 消化). */
    fun focusItem(index: Int) {
        navGenerationAtRequest = scope.userNavGeneration
        pendingRowEdge = null
        pendingIndex = index
        requestGeneration++
    }

    /**
     * 聚焦第 [row] 行的近缘列 (边缘切换的"对应位置"): [direction] > 0 = 从左进入落行首列,
     * < 0 = 从右进入落行尾列. 列数按**目标网格**的实际布局解析 (源网格列数可能不同).
     */
    fun focusRowEdge(row: Int, direction: Int) {
        navGenerationAtRequest = scope.userNavGeneration
        pendingIndex = null
        pendingRowEdge = row to direction
        requestGeneration++
    }

    /** 取消在途请求 (调用方确定目标不会出现, 如分页确定空列表). */
    fun cancel() {
        pendingIndex = null
        pendingRowEdge = null
    }

    /**
     * 送焦效应: 网格组合内装一次. 等数据就绪 (快照事件) -> 行缘目标按目标网格布局列数
     * 解析为下标 (布局事件) -> 目标滚进视口 -> [TvFocusScope.request] (锚点附着事件送达,
     * 无超时; pending 出口见文件头). [itemCount] 须读 snapshot 状态.
     */
    @Composable
    fun SendFocusEffect(gridState: LazyGridState, itemCount: () -> Int) {
        LaunchedEffect(requestGeneration, gridState) {
            if (pendingIndex == null && pendingRowEdge == null) return@LaunchedEffect
            snapshotFlow { itemCount() }.first { it > 0 }
            pendingRowEdge?.let { (row, direction) ->
                // 等目标网格首帧布局 (快照事件) 拿实际列数
                val columns = snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }
                }.first { it != null }!! + 1
                pendingRowEdge = null
                pendingIndex = (row * columns + if (direction > 0) 0 else columns - 1)
                    .coerceAtMost(itemCount() - 1)
                    .coerceAtLeast(0)
            }
            val target = (pendingIndex ?: return@LaunchedEffect).coerceAtMost(itemCount() - 1)
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                gridState.scrollToItem(target)
            }
            scope.request(entryKey)
        }
    }
}

/**
 * 创建 [TvGridFocusState] (挂在页面的 [scope] 上), 并装交互取消观察:
 * 在途请求期间用户再按方向/确认键即取消 (代数对比排除发起切换的那次按键).
 */
@Composable
fun rememberTvGridFocus(scope: TvFocusScope): TvGridFocusState {
    val state = remember(scope) { TvGridFocusState(scope) }
    LaunchedEffect(state) {
        snapshotFlow { scope.userNavGeneration }
            .drop(1) // 首值为当前代数, 非事件
            .collect { generation ->
                if (state.switching && generation != state.navGenerationAtRequest) {
                    state.cancel()
                }
            }
    }
    return state
}

/**
 * 网格卡接线 (每张卡都挂): 聚焦时上报下标; 本卡是"待聚焦目标" (钳到 [itemCount] 末项)
 * 时挂动态锚点, 送达即清 pending (焦点事件出口).
 */
fun Modifier.tvGridFocusItem(
    state: TvGridFocusState,
    index: Int,
    itemCount: Int,
): Modifier {
    val targetIndex = state.pendingIndex?.coerceAtMost(itemCount - 1)
    return this
        .then(
            if (index == targetIndex) {
                Modifier.tvFocusAnchor(state.scope, state.entryKey)
            } else Modifier,
        )
        .onFocusChanged {
            if (it.isFocused) {
                state.focusedIndex = index
                if (index == targetIndex) state.cancel()
            }
        }
}

/**
 * 网格容器的边缘横向切换按键 (挂网格 modifier): 聚焦卡在行首列按左 / 行尾列 (含末项)
 * 按右时, 以"对应位置" (同一行、进入方向的近缘列) 为目标调 [TvGridFocusState.focusItem],
 * 并回调 [onSwitch] 让页面切换数据源 (相邻 tab/分类).
 *
 * 边界语义: [hasAdjacent] 为 false 时, 左缘不消费 (交给空间搜索 -> 侧边栏),
 * 右缘消费掉原地不动. 非边缘一律不消费 (行内空间导航). 按住连发只认第一次.
 */
fun Modifier.tvGridEdgeSwitchKeys(
    state: TvGridFocusState,
    gridState: LazyGridState,
    itemCount: () -> Int,
    hasAdjacent: (direction: Int) -> Boolean,
    onSwitch: (direction: Int) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val direction = when (event.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        else -> return@onPreviewKeyEvent false
    }
    val layout = gridState.layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.index == state.focusedIndex }
        ?: return@onPreviewKeyEvent false
    val columns = (layout.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
    val atEdge = if (direction < 0) {
        info.column == 0
    } else {
        info.column == columns - 1 || info.index == itemCount() - 1
    }
    if (!atEdge) return@onPreviewKeyEvent false
    if (!hasAdjacent(direction)) {
        return@onPreviewKeyEvent direction > 0
    }
    if (event.type == KeyEventType.KeyDown && !event.isAutoRepeatCompat) {
        // "对应位置" = 同一行、进入方向的近缘列 (列数按目标网格布局解析, 见 focusRowEdge)
        state.focusRowEdge(info.row, direction)
        onSwitch(direction)
    }
    true
}

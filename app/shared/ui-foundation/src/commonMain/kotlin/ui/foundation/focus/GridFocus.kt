/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * TV 竖版海报网格的统一焦点落点协调器 (追番页/搜索结果页共用).
 *
 * 所有"把焦点送到网格某张卡"的入口 (同列上下导航 / 返回回首卡 / 顶部行下键落视口首行 /
 * 进页恢复焦点 / 跨 tab 行对齐) 都通过 [request]/[requestRow] 发出统一落点请求, 由网格所在
 * 组合内的 [resolve] 循环消化: 等数据就绪 → 解析目标下标 → 请求聚焦 (目标已组合时不打断
 * 吸顶动画) → 卡片 [onCardFocused] 确认到位, 不到位滚动让目标组合出来再试.
 *
 * 不能直连 requestFocus 的原因: 目标卡未组合时 requestFocus 被焦点系统静默拒绝
 * (runCatching 照样报成功), 按键被吞且不重试, 表现为焦点卡死; 统一走带到位确认的
 * 解析循环, 避免多套解析器各自维护退出条件时相互踩坑.
 *
 * 关于抢焦点: 解析期间每轮都会发一遍 requestFocus, 目标始终不到位时会烧满 attempts 轮
 * (~2s), 用户此刻按遥控器移动焦点就会被下一轮抢回去. 出口是 [onUserNavigation] ——
 * [gridKeyNavigation] 每次按键都递增计数, 解析发现计数变了立即放弃.
 *
 * 判据只认按键、不认"焦点落在别的卡上": 页面切换期间焦点系统会自行把默认焦点塞给第一个可聚焦
 * 元素, 在 onFocusChanged 里与用户按键无从区分; 而把那种焦点拉回目标恰恰是本解析器的职责
 * (进页恢复上次聚焦的卡就靠它), 误判成用户介入会导致恢复半途放弃.
 */
@Stable
class GridFocusController {
    /** 当前落点请求; null = 空闲. 调用方可据此判断"解析进行中" (如抑制 tab 的聚焦即选中). */
    var pending: TvGridFocusRequest? by mutableStateOf(null)
        private set

    /**
     * 解析出的目标卡下标 (该卡挂 [requester]); -1 = 无. 每次导航都会 设置->清除 变两次,
     * 使用处用 derivedStateOf 收窄成"是否目标卡"的布尔, 只让目标卡自己 (挂/摘请求器) 重组.
     */
    var resolvedIndex: Int by mutableIntStateOf(-1)
        private set

    /** 目标卡的焦点请求器: 卡片在 index == [resolvedIndex] 时挂载. */
    val requester: FocusRequester = FocusRequester()

    /** 过渡期隐形焦点驻留点的请求器; 由 [GridFocusTransitAnchor] 挂载, 用 [parkFocusOnAnchor] 聚焦. */
    val transitAnchor: FocusRequester = FocusRequester()

    // 目标卡真实拿到焦点的确认标志 (由 [onCardFocused] 置位). 不能拿"最后聚焦下标 == 目标"
    // 当退出条件: 目标恰好等于此前聚焦过的卡时 (如 tab 行下键落回吸顶可视首行行首, 而那正是
    // 离开网格前聚焦的卡) 会在焦点尚未移动时"假成功"提前退出.
    private var arrived = false

    // 用户按键计数 (由 [gridKeyNavigation] 递增). 解析只在"开始之后用户又按了键"时才让路 ——
    // 不能拿"焦点落在别的卡上"当介入判据: 页面切换期间焦点系统会自行分配默认焦点, 在
    // onFocusChanged 里与用户按键长得一模一样, 误判会让进页恢复半途放弃 (表现为退回第一个
    // tab 的第一张卡), 而拉回系统塞的默认焦点正是本解析器存在的理由之一.
    private var userKeys = 0

    /** 请求聚焦绝对下标 [index] 的卡 (超出数据量时夹到最后一张). */
    fun request(index: Int) {
        pending = TvGridFocusRequest(index = index, seq = (pending?.seq ?: 0) + 1)
    }

    /** 行对齐落点: 聚焦第 [row] 行的最左/最右卡, 行数不足时夹到最后一行对应端 (跨 tab 导航用). */
    fun requestRow(row: Int, rowStart: Boolean) {
        pending = TvGridFocusRequest(row = row, rowStart = rowStart, seq = (pending?.seq ?: 0) + 1)
    }

    /** 卡片 onFocused 中调用: 确认落点到位. */
    fun onCardFocused(index: Int) {
        if (index == resolvedIndex) arrived = true
    }

    /** [gridKeyNavigation] 中调用: 记录用户按了遥控器, 正在解析的落点据此让路. */
    fun onUserNavigation() {
        userKeys++
    }

    /**
     * 把焦点先钉到隐形锚点 ([GridFocusTransitAnchor]) 上, 再去做会销毁当前聚焦卡的数据切换
     * (追番页换 tab / 时间表换天 / 改收藏状态让条目离开本 tab).
     *
     * 必须**先** [request] 再调用本方法: 锚点只在"解析进行中"才可聚焦 (见 [GridFocusTransitAnchor]).
     * 不钉锚点的后果: 原卡随数据替换销毁, 焦点悬空, Compose 会 clearFocus 整棵树并按遍历顺序
     * 重分配 (见 FocusTargetNode.onReset/onDetach —— 源码明确**不**把焦点交给焦点祖先), 实测会
     * 闪到页面顶部的标签行上; 而此刻的按键 (长按方向键的连发) 也不再经过网格的键路由.
     */
    fun parkFocusOnAnchor(): Boolean = runCatching { transitAnchor.requestFocus() }.isSuccess

    /**
     * 常驻解析循环: 网格所在组合内 `LaunchedEffect(数据实例) { runResolveLoop(...) }` 调用,
     * **key 里不要放 [pending]**. 内部用 snapshotFlow 观察 [pending], 新请求会取消进行中的
     * 解析重新跑 (collectLatest, 与旧的 `LaunchedEffect(pending) { resolve(...) }` 重启语义一致).
     *
     * 之所以不再让调用方拿 [pending] 当 effect key: key 表达式在组合中求值 = 所在作用域订阅
     * 了这枚每次落点请求都 设置→清除 变两次的热状态, 每按一次上/下键网格宿主作用域就重组两遍,
     * 网格 content lambda (捕获 LazyPagingItems 等不稳定值) 随之换新实例 → **全部可见卡片
     * 全新重建** (2026-07-31 实测追番页 15s 内卡片重建 482 次, 为探索页同操作的 10 倍).
     */
    suspend fun runResolveLoop(
        gridState: LazyGridState,
        columns: () -> Int,
        itemCount: () -> Int,
        isLoadingFirstPage: () -> Boolean = { false },
        onEmptyIdle: (() -> Unit)? = null,
        attempts: Int = 80,
    ) {
        snapshotFlow { pending }.collectLatest { req ->
            if (req == null) return@collectLatest
            resolve(gridState, columns, itemCount, isLoadingFirstPage, onEmptyIdle, attempts)
        }
    }

    /**
     * 单次落点解析: 消化当前 [pending] (为 null 立即返回), 完成/放弃后置回 null.
     * 组合内的常驻观察请用 [runResolveLoop]; 本方法留给非组合驱动的一次性调用.
     * [onEmptyIdle] 非 null 时: 数据为空且不在首屏加载 → 执行收尾动作 (如聚焦 tab 标签) 并结束;
     * 为 null 时空数据只等待 (直到超时或数据到达).
     */
    suspend fun resolve(
        gridState: LazyGridState,
        columns: () -> Int,
        itemCount: () -> Int,
        isLoadingFirstPage: () -> Boolean = { false },
        onEmptyIdle: (() -> Unit)? = null,
        attempts: Int = 80,
    ) {
        val target = pending ?: return
        arrived = false
        // 用户按键基线: 之后计数一变就说明用户自己在导航, 立刻放弃本次落点
        val keysAtStart = userKeys
        repeat(attempts) {
            withFrameNanos { }
            // 用户已接手导航: 让路. 这是唯一的提前放弃条件 —— 系统自行分配的默认焦点不算,
            // 那种情况要继续重试把焦点拉回目标
            if (userKeys != keysAtStart) {
                pending = null
                resolvedIndex = -1
                return
            }
            val count = itemCount()
            val cols = columns().coerceAtLeast(1)
            if (count > 0) {
                val idx = target.resolveIndex(count, cols)
                resolvedIndex = idx
                withFrameNanos { } // 等目标卡上的请求器挂载
                runCatching { requester.requestFocus() }
                if (arrived) {
                    pending = null
                    resolvedIndex = -1
                    return
                }
                // 目标卡未组合 (聚焦失败): 滚过去让它组合出来再试
                runCatching { gridState.scrollToItem((idx / cols) * cols) }
            } else if (onEmptyIdle != null && !isLoadingFirstPage()) {
                onEmptyIdle()
                pending = null
                resolvedIndex = -1
                return
            }
            delay(30)
        }
        pending = null
        resolvedIndex = -1
    }
}

/**
 * 统一网格落点请求: [index] 非 null 时为绝对目标卡下标; 否则按 [row] + [rowStart] 行对齐 ——
 * 聚焦第 [row] 行的最左 ([rowStart]=true) / 最右卡. [seq] 使连续发出的同参请求也能重新触发解析.
 */
data class TvGridFocusRequest(
    val index: Int? = null,
    val row: Int = 0,
    val rowStart: Boolean = true,
    val seq: Int = 0,
) {
    /** 解析成绝对下标: [index] 优先 (夹到最后一张); 否则按行对齐, 行数不足时夹到最后一行对应端. */
    internal fun resolveIndex(count: Int, columns: Int): Int = index?.coerceAtMost(count - 1)
        ?: if (rowStart) {
            val i = row * columns
            if (i < count) i else ((count - 1) / columns) * columns
        } else {
            minOf(row * columns + columns - 1, count - 1)
        }
}

/**
 * 过渡期的隐形焦点驻留点 (1dp, 不可见, 无聚焦样式), 配合 [GridFocusController.parkFocusOnAnchor].
 *
 * 网格数据整批替换 (换 tab / 换天) 会销毁当前聚焦的卡片, 而目标卡要等新数据组合出来才能聚焦 ——
 * 这中间焦点无处可去. 把它先钉在本节点上: 焦点不悬空, 焦点系统就不会自行重分配 (实测会闪到页面
 * 顶部的标签行上), 落点解析把目标卡送到位后焦点自然离开.
 *
 * **只在过渡期间可聚焦** (解析进行中, 或 [extraCanFocus] 成立), 平时对方向搜索完全不可见 ——
 * 否则一个不可见节点会成为方向键的落点候选, 表现为"焦点圈不见了但还能走".
 *
 * 摆放位置: 放在**网格之外** (如网格上方), 不要放进网格内部 —— 行内左右键是交回默认方向搜索的,
 * 压在首卡位置上的锚点会成为候选.
 *
 * @param extraCanFocus 除"落点解析进行中"以外额外允许聚焦的条件 (如追番页等条目离开本 tab 期间)
 * @param onStranded 焦点仍在锚点上但锚点已不再允许聚焦时调用 (落点解析放弃 / 等待超时):
 *   焦点即将被系统收走, 调用方补一个自己的落点 (如聚焦选中的标签/日期胶囊)
 */
@Composable
fun GridFocusTransitAnchor(
    controller: GridFocusController,
    modifier: Modifier = Modifier,
    extraCanFocus: () -> Boolean = { false },
    onStranded: () -> Unit = {},
) {
    var hasFocus by remember { mutableStateOf(false) }
    val canFocus = { controller.pending != null || extraCanFocus() }
    val onStrandedState by rememberUpdatedState(onStranded)
    // snapshotFlow 而非 LaunchedEffect(key): extraCanFocus 读到的状态无法当 key, 交给快照系统观察
    LaunchedEffect(Unit) {
        snapshotFlow { hasFocus && !canFocus() }.collect { stranded ->
            if (stranded) onStrandedState()
        }
    }
    Box(
        modifier
            .size(GRID_TRANSIT_ANCHOR_SIZE)
            .focusRequester(controller.transitAnchor)
            .focusProperties { this.canFocus = canFocus() }
            .onFocusChanged { hasFocus = it.isFocused }
            // 焦点驻留期间吞掉方向键与确认键: 锚点不是真正的落点, 交回默认方向搜索的落点不可
            // 预测 (长按方向键跨 tab/跨天时连发尤其明显, 实测会闪到标签行), 确认键则会误触.
            // 过渡只有一两百毫秒, 期间丢掉的连发按键正好让"一次按住走一格"更可控
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key in GRID_TRANSIT_ANCHOR_SWALLOWED_KEYS
            }
            .focusable(),
    )
}

/** 隐形锚点的尺寸: 不能为 0 —— 零尺寸节点在部分版本上会被焦点系统跳过. */
private val GRID_TRANSIT_ANCHOR_SIZE = 1.dp

/** 焦点驻留在隐形锚点期间要吞掉的按键 (返回键不在内: 它走返回分发器, 由页面的分层规则处理). */
private val GRID_TRANSIT_ANCHOR_SWALLOWED_KEYS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
)

/**
 * 竖版海报网格的方向键路由 (追番页/搜索结果页共用), 配合 [GridFocusController]:
 * - 上/下键显式同列导航, 不交给默认方向搜索 —— 吸顶后上一行在视口外未组合, 越界组合只补出
 *   前一个 item (上一行最后一张), 焦点必然斜跳; 吸顶滚动进行中方向搜索又按瞬时几何位置挑
 *   候选, 偶尔斜跳到别的列. 顶行上键交 [onTopRowUp]; 末行不满时同列下方没有卡则落到最后
 *   一张; 已是最后一行则消费掉防斜跳.
 * - 播放/暂停键交 [onPlayKey] (聚焦卡直达播放).
 * - 其余 KeyDown 交 [extraKeys] (如追番页跨 tab 左右导航), 返回 false 走默认焦点搜索.
 */
fun Modifier.gridKeyNavigation(
    controller: GridFocusController,
    focusedIndex: () -> Int,
    itemCount: () -> Int,
    columns: () -> Int,
    onTopRowUp: () -> Boolean,
    onPlayKey: (focusedIndex: Int) -> Boolean,
    enabled: () -> Boolean = { true },
    extraKeys: ((event: KeyEvent, focusedIndex: Int, columns: Int, itemCount: Int) -> Boolean)? = null,
): Modifier = onPreviewKeyEvent { event ->
    if (!enabled()) return@onPreviewKeyEvent false
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    // 用户在导航: 正在进行的落点解析 (如进页恢复) 据此让路, 不把焦点抢回去
    controller.onUserNavigation()
    val focused = focusedIndex()
    val count = itemCount()
    if (focused < 0 || count == 0) return@onPreviewKeyEvent false
    val cols = columns().coerceAtLeast(1)
    when (event.key) {
        Key.DirectionUp ->
            if (focused < cols) {
                onTopRowUp()
            } else {
                controller.request(focused - cols)
                true
            }

        Key.DirectionDown -> {
            val next = focused + cols
            when {
                next < count -> {
                    controller.request(next)
                    true
                }

                // 末行不满时同列下方没有卡: 落到最后一张
                focused / cols < (count - 1) / cols -> {
                    controller.request(count - 1)
                    true
                }

                // 已是最后一行: 消费掉, 防止焦点斜跳
                else -> true
            }
        }

        Key.MediaPlayPause, Key.MediaPlay -> onPlayKey(focused)

        else -> extraKeys?.invoke(event, focused, cols, count) ?: false
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/*
 * TV 焦点框架的使用侧 API: 页面用这些 Modifier 扩展声明焦点层级与操作方式,
 * 调度逻辑集中在 [TvFocusScope]. 典型页面:
 *
 * ```
 * private enum class HomeFocus : TvFocusKey { Play, Cards, Rail }
 *
 * val focus = rememberTvFocusScope()
 * focus.Resolver()                      // 页面根部装一次
 * focus.InitialFocus(HomeFocus.Play)    // 进页初始焦点
 *
 * Modifier.tvFocusAnchor(focus, HomeFocus.Play)           // 标注锚点
 * Modifier.tvFocusLink(focus, down = HomeFocus.Cards)     // 显式方向链接
 * Modifier.tvFocusHotkey(focus, Key.Menu to HomeFocus.Rail) // 全局快捷键 (挂页面根)
 * focus.request(HomeFocus.Play)                           // 程序化聚焦 (返回分层等)
 * ```
 */

/**
 * 标注本节点为 [key] 锚点: 挂 FocusRequester + 焦点得失自动上报 (到位确认依据).
 *
 * 上报用 hasFocus (含子树): 锚点可以是容器 (如轮播行, 配合 focusRestorer 恢复行内
 * 上次聚焦的卡片), 也可以是叶子按钮.
 */
fun Modifier.tvFocusAnchor(scope: TvFocusScope, key: TvFocusKey): Modifier = this
    .focusRequester(scope.requesterOf(key))
    .onFocusChanged { scope.onAnchorFocusChanged(key, it.hasFocus) }

/**
 * 显式方向链接: 声明方向的焦点搜索直达目标锚点, 不走空间搜索.
 *
 * TV 上跨大段不可聚焦内容 (标题/指示器/渐变区) 的空间焦点搜索不可靠 (落错或落空),
 * 边缘元素应显式声明去向 —— 这是上游 PR 与本项目实测一致的结论.
 */
fun Modifier.tvFocusLink(
    scope: TvFocusScope,
    up: TvFocusKey? = null,
    down: TvFocusKey? = null,
    left: TvFocusKey? = null,
    right: TvFocusKey? = null,
): Modifier = focusProperties {
    up?.let { this.up = scope.requesterOf(it) }
    down?.let { this.down = scope.requesterOf(it) }
    left?.let { this.left = scope.requesterOf(it) }
    right?.let { this.right = scope.requesterOf(it) }
}

/**
 * 进入门控 (挂在焦点组容器上): 只有 [allow] 中的方向能把焦点移进本容器, 其余空间搜索
 * 一律取消; 允许进入时焦点总是落到 [entry] 锚点.
 *
 * [FocusDirection.Enter] 表示编程式聚焦 (requestFocus / 全局快捷键), 通常应包含 ——
 * 否则 [TvFocusScope.request] 送不进来.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvFocusEnterGate(
    scope: TvFocusScope,
    entry: TvFocusKey,
    allow: Set<FocusDirection> = setOf(FocusDirection.Left, FocusDirection.Enter),
): Modifier = tvFocusEnterGate(scope.requesterOf(entry), allow)

/** [tvFocusEnterGate] 的组件级重载: 不依赖 scope, 直接给进入落点 requester (如 SideRail). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvFocusEnterGate(
    entry: androidx.compose.ui.focus.FocusRequester,
    allow: Set<FocusDirection> = setOf(FocusDirection.Left, FocusDirection.Enter),
): Modifier = this
    .focusProperties {
        onEnter = {
            if (requestedFocusDirection in allow) {
                entry.requestFocus()
            } else {
                cancelFocus()
            }
        }
    }
    .focusGroup()

/**
 * 全局按键快捷跳转 (挂页面/壳根节点): 按下 [mappings] 中的键把焦点送到对应锚点.
 * KeyUp 一并消费, 不让残余触发别处; 按住的系统连发不重复触发. 焦点在页面任意深度都生效.
 *
 * 同时兼任 [tvFocusNavSignal]: 方向键按下时上报"用户在导航", 放弃在途的焦点解析.
 */
fun Modifier.tvFocusHotkey(
    scope: TvFocusScope,
    vararg mappings: Pair<Key, TvFocusKey>,
): Modifier = this
    .tvFocusNavSignal(scope)
    .onPreviewKeyEvent { event ->
        val target = mappings.firstOrNull { it.first == event.key }?.second
            ?: return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && !event.isAutoRepeatCompat) {
            scope.request(target)
        }
        true
    }

/**
 * 全局按键"去/回"切换 (挂壳/页面根): [hotkey] 按下时, 焦点不在 [target] 子树 -> 送往
 * [target]; 已在其中 -> 调用 [onLeave] (典型: 恢复内容区上次焦点, 目标容器随失焦自动收起).
 * 判据用锚点上报 ([TvFocusScope.isFocused]), 所以 [target] 必须挂有 [tvFocusAnchor].
 * KeyUp 一并消费; 按住连发不重复触发. 兼任 [tvFocusNavSignal].
 */
fun Modifier.tvFocusHotkeyToggle(
    scope: TvFocusScope,
    hotkey: Key,
    target: TvFocusKey,
    onLeave: () -> Unit,
): Modifier = this
    .tvFocusNavSignal(scope)
    .onPreviewKeyEvent { event ->
        if (event.key != hotkey) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && !event.isAutoRepeatCompat) {
            if (scope.isFocused(target)) onLeave() else scope.request(target)
        }
        true
    }

/**
 * 出口重定向 (挂焦点组容器): 焦点沿 [mappings] 中的方向离开本容器时不走空间搜索,
 * 直达对应锚点; 未声明的方向保持默认行为.
 *
 * 典型: 网格上缘按上应回"当前选中"的分类 tab —— 空间搜索只会落到几何最近的 tab.
 * 锚点可以是动态的 (如挂在当前选中项上), 重定向时取此刻挂着锚点的节点.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvFocusExit(
    scope: TvFocusScope,
    vararg mappings: Pair<FocusDirection, TvFocusKey>,
): Modifier = this
    .focusProperties {
        onExit = {
            mappings.firstOrNull { it.first == requestedFocusDirection }?.let {
                scope.requesterOf(it.second).requestFocus()
            }
        }
    }
    .focusGroup()

/**
 * 用户交互信号 (挂页面根节点): 方向键或确认键按下时上报 [TvFocusScope.notifyUserNavigation],
 * 放弃在途的焦点解析 —— 否则解析轮询会把用户刚移走的焦点抢回目标锚点. 不消费事件.
 * 确认键也算: 点击 (如侧边栏条目把焦点送回内容区) 引发的焦点变化同样不该被在途轮询抢回.
 *
 * 每个持有 [TvFocusScope] 的页面都应在根上挂本 modifier (或挂 [tvFocusHotkey], 它已兼任).
 */
fun Modifier.tvFocusNavSignal(scope: TvFocusScope): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key in TV_USER_INTERACTION_KEYS) {
        scope.notifyUserNavigation()
    }
    false // 只旁听, 不消费
}

private val TV_USER_INTERACTION_KEYS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
)

/** 本次 KeyDown 是否系统按住连发 (android nativeKeyEvent.repeatCount). */
private val androidx.compose.ui.input.key.KeyEvent.isAutoRepeatCompat: Boolean
    get() = (nativeKeyEvent as? android.view.KeyEvent)?.let { it.repeatCount > 0 } ?: false

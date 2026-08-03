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
): Modifier = this
    .focusProperties {
        onEnter = {
            if (requestedFocusDirection in allow) {
                scope.requesterOf(entry).requestFocus()
            } else {
                cancelFocus()
            }
        }
    }
    .focusGroup()

/**
 * 全局按键快捷跳转 (挂页面/壳根节点): 按下 [mappings] 中的键把焦点送到对应锚点.
 * KeyUp 一并消费, 不让残余触发别处. 焦点在页面任意深度都生效 (preview 自根下行).
 */
fun Modifier.tvFocusHotkey(
    scope: TvFocusScope,
    vararg mappings: Pair<Key, TvFocusKey>,
): Modifier = onPreviewKeyEvent { event ->
    val target = mappings.firstOrNull { it.first == event.key }?.second
        ?: return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) {
        scope.request(target)
    }
    true
}

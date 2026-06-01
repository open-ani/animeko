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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior

/**
 * Provides the [FocusRequester] for the comment-tab button in the episode page tab row.
 * When the user presses Back while inside the comment list, focus returns to this tab.
 *
 * Provided by episode screen composables; defaults to null (no-op on non-TV).
 */
val LocalCommentTabFocusRequester = compositionLocalOf<FocusRequester?> { null }

/**
 * Provides the [FocusRequester] for the primary action button in a subject-details pane
 * (e.g. the "Select Episode" button). Focus is requested here when the user explicitly
 * selects a subject from the search results list.
 *
 * Provided by search/detail screen composables; defaults to null (no-op on non-TV).
 */
val LocalDetailsFocusRequester = compositionLocalOf<FocusRequester?> { null }

/**
 * Modifier that ensures a default focusable element exists on TV platforms.
 * 
 * This should be applied to the root composable of any screen/page to ensure
 * that when the page is displayed, there is always a focusable element available.
 * 
 * On TV platforms:
 * - Creates a FocusRequester and applies it to the element
 * - Automatically requests focus when the element is composed
 * - Makes the element focusable
 * 
 * On non-TV platforms:
 * - Does nothing (returns the modifier unchanged)
 * 
 * Usage:
 * ```
 * Box(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .defaultFocus()
 * ) {
 *     // Your content
 * }
 * ```
 */
fun Modifier.defaultFocus(): Modifier = composed {
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    
    if (focusDriven) {
        val focusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        
        this
            .focusRequester(focusRequester)
            .focusable()
    } else {
        this
    }
}

/**
 * 弹窗/覆盖层关掉之后, 把焦点还回本元素 (= 打开它的那个按钮).
 *
 * Compose 在聚焦节点被移除时会清掉整棵树的焦点, **不会**交给祖先; 独立窗口的 Dialog 关闭时
 * 也不保证把焦点还给下层窗口原来的那个节点. 于是遥控器用户按返回之后就"没有焦点"了 ——
 * 看不到焦点圈, 方向键全无反应. 凡是打开弹窗的按钮都应该挂这个.
 *
 * 用法: `Modifier.restoreFocusAfter(state.showSomeDialog)`.
 *
 * 焦点导航设备之外 (手机/桌面鼠标) 不做任何事.
 *
 * @param overlayVisible 弹窗是否在场; 由 true 变 false 的那一刻开始找回焦点.
 * @param abandon 放弃找回的判据 (见 [resolveFocusRepeatedly]): 本元素所在的那一层自己也可能
 *   在弹窗关闭后随即退场 (如播放器控制层被自动隐藏), 那时焦点归属另有人负责, 继续重试就是跟
 *   它抢. 能观察到这种情况的调用点都该传 —— 不传就会烧满重试次数, 每次都发一遍 requestFocus.
 */
@Composable
fun Modifier.restoreFocusAfter(
    overlayVisible: Boolean,
    abandon: () -> Boolean = { false },
): Modifier {
    if (!LocalAniUiBehavior.current.focusDrivenNavigation) return this
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var everVisible by remember { mutableStateOf(false) }
    val abandonState by rememberUpdatedState(abandon)
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            everVisible = true
            return@LaunchedEffect
        }
        if (!everVisible) return@LaunchedEffect // 首次组合, 不是"关闭"
        everVisible = false
        // 到位判据用本元素的 hasFocus: requestFocus 在节点未附着时是静默失败, 不抛异常,
        // 拿"没抛异常"当到位会一次就收工 (见 resolveFocusRepeatedly 的文档)
        resolveFocusRepeatedly(
            attempts = 30,
            arrived = { hasFocus },
            abandon = { abandonState() },
        ) {
            runCatching { requester.requestFocus() }
        }
    }
    return this
        .focusRequester(requester)
        // hasFocus 而不是 isFocused: 请求器挂在容器上, 真正持焦的是它子树里的可点击节点
        .onFocusChanged { hasFocus = it.hasFocus }
}

/**
 * Composable wrapper that ensures default focus on TV platforms.
 * 
 * This is a convenience wrapper around defaultFocus() modifier.
 * It wraps the content in a focusable container that automatically
 * receives focus on TV platforms.
 * 
 * Usage:
 * ```
 * DefaultFocusContainer {
 *     // Your screen content
 * }
 * ```
 */
@Composable
fun DefaultFocusContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.defaultFocus()
    ) {
        content()
    }
}

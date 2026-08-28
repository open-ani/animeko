/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * iOS 风格的导航压暗蒙版: 被上层页面盖住的那一页会盖一层黑色蒙版, 随转场进度淡入淡出.
 *
 * UIKit 的 `UINavigationController` 就是这么干的 —— 新页面整宽划入的同时, 下层页面反向视差移动并被
 * 压暗. 蒙版贴在下层页面上, 会跟着它一起位移.
 *
 * ### 为什么需要记录页面深度
 *
 * 只有**下层**页面该被压暗, 而单看 `EnterExitState` 分不出谁是下层:
 *
 * | | 下层 (要压暗) | 上层 (不压) |
 * |---|---|---|
 * | 前进 | 正在退出的 | 正在进入的 |
 * | 返回 | 正在进入的 | 正在退出的 |
 *
 * 同一条 `Visible -> PostExit` 轨迹在两个方向上含义正好相反. 而"方向"这个信息 `NavDisplay` 没有暴露,
 * 手势返回时 back stack 还没变, 从 back stack 的增减也推不出来.
 *
 * 所以这里换个角度: 每个 entry 首次组合时记下自己在 back stack 里的深度, 转场期间**深度小的那个就是
 * 下层**. 这个判据对前进、返回、手势返回都成立, 不需要知道方向.
 *
 * ### 已知不准的情况
 *
 * "换根"式导航 (例如 onboarding 结束时 `navigateMain(popUpTargetInclusive = ...)`, 栈被清空后压入新页)
 * 会让新页面的深度比旧页面小, 于是压暗加在了新页面上. 这类转场整个 app 只有几处, 而且蒙版只有 12% 黑,
 * 暂时不处理.
 */
@Stable
class NavigationDimState internal constructor() {
    /** contentKey -> 首次组合时它在 back stack 里的深度. */
    private val composedDepths = mutableStateMapOf<Any, Int>()

    private val topDepth: Int by derivedStateOf { composedDepths.values.maxOrNull() ?: 0 }

    internal fun onEntryComposed(contentKey: Any, depth: Int) {
        composedDepths[contentKey] = depth
    }

    internal fun onEntryDisposed(contentKey: Any) {
        composedDepths.remove(contentKey)
    }

    /**
     * 当前在屏幕上的 entry 里, 这个是不是被盖住的那一层.
     */
    internal fun isLowerPage(contentKey: Any): Boolean {
        val depth = composedDepths[contentKey] ?: return false
        return depth < topDepth
    }
}

/**
 * 给 `NavDisplay` 用的压暗蒙版 decorator. 加到 `entryDecorators` 里就能对所有页面生效, 不用改每个 entry.
 *
 * decorator 的内容是在 `LocalNavAnimatedContentScope` 里被调用的, 所以蒙版能挂到 entry 自己的
 * transition 上 —— 手势返回时这条动画会跟着被 `seekTo`, 蒙版跟手指走.
 *
 * @param backStack 当前 back stack, 用来取每个 entry 首次组合时的深度.
 * @param maxAlpha 蒙版最大不透明度. 传 `0f` 关闭 (Material 那套动画本来就有淡入淡出, 再压暗会显脏).
 */
@Composable
fun <T : Any> rememberNavigationDimNavEntryDecorator(
    backStack: List<T>,
    maxAlpha: Float,
): NavEntryDecorator<T> {
    val state = remember { NavigationDimState() }
    val currentBackStack by rememberUpdatedState(backStack)
    return remember(state) {
        NavEntryDecorator { entry ->
            NavigationDimmedEntry(entry, state, { currentBackStack }, maxAlpha)
        }
    }
}

@Composable
private fun <T : Any> NavigationDimmedEntry(
    entry: NavEntry<T>,
    state: NavigationDimState,
    backStack: () -> List<T>,
    maxAlpha: Float,
) {
    val contentKey = entry.contentKey
    // 首次组合时取一次深度就够了: 之后这一页在栈里的位置不会变, 而且转场期间它可能已经不在栈里了
    // (返回提交之后被弹出的那一页还要继续渲染完动画).
    val depth = remember(contentKey) {
        backStack().indexOfFirst { it == contentKey }.coerceAtLeast(0)
    }
    DisposableEffect(contentKey, depth) {
        state.onEntryComposed(contentKey, depth)
        onDispose { state.onEntryDisposed(contentKey) }
    }
    // propagateMinConstraints: 原本 entry 内容是直接放在 AnimatedContent 的 Box 里的, 不传的话
    // 这一层会变成 wrap content, 整页布局会塌掉.
    Box(Modifier.navigationDim(state, contentKey, maxAlpha), propagateMinConstraints = true) {
        entry.Content()
    }
}

/**
 * 转场期间在内容之上盖一层黑色蒙版.
 *
 * 用 `drawWithContent` 而不是叠一个 `Box`: 省一层布局, 也不会拦触摸事件.
 */
@Composable
private fun Modifier.navigationDim(
    state: NavigationDimState,
    contentKey: Any,
    maxAlpha: Float,
): Modifier {
    val transition = LocalNavAnimatedContentScope.current.transition
    val isLowerPage = state.isLowerPage(contentKey)
    val dim by transition.animateFloat(
        transitionSpec = { IosNavigationMotion.DimSpec },
        label = "navigationDim",
    ) { enterExitState ->
        when {
            maxAlpha <= 0f -> 0f
            // 上层页面不压暗
            !isLowerPage -> 0f
            // 下层页面: 完全露出来时不压暗, 被盖住时压到最暗.
            // 前进是 Visible -> PostExit (0 -> max), 返回是 PreEnter -> Visible (max -> 0).
            enterExitState == EnterExitState.Visible -> 0f
            else -> maxAlpha
        }
    }
    return drawWithContent {
        drawContent()
        if (dim > 0f) {
            drawRect(Color.Black, alpha = dim)
        }
    }
}

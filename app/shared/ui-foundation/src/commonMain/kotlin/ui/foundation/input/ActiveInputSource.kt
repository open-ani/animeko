/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * 记录用户最近一次实际使用的指针设备. 输入方式是运行时属性而非平台属性:
 * 带触屏的 Windows 二合一和接了鼠标的 Android 平板会在触摸和鼠标之间来回切换.
 */
@Stable
class ActiveInputSourceState {
    /**
     * 供组合期挂载决策使用. 只在 [commit] 更新 —— 在按下事件里写快照状态会触发重组,
     * 打断同一修饰符链上正在识别的点击手势, 点击会直接丢失.
     */
    var current: PointerType by mutableStateOf(PointerType.Unknown)
        private set

    /**
     * 普通字段, 不参与快照系统. 点击回调必须读这个而不是 [current]:
     * 用鼠标进入播放页后的第一次触摸, [current] 还来不及提交, 而它在按下那一刻就已经是 Touch 了.
     */
    var latest: PointerType = PointerType.Unknown
        private set

    /**
     * 是否曾经观察到鼠标事件, 一旦为 true 不再变回. 回答「这台设备有没有鼠标」而不是「此刻在用什么」:
     * 只有真实鼠标会产生 [PointerType.Mouse] 事件, 观察到一次即可证明其存在.
     */
    var hasSeenMouse: Boolean by mutableStateOf(false)
        private set

    internal fun record(type: PointerType) {
        val gesturePointerType = type.asGesturePointerType()
        if (gesturePointerType == PointerType.Touch || gesturePointerType == PointerType.Mouse) {
            latest = gesturePointerType
        }
    }

    /** 只应在按下以外的事件上调用, 见 [current]. */
    internal fun commit() {
        if (latest == PointerType.Mouse && !hasSeenMouse) {
            hasSeenMouse = true
        }
        if (latest != current) {
            current = latest
        }
    }
}

val LocalActiveInputSource = compositionLocalOf { ActiveInputSourceState() }

/**
 * 将 Compose 指针类型归一化为应用使用的两套手势约定.
 *
 * Stylus 和 Eraser 与手指一样直接操作屏幕, 因此采用 Touch 手势. CMP Desktop 的 AWT
 * 输入路径会把未被原生触摸桥接管的笔事件报告为 Mouse, 这里不会改变该平台既有行为.
 */
fun PointerType.asGesturePointerType(): PointerType = when (this) {
    PointerType.Stylus, PointerType.Eraser -> PointerType.Touch
    else -> this
}

/** 观察指针类型, 不消费任何事件. */
fun Modifier.trackActiveInputSource(state: ActiveInputSourceState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // Enter/Exit 即使用户正在用手指也会带着 PointerType.Mouse 到达 (鼠标指针仍停在窗口里),
                // 据此判断会把刚切过去的触摸又翻回鼠标.
                val interactive = when (event.type) {
                    PointerEventType.Press,
                    PointerEventType.Move,
                    PointerEventType.Release,
                    PointerEventType.Scroll,
                        -> true

                    else -> false
                }
                if (!interactive) continue

                event.changes.firstOrNull()?.type?.let(state::record)
                // 按下阶段不提交: 写快照状态会触发重组, 打断正在识别的点击手势.
                if (event.type != PointerEventType.Press) {
                    state.commit()
                }
            }
        }
    }

/**
 * 只允许与 [requiredPointerType] 属于同一手势约定的指针发起拖动, 不影响其他指针类型的点击和滚轮事件.
 * Stylus/Eraser 与 Touch 属于同一约定.
 *
 * 必须按本次手势的 down 事件过滤, 不能用 [ActiveInputSourceState.current] 在组合期切换
 * `draggable.enabled`: 输入设备切换后的第一次拖动开始时, `current` 还是上一次的设备.
 */
fun Modifier.dragFromPointerType(requiredPointerType: PointerType): Modifier =
    pointerInput(requiredPointerType) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (down.type.asGesturePointerType() == requiredPointerType.asGesturePointerType()) {
                return@awaitEachGesture
            }

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** 只允许触摸发起拖动. */
fun Modifier.touchDragOnly(): Modifier = dragFromPointerType(PointerType.Touch)

/**
 * 只允许 [requiredPointerType] 发起指定方向的 nested scroll.
 *
 * 与 [dragFromPointerType] 不同, 这里只消费 nested scroll 的目标方向, 不消费原始指针位移.
 * 适合用于 Pager: 可以阻止鼠标拖动 Pager 翻页, 同时保留页面内滚动条、Slider 等子控件的鼠标拖动.
 */
fun Modifier.scrollFromPointerType(
    requiredPointerType: PointerType,
    orientation: Orientation,
): Modifier = composed {
    val activeInputSource = LocalActiveInputSource.current
    val connection = remember(activeInputSource, requiredPointerType, orientation) {
        PointerTypeNestedScrollConnection(
            activeInputSource = activeInputSource,
            requiredPointerType = requiredPointerType,
            orientation = orientation,
        )
    }
    trackActiveInputSource(activeInputSource).nestedScroll(connection)
}

private class PointerTypeNestedScrollConnection(
    private val activeInputSource: ActiveInputSourceState,
    private val requiredPointerType: PointerType,
    private val orientation: Orientation,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (
            source != NestedScrollSource.UserInput ||
            activeInputSource.latest == requiredPointerType
        ) {
            return Offset.Zero
        }
        return when (orientation) {
            Orientation.Horizontal -> Offset(available.x, 0f)
            Orientation.Vertical -> Offset(0f, available.y)
        }
    }
}

/** 只允许触摸发起横向 nested scroll, 但不影响子控件的鼠标拖动. */
fun Modifier.touchHorizontalScrollOnly(): Modifier =
    scrollFromPointerType(PointerType.Touch, Orientation.Horizontal)

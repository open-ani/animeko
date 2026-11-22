/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastFirstOrNull

/**
 * 判断鼠标的 [PointerEventType.Move] 是不是由用户移动鼠标引起的.
 *
 * `Move` 事件不保证位置有变化: 鼠标停在窗口里不动时, 每次布局变化 Compose 都会用同一个位置再派发一次 `Move`
 * 来更新悬浮状态. 把这样的事件当成用户操作, 界面自身的变化就会触发「鼠标动了」的逻辑.
 *
 * Compose 只为按下的指针记录上一次的位置, 悬浮中的鼠标的 [PointerInputChange.previousPosition] 总是等于当前位置,
 * 所以这里自己记录鼠标上一次所在的位置.
 *
 * 对同一个 `awaitPointerEventScope` 循环收到的每个事件依次调用 [isMouseMovement], 包括 Enter 和 Exit.
 */
internal class MouseMovementTracker {
    private var lastPosition = Offset.Unspecified

    /**
     * 记录 [event] 中鼠标的位置, 返回它是否是一次位置发生了变化的鼠标 `Move`.
     *
     * 其他类型指针的事件不影响记录, 返回 `false`.
     */
    fun isMouseMovement(event: PointerEvent): Boolean {
        val change = event.changes.fastFirstOrNull { it.type == PointerType.Mouse } ?: return false
        if (event.type == PointerEventType.Exit) {
            lastPosition = Offset.Unspecified
            return false
        }
        val previous = lastPosition
        lastPosition = change.position
        return event.type == PointerEventType.Move && previous.isSpecified && change.position != previous
    }
}

/**
 * 用户移动鼠标时调用 [onMouseMoved]. 鼠标位置没有变化的 `Move` 事件不算, 见 [MouseMovementTracker].
 *
 * 不消费任何事件.
 */
fun Modifier.onMouseMoved(onMouseMoved: () -> Unit): Modifier = composed {
    val currentOnMouseMoved by rememberUpdatedState(onMouseMoved)
    pointerInput(Unit) {
        awaitPointerEventScope {
            val tracker = MouseMovementTracker()
            while (true) {
                if (tracker.isMouseMovement(awaitPointerEvent())) {
                    currentOnMouseMoved()
                }
            }
        }
    }
}

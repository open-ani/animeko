/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** 单指纵向退出的位移与回弹. 图片位移使用视口坐标, 与图片自身的缩放、平移独立. */
internal class ImageViewerDismissState(private val scope: CoroutineScope) {
    var offset by mutableStateOf(Offset.Zero)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var animation: Job? = null
    private var dismissing = false

    val active: Boolean get() = dragging || offset != Offset.Zero

    fun cancel() {
        animation?.cancel()
    }

    fun start(): Boolean {
        if (dismissing) return false
        animation?.cancel()
        dragging = true
        return true
    }

    fun drag(delta: Offset) {
        offset += delta
    }

    fun release(height: Float, density: Float, velocityY: Float, cancelled: Boolean, onClose: () -> Unit) {
        if (!dragging) return
        dragging = false
        val distance = abs(offset.y)
        val shouldDismiss = !cancelled && (
                distance >= min(height * 0.22f, 160f * density) ||
                        (distance >= 32f * density && abs(velocityY) >= 1_000f * density && velocityY * offset.y > 0)
                )
        dismissing = shouldDismiss
        val start = offset
        val target = if (shouldDismiss) Offset(start.x, sign(start.y) * height) else Offset.Zero
        animation = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (shouldDismiss) tween(180) else spring(stiffness = 500f),
            ) { fraction, _ -> offset = start + (target - start) * fraction }
            offset = target
            if (shouldDismiss) onClose()
        }
    }
}

/** 只在适应窗口时接管触屏的纵向拖动. 横向拖动、鼠标输入和多指缩放交给图片查看器. */
@Composable
internal fun Modifier.imageViewerSwipeToDismiss(
    state: ImageViewerDismissState,
    canDismiss: () -> Boolean,
    onClose: () -> Unit,
): Modifier {
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    val currentOnClose by rememberUpdatedState(onClose)
    return pointerInput(state) {
        val density = density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch || !currentCanDismiss()) return@awaitEachGesture
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var lastSampleTime = down.uptimeMillis
            var releaseTime = down.uptimeMillis
            var previous = down.position
            var accepted = false
            var cancelled = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()
                    if (change == null || change.id != down.id || (!accepted && change.isConsumed)) {
                        cancelled = true
                        break
                    }
                    releaseTime = change.uptimeMillis
                    if (change.pressed) {
                        velocity.addPosition(change.uptimeMillis, change.position)
                        lastSampleTime = change.uptimeMillis
                    }
                    val distance = change.position - down.position
                    if (!accepted && distance.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(distance.y) <= abs(distance.x) || !currentCanDismiss() || !state.start()) break
                        accepted = true
                    }
                    if (accepted) {
                        state.drag(change.position - previous)
                        change.consume()
                    }
                    previous = change.position
                    if (!change.pressed) break
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                if (accepted) {
                    // 手指停留后松开按距离判断, 快速松开使用最后一段移动的速度.
                    val velocityY = if (releaseTime - lastSampleTime <= 100) velocity.calculateVelocity().y else 0f
                    state.release(size.height.toFloat(), density, velocityY, cancelled, currentOnClose)
                }
            }
        }
    }
}

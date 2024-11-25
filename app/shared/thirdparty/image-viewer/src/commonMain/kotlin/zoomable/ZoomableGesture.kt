package me.him188.ani.app.imageviewer.zoomable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * @program: ImageViewer
 *
 * @description:
 *
 * @author: JVZIYAOYAO
 *
 * @create: 2023-11-24 16:58
 **/

/**
 * 标记手势事件开始
 *
 * @param scope 用于进行变换的协程作用域
 */
internal fun ZoomableViewState.onGestureStart(scope: CoroutineScope) {
    if (allowGestureInput) {
        eventChangeCount = 0
        velocityTracker = VelocityTracker()
        scope.launch {
            offsetX.stop()
            offsetY.stop()
            offsetX.updateBounds(null, null)
            offsetY.updateBounds(null, null)
        }
    }
}

/**
 * 标记手势事件结束
 *
 * @param scope 用于进行变换的协程作用域
 * @param transformOnly 仅转换
 */
internal fun ZoomableViewState.onGestureEnd(scope: CoroutineScope, transformOnly: Boolean) {
    scope.apply {
        if (!transformOnly || !allowGestureInput || isRunning()) return
        var velocity = try {
            velocityTracker.calculateVelocity()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        // 如果缩放比小于1，要自动回到1
        // 如果缩放比大于最大显示缩放比，就设置回去，并且避免加速度
        val nextScale = when {
            scale.value < 1 -> 1F
            scale.value > maxScale -> {
                velocity = null
                maxScale
            }

            else -> null
        }
        launch {
            if (inBound(offsetX.value, boundX) && velocity != null) {
                val vx = sameDirection(lastPan.x, velocity.x)
                offsetX.updateBounds(boundX.first, boundX.second)
                offsetX.animateDecay(vx, decay)
            } else {
                val targetX = if (nextScale != maxScale) {
                    offsetX.value
                } else {
                    panTransformAndScale(
                        offset = offsetX.value,
                        center = centroid.x,
                        bh = containerWidth,
                        uh = displayWidth,
                        fromScale = scale.value,
                        toScale = nextScale,
                    )
                }
                offsetX.animateTo(limitToBound(targetX, boundX))
            }
        }
        launch {
            if (inBound(offsetY.value, boundY) && velocity != null) {
                val vy = sameDirection(lastPan.y, velocity.y)
                offsetY.updateBounds(boundY.first, boundY.second)
                offsetY.animateDecay(vy, decay)
            } else {
                val targetY = if (nextScale != maxScale) {
                    offsetY.value
                } else {
                    panTransformAndScale(
                        offset = offsetY.value,
                        center = centroid.y,
                        bh = containerHeight,
                        uh = displayHeight,
                        fromScale = scale.value,
                        toScale = nextScale,
                    )
                }
                offsetY.animateTo(limitToBound(targetY, boundY))
            }
        }
        launch {
            rotation.animateTo(0F)
        }
        nextScale?.let {
            launch {
                scale.animateTo(nextScale)
            }
        }
    }
}

/**
 * 输入手势事件
 *
 * @param scope 用于进行变换的协程作用域
 * @param center 手势中心坐标
 * @param pan 手势移动距离
 * @param zoom 手势缩放率
 * @param rotate 旋转角度
 * @param event 事件对象
 * @return 是否消费这次事件
 */
internal fun ZoomableViewState.onGesture(
    scope: CoroutineScope,
    center: Offset,
    pan: Offset,
    zoom: Float,
    rotate: Float,
    event: PointerEvent
): Boolean {
    if (!allowGestureInput) return true
    // 这里只记录最大手指数
    if (eventChangeCount <= event.changes.size) {
        eventChangeCount = event.changes.size
    } else {
        // 如果手指数从多个变成一个，就结束本次手势操作
        return false
    }

    var checkRotate = rotate
    var checkZoom = zoom
    // 如果是双指的情况下，手指距离小于一定值时，缩放和旋转的值会很离谱，所以在这种极端情况下就不要处理缩放和旋转了
    if (event.changes.size == 2) {
        val fingerDistanceOffset =
            event.changes[0].position - event.changes[1].position
        if (
            fingerDistanceOffset.x.absoluteValue < MIN_GESTURE_FINGER_DISTANCE
            && fingerDistanceOffset.y.absoluteValue < MIN_GESTURE_FINGER_DISTANCE
        ) {
            checkRotate = 0F
            checkZoom = 1F
        }
    }

    gestureCenter.value = center

    val currentOffsetX = offsetX.value
    val currentOffsetY = offsetY.value
    val currentScale = scale.value
    val currentRotation = rotation.value

    var nextScale = currentScale.times(checkZoom)
    // 检查最小放大倍率
    if (nextScale < MIN_SCALE) nextScale = MIN_SCALE

    // 最后一次的偏移量
    lastPan = pan
    // 记录手势的中点
    centroid = center
    // 计算边界，如果目标缩放值超过最大显示缩放值，边界就要用最大缩放值来计算，否则手势结束时会导致无法归位
    boundScale =
        if (nextScale > maxScale) maxScale else nextScale
    boundX =
        getBound(
            boundScale,
            containerWidth,
            displayWidth,
        )
    boundY =
        getBound(
            boundScale,
            containerHeight,
            displayHeight,
        )

    var nextOffsetX = panTransformAndScale(
        offset = currentOffsetX,
        center = center.x,
        bh = containerWidth,
        uh = displayWidth,
        fromScale = currentScale,
        toScale = nextScale,
    ) + pan.x
    var nextOffsetY = panTransformAndScale(
        offset = currentOffsetY,
        center = center.y,
        bh = containerHeight,
        uh = displayHeight,
        fromScale = currentScale,
        toScale = nextScale,
    ) + pan.y

    // 如果手指数1，就是拖拽，拖拽受范围限制
    // 如果手指数大于1，即有缩放事件，则支持中心点放大
    if (eventChangeCount == 1) {
        nextOffsetX = limitToBound(nextOffsetX, boundX)
        nextOffsetY = limitToBound(nextOffsetY, boundY)
    }

    val nextRotation = if (nextScale < 1) {
        currentRotation + checkRotate
    } else currentRotation

    // 添加到手势加速度
    velocityTracker.addPosition(
        event.changes[0].uptimeMillis,
        Offset(nextOffsetX, nextOffsetY),
    )

    if (!isRunning()) scope.launch {
        scale.snapTo(nextScale)
        offsetX.snapTo(nextOffsetX)
        offsetY.snapTo(nextOffsetY)
        rotation.snapTo(nextRotation)
    }

    // 这里判断是否已运动到边界，如果到了边界，就不消费事件，让上层界面获取到事件
    val canConsumeX = reachSide(pan.x, nextOffsetX, boundX)
    val canConsumeY = reachSide(pan.y, nextOffsetY, boundY)
    // 判断主要活动方向
    val canConsume = if (pan.x.absoluteValue > pan.y.absoluteValue) {
        canConsumeX
    } else {
        canConsumeY
    }
    if (canConsume || scale.value < 1) {
        event.changes.fastForEach {
            if (it.positionChanged()) {
                it.consume()
            }
        }
    }
    // 返回true，继续下一次手势
    return true
}

/**
 * 追踪缩放过程中的中心点
 */
private fun panTransformAndScale(
    offset: Float,
    center: Float,
    bh: Float,
    uh: Float,
    fromScale: Float,
    toScale: Float,
): Float {
    val srcH = uh * fromScale
    val desH = uh * toScale
    val gapH = (bh - uh) / 2

    val py = when {
        uh >= bh -> {
            val upy = (uh * fromScale - uh).div(2)
            (upy - offset + center) / (fromScale * uh)
        }

        srcH > bh || bh > uh -> {
            val upy = (srcH - uh).div(2)
            (upy - gapH - offset + center) / (fromScale * uh)
        }

        else -> {
            val upy = -(bh - srcH).div(2)
            (upy - offset + center) / (fromScale * uh)
        }
    }
    return when {
        uh >= bh -> {
            val upy = (uh * toScale - uh).div(2)
            upy + center - py * toScale * uh
        }

        desH > bh -> {
            val upy = (desH - uh).div(2)
            upy - gapH + center - py * toScale * uh
        }

        else -> {
            val upy = -(bh - desH).div(2)
            upy + center - py * desH
        }
    }
}
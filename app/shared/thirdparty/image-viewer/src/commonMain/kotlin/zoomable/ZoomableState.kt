package me.him188.ani.app.imageviewer.zoomable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * @program: ImageViewer
 *
 * @description:
 *
 * @author: JVZIYAOYAO
 *
 * @create: 2023-11-24 15:45

 **/

// 默认X轴偏移量
private const val DEFAULT_OFFSET_X = 0F

// 默认Y轴偏移量
private const val DEFAULT_OFFSET_Y = 0F

// 默认缩放率
private const val DEFAULT_SCALE = 1F

// 默认旋转角度
private const val DEFAULT_ROTATION = 0F

// 图片最小缩放率
internal const val MIN_SCALE = 0.5F

// 图片最大缩放率
//const val MAX_SCALE_RATE = 8F
//const val MAX_SCALE_RATE = 2F
private const val MAX_SCALE_RATE = 4F

// 最小手指手势间距
internal const val MIN_GESTURE_FINGER_DISTANCE = 200

/**
 * viewer状态对象，用于记录compose组件状态
 *
 * @property maxScale 最大缩放率
 * @constructor
 *
 * @param offsetX X轴偏移量
 * @param offsetY Y轴偏移量
 * @param scale 缩放率
 * @param rotation 旋转角度
 * @param animationSpec 动画窗格
 */
open class ZoomableViewState(
    val maxScale: Float = MAX_SCALE_RATE,
    offsetX: Float = DEFAULT_OFFSET_X,
    offsetY: Float = DEFAULT_OFFSET_Y,
    scale: Float = DEFAULT_SCALE,
    rotation: Float = DEFAULT_ROTATION,
    animationSpec: AnimationSpec<Float>? = null,
) : CoroutineScope by MainScope() {

    // 默认动画窗格
    private var defaultAnimateSpec: AnimationSpec<Float> = animationSpec ?: SpringSpec()

    // x偏移
    val offsetX = Animatable(offsetX)

    // y偏移
    val offsetY = Animatable(offsetY)

    // 放大倍率
    val scale = Animatable(scale)

    // 旋转
    val rotation = Animatable(rotation)

    // 是否允许手势输入
    var allowGestureInput = true

    private val contentSizeState = mutableStateOf<Size?>(null)

    val isSpecified: Boolean
        get() {
            return contentSizeState.value?.isSpecified == true
        }

    var contentSize: Size
        set(value) {
            contentSizeState.value = value
        }
        get() {
            return if (contentSizeState.value?.isSpecified == true) {
                contentSizeState.value!!
            } else {
                Size.Zero
            }
        }

    // 容器大小
    var containerSize = mutableStateOf(Size.Zero)

    val containerWidth: Float
        get() = containerSize.value.width

    val containerHeight: Float
        get() = containerSize.value.height

    private val containerRatio: Float
        get() = containerSize.value.run {
            width.div(height)
        }

    private val contentRatio: Float
        get() = contentSize.run {
            width.div(height)
        }

    // 宽度是否对齐视口
    private val widthFixed: Boolean
        get() = contentRatio > containerRatio

    // 1倍缩放率
    internal val scale1x: Float
        get() {
            return if (widthFixed) {
                containerSize.value.width.div(contentSize.width)
            } else {
                containerSize.value.height.div(contentSize.height)
            }
        }

    val displaySize: Size
        get() {
            return Size(displayWidth, displayHeight)
        }

    val displayWidth: Float
        get() {
            return contentSize.width.times(scale1x)
        }

    val displayHeight: Float
        get() {
            return contentSize.height.times(scale1x)
        }

    val realSize: Size
        get() {
            return Size(
                width = displayWidth.times(scale.value),
                height = displayHeight.times(scale.value),
            )
        }

    // 手势的中心点
    val gestureCenter = mutableStateOf(Offset.Zero)

    // 手势加速度
    var velocityTracker = VelocityTracker()

    // 最后一次偏移运动
    var lastPan = Offset.Zero

    // 减速运动动画曲线
    val decay = FloatExponentialDecaySpec(2f).generateDecayAnimationSpec<Float>()

    // 手势实时的偏移范围
    var boundX = Pair(0F, 0F)
    var boundY = Pair(0F, 0F)

    // 计算边界使用的缩放率
    var boundScale = 1F

    // 记录触摸事件中手指的个数
    var eventChangeCount = 0

    // 触摸时中心位置
    var centroid = Offset.Zero

//    // 挂载状态
//    val mountedFlow = MutableStateFlow(false)
//
//    // 等待挂载
//    suspend fun awaitMounted() {
//        mountedFlow.apply {
//            withContext(Dispatchers.Default) {
//                takeWhile { !it }.collect()
//            }
//        }
//    }
//
//    // 标记挂载
//    suspend fun onMounted() {
//        mountedFlow.emit(true)
//    }

    // 标识是否来自saver，旋转屏幕后会变成true
//    internal var fromSaver = false

    // 恢复的时间戳
//    private var resetTimeStamp by mutableStateOf(0L)

    /**
     * 判断是否有动画正在运行
     * @return Boolean
     */
    fun isRunning(): Boolean {
        return scale.isRunning
                || offsetX.isRunning
                || offsetY.isRunning
                || rotation.isRunning
    }

    internal fun updateContainerSize(size: Size) {
        containerSize.value = size
    }

    /**
     * 立即设置回初始值
     */
    suspend fun resetImmediately() {
        rotation.snapTo(DEFAULT_ROTATION)
        offsetX.snapTo(DEFAULT_OFFSET_X)
        offsetY.snapTo(DEFAULT_OFFSET_Y)
        scale.snapTo(DEFAULT_SCALE)
    }

    /**
     * 修正offsetX,offsetY的位置
     */
    suspend fun fixToBound() {
        boundX = getBound(
            scale.value,
            containerWidth,
            displayWidth,
        )
        boundY = getBound(
            scale.value,
            containerHeight,
            displayHeight,
        )
        val limitX = limitToBound(offsetX.value, boundX)
        val limitY = limitToBound(offsetY.value, boundY)
        coroutineScope {
            launch {
                offsetX.animateTo(limitX)
            }
            launch {
                offsetY.animateTo(limitY)
            }
        }
    }

    /**
     * 设置回初始值
     */
    suspend fun reset(animationSpec: AnimationSpec<Float> = defaultAnimateSpec) {
        coroutineScope {
            listOf(
                async {
                    rotation.animateTo(DEFAULT_ROTATION, animationSpec)
                },
                async {
                    offsetX.animateTo(DEFAULT_OFFSET_X, animationSpec)
                },
                async {
                    offsetY.animateTo(DEFAULT_OFFSET_Y, animationSpec)
                },
                async {
                    scale.animateTo(DEFAULT_SCALE, animationSpec)
                },
            ).awaitAll()
        }
    }

    /**
     * 缩放
     */
    suspend fun scaleTo(
        offset: Offset,
        scaleT: Float,
        animationSpec: AnimationSpec<Float>? = null
    ) {
        val scaleCoerced = scaleT.coerceIn(MIN_SCALE, maxScale)
        val currentAnimateSpec = animationSpec ?: defaultAnimateSpec

        var nextOffsetX = (containerWidth / 2 - offset.x) * scaleCoerced
        var nextOffsetY = (containerHeight / 2 - offset.y) * scaleCoerced

        val boundX = getBound(scaleCoerced, containerWidth, displayWidth)
        val boundY = getBound(scaleCoerced, containerHeight, displayHeight)

        nextOffsetX = limitToBound(nextOffsetX, boundX)
        nextOffsetY = limitToBound(nextOffsetY, boundY)

        // 启动
        coroutineScope {
            listOf(
                async {
                    offsetX.updateBounds(null, null)
                    offsetX.animateTo(nextOffsetX, currentAnimateSpec)
                    offsetX.updateBounds(boundX.first, boundX.second)
                },
                async {
                    offsetY.updateBounds(null, null)
                    offsetY.animateTo(nextOffsetY, currentAnimateSpec)
                    offsetY.updateBounds(boundY.first, boundY.second)
                },
                async {
                    scale.animateTo(scaleCoerced, currentAnimateSpec)
                },
            ).awaitAll()
        }
    }

    /**
     * 放大或缩小
     */
    suspend fun toggleScale(
        offset: Offset,
        animationSpec: AnimationSpec<Float> = defaultAnimateSpec
    ) {
        // 如果不等于1，就调回1
        if (scale.value != 1F) {
            reset(animationSpec)
        } else {
            scaleTo(offset, maxScale, animationSpec)
        }
    }

    companion object {
        val SAVER: Saver<ZoomableViewState, *> = listSaver(
            save = {
                listOf(it.offsetX.value, it.offsetY.value, it.scale.value, it.rotation.value)
            },
            restore = {
                ZoomableViewState(
                    offsetX = it[0],
                    offsetY = it[1],
                    scale = it[2],
                    rotation = it[3],
                )
            },
        )
    }

}

/**
 * 返回一个ZoomableState
 *
 * @param contentSize 内容尺寸大小，必填，不为空且大小确切的时候才能正常显示
 * @param maxScale 最大缩放比例
 * @param animationSpec 动画窗格
 * @return
 */
@Composable
fun rememberSavableZoomableState(
    contentSize: Size? = null,
    maxScale: Float = MAX_SCALE_RATE,
    key: String? = null,
    animationSpec: AnimationSpec<Float>? = null,
): ZoomableViewState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = ZoomableViewState.SAVER, key = key) {
        ZoomableViewState(
            maxScale = maxScale,
            animationSpec = animationSpec,
        )
    }.apply {
        contentSize?.let {
            this.contentSize = it
            // 旋转后如果超出边界了要修复回去
            scope.launch { fixToBound() }
        }
    }
}

@Composable
fun rememberZoomableState(
    maxScale: Float = MAX_SCALE_RATE,
    animationSpec: AnimationSpec<Float>? = null,
    key: Any? = null
): ZoomableViewState {
    return remember(key1 = key) {
        ZoomableViewState(
            maxScale = maxScale,
            animationSpec = animationSpec,
        )
    }
}
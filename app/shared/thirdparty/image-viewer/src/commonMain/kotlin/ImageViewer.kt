package me.him188.ani.app.imageviewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import me.him188.ani.app.imageviewer.zoomable.ZoomableGestureScope
import me.him188.ani.app.imageviewer.zoomable.ZoomableView
import me.him188.ani.app.imageviewer.zoomable.ZoomableViewState
import kotlin.reflect.KClass

/**
 * 单个图片预览组件
 *
 * @param modifier 图层修饰
 * @param model 需要显示的图像，默认支持Painter、ImageBitmap、ImageVector、AnyComposable,如果需要支持其他类型的数据可以自定义processor
 * @param state 组件状态和控制类
 * @param processor 将model数据渲染到界面上
 * @param detectGesture 检测组件的手势交互
 */
@Composable
fun ImageViewer(
    modifier: Modifier = Modifier,
    model: Any?,
    state: ZoomableViewState,
    processor: ModelProcessor = ModelProcessor(),
    detectGesture: ZoomableGestureScope = ZoomableGestureScope(),
) {
    ZoomableView(
        modifier = modifier,
        state = state,
        detectGesture = detectGesture,
    ) {
        model?.let {
            processor.Deploy(model = it, state = state)
        }
    }
}

/**
 * 用于解析图像数据给ZoomableView显示的方法
 */
internal typealias ImageContent = @Composable (Any, ZoomableViewState) -> Unit

class ModelProcessor(
    vararg additionalProcessor: ModelProcessorPair,
) {

    private val typeMapper = mutableStateMapOf<KClass<out Any>, ImageContent>()

    init {
        listOf(*basicModelProcessorList.toTypedArray(), *additionalProcessor).forEach { pair ->
            typeMapper[pair.first] = pair.second
        }
    }

    @Composable
    fun Deploy(model: Any, state: ZoomableViewState) {
        val entry = typeMapper.entries.firstOrNull { isSubclassOf(model, it.key) } ?: return
        entry.value.invoke(model, state)
    }
}

internal typealias ModelProcessorPair = Pair<KClass<out Any>, ImageContent>

private val basicModelProcessorList: List<ModelProcessorPair> = listOf(
    Painter::class to { model, _ ->
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = model as Painter,
            contentDescription = null,
        )
    },
    ImageBitmap::class to { model, _ ->
        Image(
            modifier = Modifier.fillMaxSize(),
            bitmap = model as ImageBitmap,
            contentDescription = null,
        )
    },
    ImageVector::class to { model, _ ->
        Image(
            modifier = Modifier.fillMaxSize(),
            imageVector = model as ImageVector,
            contentDescription = null,
        )
    },
    ImageViewer.AnyComposable::class to { model, _ ->
        (model as ImageViewer.AnyComposable).composable.invoke()
    },
)

/**
 * 判断对象是否为某个类的子类
 *
 * @param T
 * @param instance 当前实例
 * @param kClass 需要匹配的类对象
 * @return
 */
private fun <T : Any> isSubclassOf(instance: T, kClass: KClass<out Any>): Boolean {
    return kClass.isInstance(instance)
}

class ImageViewer {

    /**
     * ImageViewer传人的Model参数除了特定图片以外，还支持传人一个Composable函数
     *
     * @property composable
     */
    class AnyComposable(val composable: @Composable () -> Unit)
}
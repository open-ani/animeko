/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.zoom.GestureType
import com.github.panpf.zoomimage.zoom.MouseWheelScaleCalculator
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.window.MacTrackpadGestures
import me.him188.ani.app.platform.window.rememberLayoutHitTestOwner
import me.him188.ani.app.ui.foundation.imageviewer.FileKitImageFileSaver
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerContent
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerExportedFile
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalSecondaryWindowFrame
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.image_viewer_save
import me.him188.ani.app.ui.lang.image_viewer_window_title
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.isMacOS
import me.him188.ani.utils.platform.isWindows
import org.jetbrains.compose.resources.stringResource
import java.awt.MouseInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

private val logger = logger("ImageViewer")

/**
 * 桌面端: 在真实窗口内时打开独立窗口; 没有宿主窗口 (预览, UI 测试) 时回退到页面内覆盖层.
 */
@Composable
actual fun ImageViewer(handler: ImageViewerHandler, onClose: () -> Unit) {
    val hostWindow = hostAwtWindow()
    if (hostWindow == null) {
        ImageViewerOverlay(handler, onClose)
        return
    }
    if (!handler.viewing.value) return
    ImageViewerWindow(handler, hostWindow, onClose)
}

@Composable
actual fun ImageViewerBackHandler(handler: ImageViewerHandler) {
    if (hostAwtWindow() == null) {
        ImageViewerOverlayBackHandler(handler)
    }
}

/**
 * 当前页面所在的 AWT 窗口. 预览和 UI 测试没有真实窗口, 返回 `null`.
 */
@Composable
private fun hostAwtWindow(): java.awt.Window? {
    return (LocalPlatformWindow.current.windowScope as? FrameWindowScope)?.window
}

/**
 * 独立的查看器窗口. 沿用主窗口的自定义外观 ([LocalSecondaryWindowFrame]), Esc 关闭.
 */
@Composable
private fun ImageViewerWindow(
    handler: ImageViewerHandler,
    hostWindow: java.awt.Window,
    onClose: () -> Unit,
) {
    val model by handler.imageModel.collectAsStateWithLifecycle()
    val onCloseState = rememberUpdatedState(onClose)
    val windowState = rememberWindowState(
        size = DpSize(1000.dp, 720.dp),
        position = WindowPosition.Aligned(Alignment.Center),
    )
    // 沿用主窗口的图标 (Windows/Linux 任务栏).
    val icon = remember(hostWindow) {
        (hostWindow.iconImages.firstOrNull() as? BufferedImage)?.toPainter()
    }
    Window(
        onCloseRequest = { onCloseState.value() },
        state = windowState,
        title = stringResource(Lang.image_viewer_window_title),
        icon = icon,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onCloseState.value()
                true
            } else {
                false
            }
        },
    ) {
        val platform = LocalPlatform.current
        // Windows 自绘标题栏需要命中测试; 与主窗口 (AniDesktop) 一致.
        val layoutHitTestOwner = if (platform.isWindows()) rememberLayoutHitTestOwner() else null
        val platformWindow = remember(window.windowHandle, this, platform, windowState, layoutHitTestOwner) {
            PlatformWindow(
                windowHandle = window.windowHandle,
                windowScope = this,
                windowState = windowState,
                platform = platform,
                layoutHitTestOwner = layoutHitTestOwner,
            )
        }
        val window = this.window
        val saveDialogTitle = stringResource(Lang.image_viewer_save)
        val content: @Composable () -> Unit = {
            ImageViewerContent(
                model = model,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
                // 独立窗口有自己的关闭按钮, 单击图片不再关闭.
                closeOnTap = false,
                showCloseButton = false,
                fileSaver = remember(window, saveDialogTitle) {
                    // 保存对话框作为查看器窗口的 sheet 弹出
                    FileKitImageFileSaver(FileKitDialogSettings(title = saveDialogTitle, parentWindow = window))
                },
                platformImageModifier = { exported, zoomable ->
                    MacTrackpadPinchZoom(zoomable)
                    Modifier
                        .imageDragOut(exported, zoomable)
                        .imageScrollPan(zoomable)
                },
            )
        }
        CompositionLocalProvider(LocalPlatformWindow provides platformWindow) {
            val frame = LocalSecondaryWindowFrame.current
            if (frame != null) {
                frame(windowState, onClose, content)
            } else {
                content()
            }
        }
    }
}

private const val SCALE_EPSILON = 0.001f

/** 拖出时鼠标旁显示的缩略图最长边 (px). */
private const val DRAG_DECORATION_MAX_SIZE = 240

/**
 * 把图片本身拖到其他应用 (访达, 聊天软件等) 即可传过去图片文件.
 *
 * 图片适应窗口时没有可平移的内容, 拖动就是拖出; 放大后拖动用于平移 (与 macOS 照片 App 一致).
 */
@Composable
private fun Modifier.imageDragOut(exported: ImageViewerExportedFile?, zoomable: ZoomableState): Modifier {
    val atFitScale = zoomable.isAtFitScale()
    LaunchedEffect(zoomable, atFitScale) {
        // 适应窗口时关掉 zoomimage 的单指拖动, 避免它和拖出抢手势.
        zoomable.setDisabledGestureTypes(if (atFitScale) GestureType.ONE_FINGER_DRAG else 0)
    }
    return this.then(ImageDragOutElement(exported, zoomable))
}

private fun ZoomableState.isAtFitScale(): Boolean = transform.scaleX <= minScale + SCALE_EPSILON

private data class ImageDragOutElement(
    val exported: ImageViewerExportedFile?,
    val zoomable: ZoomableState,
) : ModifierNodeElement<ImageDragOutNode>() {
    override fun create() = ImageDragOutNode(exported, zoomable)

    override fun update(node: ImageDragOutNode) {
        node.update(exported, zoomable)
    }
}

/**
 * zoomimage 的手势层会消费掉拖动事件 (即使已禁用单指拖动), 所以在 [PointerEventPass.Initial]
 * 阶段抢先识别拖动: 适应窗口且本地副本已就绪时, 拖动超过阈值就发起系统拖放.
 */
private class ImageDragOutNode(
    private var exported: ImageViewerExportedFile?,
    private var zoomable: ZoomableState,
) : DelegatingNode() {
    private var decoration: ImageBitmap? = null

    private val source = delegate(
        DragAndDropSourceModifierNode { _ ->
            val file = exported ?: return@DragAndDropSourceModifierNode
            val awtFile = File(file.path.absolutePath)
            logger.info { "Dragging image out: ${awtFile.name}" }
            val thumbnail = decoration ?: runCatching { decodeImageBitmap(file.path.readBytes()) }.getOrNull()
                ?.also { decoration = it }
            val decorationSize = thumbnail?.let {
                val scale = DRAG_DECORATION_MAX_SIZE.toFloat() / maxOf(it.width, it.height, 1)
                Size(it.width * scale, it.height * scale)
            } ?: Size.Zero
            startDragAndDropTransfer(
                transferData = DragAndDropTransferData(
                    transferable = DragAndDropTransferable(FileListTransferable(listOf(awtFile))),
                    supportedActions = listOf(DragAndDropTransferAction.Copy),
                ),
                decorationSize = decorationSize,
                drawDragDecoration = {
                    if (thumbnail != null) {
                        drawImage(
                            thumbnail,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            alpha = 0.9f,
                        )
                    }
                },
            )
        },
    )

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (exported == null || !zoomable.isAtFitScale()) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > slop) {
                            change.consume()
                            source.requestDragAndDropTransfer(change.position)
                            break
                        }
                    }
                }
            },
        )
    }

    fun update(exported: ImageViewerExportedFile?, zoomable: ZoomableState) {
        if (this.exported !== exported) decoration = null
        this.exported = exported
        this.zoomable = zoomable
    }
}

/** 滚轮/触摸板每个滚动单位平移的距离, 与 Compose 桌面端滚动容器一致. */
private val SCROLL_PAN_STEP = 64.dp

/**
 * 滚轮和触摸板双指滚动: 不按修饰键时平移图片, 按住 Ctrl/Cmd 时缩放 (与 Preview / Figma 一致).
 *
 * zoomimage 默认把所有滚动都当缩放且不看修饰键. 这里在 Initial 阶段先记录本次滚动有没有按修饰键:
 * 有则交给 zoomimage 自己缩放 (它的算法通过 [ModifierAwareWheelScaleCalculator] 只在此时生效),
 * 没有则由这里平移并消费事件.
 */
@Composable
internal fun Modifier.imageScrollPan(zoomable: ZoomableState): Modifier {
    val calculator = remember { ModifierAwareWheelScaleCalculator() }
    LaunchedEffect(zoomable, calculator) {
        zoomable.setMouseWheelScaleCalculator(calculator)
    }
    return this.then(ImageScrollPanElement(zoomable, calculator))
}

/** 只在最近一次滚动按着 Ctrl/Cmd 时才缩放, 否则返回原比例 (不缩放). */
private class ModifierAwareWheelScaleCalculator : MouseWheelScaleCalculator {
    @Volatile
    var zoomModifierPressed: Boolean = false

    override fun calculateScale(currentScale: Float, scrollDelta: Float): Float {
        return if (zoomModifierPressed) {
            MouseWheelScaleCalculator.Default.calculateScale(currentScale, scrollDelta)
        } else {
            currentScale
        }
    }
}

private data class ImageScrollPanElement(
    val zoomable: ZoomableState,
    val calculator: ModifierAwareWheelScaleCalculator,
) : ModifierNodeElement<ImageScrollPanNode>() {
    override fun create() = ImageScrollPanNode(zoomable, calculator)

    override fun update(node: ImageScrollPanNode) {
        node.zoomable = zoomable
        node.calculator = calculator
    }
}

private class ImageScrollPanNode(
    var zoomable: ZoomableState,
    var calculator: ModifierAwareWheelScaleCalculator,
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                val step = SCROLL_PAN_STEP.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val modifiers = event.keyboardModifiers
                        val zoomModifierPressed = modifiers.isCtrlPressed || modifiers.isMetaPressed
                        calculator.zoomModifierPressed = zoomModifierPressed
                        if (zoomModifierPressed) continue // zoomimage 在 Main 阶段自己缩放
                        val delta = change.scrollDelta
                        if (delta == Offset.Zero) continue
                        change.consume()
                        val zoomable = zoomable
                        coroutineScope.launch {
                            zoomable.offsetBy(Offset(-delta.x * step, -delta.y * step), animated = false)
                        }
                    }
                }
            },
        )
    }
}

/**
 * macOS 触摸板捏合缩放, 以鼠标指针位置为中心. Compose Desktop 不产生捏合事件, 所以监听 AWT 手势.
 */
@Composable
private fun FrameWindowScope.MacTrackpadPinchZoom(zoomable: ZoomableState) {
    if (!LocalPlatform.current.isMacOS()) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val window = window
    DisposableEffect(window, zoomable, density) {
        val handle = MacTrackpadGestures.addMagnificationListener(window.rootPane) { magnification ->
            val content = window.contentPane
            val mouse = MouseInfo.getPointerInfo()?.location
            if (!content.isShowing || mouse == null) return@addMagnificationListener
            val origin = content.locationOnScreen
            val touchPoint = Offset((mouse.x - origin.x) * density, (mouse.y - origin.y) * density)
            val centroid = zoomable.touchPointToContentPoint(touchPoint)
            scope.launch {
                zoomable.scaleBy(1f + magnification.toFloat(), centroidContentPoint = centroid, animated = false)
            }
        }
        onDispose { handle?.close() }
    }
}

/** 只提供文件列表 flavor 的 [Transferable], 目标应用收到的是文件本身. */
private class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
    }
}

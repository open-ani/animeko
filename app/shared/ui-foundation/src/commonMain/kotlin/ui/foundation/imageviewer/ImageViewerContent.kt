/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.rememberSketchZoomState
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.files
import me.him188.ani.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.image_viewer_close
import me.him188.ani.app.ui.lang.image_viewer_copied
import me.him188.ani.app.ui.lang.image_viewer_copy
import me.him188.ani.app.ui.lang.image_viewer_copy_failed
import me.him188.ani.app.ui.lang.image_viewer_load_failed
import me.him188.ani.app.ui.lang.image_viewer_reset_zoom
import me.him188.ani.app.ui.lang.image_viewer_save
import me.him188.ani.app.ui.lang.image_viewer_save_failed
import me.him188.ani.app.ui.lang.image_viewer_saved
import me.him188.ani.app.ui.lang.image_viewer_zoom_in
import me.him188.ani.app.ui.lang.image_viewer_zoom_out
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 查看器内各控件的 test tag. */
object ImageViewerTestTags {
    const val ZOOM_IN = "ImageViewer.ZoomIn"
    const val ZOOM_OUT = "ImageViewer.ZoomOut"
    const val RESET_ZOOM = "ImageViewer.ResetZoom"
    const val SCALE_TEXT = "ImageViewer.ScaleText"
    const val COPY = "ImageViewer.Copy"
    const val SAVE = "ImageViewer.Save"
    const val CLOSE = "ImageViewer.Close"
}

/** 每次点击放大/缩小按钮的倍率. */
private const val ZOOM_STEP = 1.5f

private val logger = logger<ImageViewerTestTags>()

/**
 * 图片查看器的内容: 可缩放图片 + 底部工具栏 (缩小 / 缩放比例 / 放大 / 适应窗口 / 复制 / 保存 / 关闭).
 *
 * 快捷键: Ctrl/Cmd+C 复制图片.
 *
 * 缩放由 zoomimage 提供: 触摸双指缩放, 双击切换, 鼠标滚轮缩放, 键盘 `+`/`-` 缩放.
 * 图片加载成功后会在后台导出一份带扩展名的本地副本 ([ImageViewerExportedFile]), 供保存和拖拽使用.
 *
 * @param model 图片 URL. 为 `null` 时只显示黑底.
 * @param closeOnTap 单击图片是否关闭 (覆盖层模式为 `true`; 独立窗口为 `false`).
 * @param showCloseButton 工具栏是否显示关闭按钮.
 * @param fileSaver 点击保存时的保存方式, 默认弹系统对话框.
 * @param imageClipboard 复制图片的方式, `null` 表示不支持复制 (隐藏按钮).
 * @param platformImageModifier 平台相关的图片层扩展: 返回附加在图片上的 [Modifier] (例如桌面端把图片拖到其他应用),
 * 也可以在其中注册额外手势 (例如触摸板捏合缩放). 参数为当前已导出的本地副本 (未就绪时为 `null`) 和缩放状态.
 * @param exportDirectory 本地副本所在目录, 默认为 [imageViewerExportDirectory].
 * @param contentScale 初始 (最小) 缩放方式. 覆盖层用 [ContentScale.Fit] 填满屏幕; 独立窗口用 [ContentScale.Inside] 不放大小图.
 * @param decodeSize 解码尺寸上限 (px, 只按 2 的幂采样). 默认按显示区域解码; 独立窗口传屏幕的 2 倍, 让屏幕能放下的图片按原尺寸解码,
 * 这样 [ContentScale.Inside] 就能 1:1 显示.
 * @param onImageSizeAvailable 图片加载成功后回调解码后的像素尺寸 (独立窗口用来按图片大小调整窗口).
 */
@Composable
fun ImageViewerContent(
    model: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeOnTap: Boolean = true,
    showCloseButton: Boolean = true,
    fileSaver: ImageFileSaver = rememberFileKitImageFileSaver(),
    imageClipboard: ImageClipboard? = rememberImageClipboard(),
    platformImageModifier: @Composable (exported: ImageViewerExportedFile?, zoomable: ZoomableState) -> Modifier =
        { _, _ -> Modifier },
    exportDirectory: SystemPath = imageViewerExportDirectory(LocalContext.current),
    contentScale: ContentScale = ContentScale.Fit,
    decodeSize: IntSize? = null,
    onImageSizeAvailable: (IntSize) -> Unit = {},
) {
    val sketch = LocalSketch.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val zoomState = rememberSketchZoomState()
    val imageState = rememberAsyncImageState()
    val loadState = imageState.loadState
    val loaded = loadState is LoadState.Success

    val onImageSizeAvailableState = rememberUpdatedState(onImageSizeAvailable)
    LaunchedEffect(loadState) {
        val image = (loadState as? LoadState.Success)?.result?.image ?: return@LaunchedEffect
        onImageSizeAvailableState.value(IntSize(image.width, image.height))
    }

    // 图片加载成功后再导出, 此时 Sketch 的下载缓存已命中, 不会再下载一次.
    var exported by remember(model) { mutableStateOf<ImageViewerExportedFile?>(null) }
    LaunchedEffect(model, loaded) {
        if (model == null || !loaded) return@LaunchedEffect
        exported = try {
            sketch.exportImageForViewer(model, exportDirectory)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to export image for viewer: $model" }
            null
        }
    }

    val savedText = stringResource(Lang.image_viewer_saved)
    val saveFailedText = stringResource(Lang.image_viewer_save_failed)
    val copiedText = stringResource(Lang.image_viewer_copied)
    val copyFailedText = stringResource(Lang.image_viewer_copy_failed)

    val onCopy: (() -> Unit)? = if (imageClipboard == null) null else {
        {
            val file = exported
            if (file != null) {
                scope.launch {
                    try {
                        imageClipboard.copy(file)
                        toaster.toast(copiedText)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to copy image ${file.fileName}" }
                        toaster.toast(copyFailedText)
                    }
                }
            }
        }
    }

    // 图片加载后让图片获得焦点: 键盘缩放 (zoomimage) 和 Ctrl/Cmd+C 都依赖焦点
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(model, loaded) {
        if (loaded) runCatching { focusRequester.requestFocus() }
    }

    val imageModifier = platformImageModifier(exported, zoomState.zoomable)
    // zoomimage 的 onTap 用 detectTapGestures 实现: 适应窗口时单指拖动没人消费, 抬起也会当成 tap 关掉查看器.
    // 在 Initial 阶段记录这一次手势是否超过 touchSlop, 拖动过就不当 tap.
    val tapGuard = remember { ImageViewerTapGuard() }
    Box(
        modifier
            .background(Color.Black)
            .onKeyEvent { event ->
                val isCopy = event.type == KeyEventType.KeyDown && event.key == Key.C &&
                        (event.isCtrlPressed || event.isMetaPressed)
                if (isCopy && onCopy != null) {
                    onCopy()
                    true
                } else {
                    false
                }
            },
    ) {
        if (model != null) {
            val platformContext = LocalPlatformContext.current
            val request = remember(platformContext, model, decodeSize) {
                imageViewerImageRequest(platformContext, model, decodeSize)
            }
            SketchZoomAsyncImage(
                request = request,
                contentDescription = null,
                sketch = sketch,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(IMAGE_VIEWER_TEST_TAG)
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(tapGuard) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { (it.position - down.position).getDistance() > slop }) {
                                    dragged = true
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            tapGuard.dragged = dragged
                        }
                    }
                    .then(imageModifier),
                state = imageState,
                contentScale = contentScale,
                zoomState = zoomState,
                onTap = if (closeOnTap) {
                    { if (!tapGuard.dragged) onClose() }
                } else {
                    null
                },
            )
        }
        if (loadState is LoadState.Error) {
            Text(
                stringResource(Lang.image_viewer_load_failed),
                Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ImageViewerToolbar(
            zoomable = zoomState.zoomable,
            scalePercent = if (loaded) zoomState.currentScalePercent() else null,
            enabled = loaded,
            canSave = exported != null,
            onCopy = onCopy,
            onSave = {
                val file = exported ?: return@ImageViewerToolbar
                scope.launch {
                    try {
                        if (fileSaver.save(file)) toaster.toast(savedText)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to save image ${file.fileName}" }
                        toaster.toast(saveFailedText)
                    }
                }
            },
            showCloseButton = showCloseButton,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
    }
}

/**
 * 当前显示比例, 相对于原图像素: 100% 表示原图一个像素对应屏幕一个 dp.
 * Sketch 会按显示尺寸缩小解码, 所以要用分块加载拿到的原图尺寸换算.
 */
@Composable
private fun com.github.panpf.zoomimage.compose.ZoomState.currentScalePercent(): Int {
    val contentWidth = zoomable.contentSize.width
    val originalWidth = subsampling.imageInfo?.width
    val sourceFactor = if (originalWidth != null && originalWidth > 0 && contentWidth > 0) {
        contentWidth.toFloat() / originalWidth
    } else {
        1f
    }
    return (zoomable.transform.scaleX * sourceFactor * 100).roundToInt()
}

@Composable
private fun ImageViewerToolbar(
    zoomable: ZoomableState,
    scalePercent: Int?,
    enabled: Boolean,
    canSave: Boolean,
    onCopy: (() -> Unit)?,
    onSave: () -> Unit,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scale = zoomable.transform.scaleX
    val canZoomOut = enabled && scale > zoomable.minScale + SCALE_EPSILON
    val canZoomIn = enabled && scale < zoomable.maxScale - SCALE_EPSILON

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { scope.launch { zoomable.scale(scale / ZOOM_STEP, animated = true) } },
                enabled = canZoomOut,
                modifier = Modifier.testTag(ImageViewerTestTags.ZOOM_OUT),
            ) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = stringResource(Lang.image_viewer_zoom_out))
            }
            Text(
                text = scalePercent?.let { "$it%" } ?: "—",
                modifier = Modifier.testTag(ImageViewerTestTags.SCALE_TEXT),
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(
                onClick = { scope.launch { zoomable.scale(scale * ZOOM_STEP, animated = true) } },
                enabled = canZoomIn,
                modifier = Modifier.testTag(ImageViewerTestTags.ZOOM_IN),
            ) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = stringResource(Lang.image_viewer_zoom_in))
            }
            IconButton(
                onClick = { scope.launch { zoomable.scale(zoomable.minScale, animated = true) } },
                enabled = canZoomOut,
                modifier = Modifier.testTag(ImageViewerTestTags.RESET_ZOOM),
            ) {
                Icon(Icons.Rounded.FitScreen, contentDescription = stringResource(Lang.image_viewer_reset_zoom))
            }
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    enabled = canSave,
                    modifier = Modifier.testTag(ImageViewerTestTags.COPY),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(Lang.image_viewer_copy))
                }
            }
            IconButton(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.testTag(ImageViewerTestTags.SAVE),
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(Lang.image_viewer_save))
            }
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag(ImageViewerTestTags.CLOSE),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Lang.image_viewer_close))
                }
            }
        }
    }
}

private const val SCALE_EPSILON = 0.001f

private val exportDirectoryCleared = atomic(false)

/**
 * 查看器导出副本所在目录 (`cacheDir/image-viewer`). 进程内首次使用时清空上次运行留下的文件.
 */
fun imageViewerExportDirectory(context: ContextMP): SystemPath {
    val directory = context.files.cacheDir.resolve("image-viewer")
    if (exportDirectoryCleared.compareAndSet(expect = false, update = true)) {
        runCatching { directory.deleteRecursively() }
    }
    return directory
}

private class ImageViewerTapGuard {
    /** 最近一次手势是否拖动过 (超过 touchSlop). Initial 阶段写, zoomimage 的 onTap (Main 阶段) 读. */
    var dragged: Boolean = false
}

/**
 * 查看器加载图片用的请求. 独立窗口预加载 (拿尺寸) 和显示都用同一个请求, 第二次直接命中内存缓存.
 *
 * @param decodeSize 解码尺寸上限 (px), 见 [ImageViewerContent].
 */
fun imageViewerImageRequest(context: PlatformContext, model: String, decodeSize: IntSize?): ImageRequest {
    return ImageRequest(context, model) {
        if (decodeSize != null) {
            // 只按 2 的幂采样, 不裁剪; 宽高都不超过 decodeSize
            size(decodeSize.width, decodeSize.height)
            precision(Precision.SMALLER_SIZE)
        }
    }
}

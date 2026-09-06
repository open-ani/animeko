/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import me.him188.ani.app.ui.foundation.imageviewer.FileKitImageFileSaver
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerContent
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerExportedFile
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerTestTags
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.image_viewer_drag_out
import me.him188.ani.app.ui.lang.image_viewer_save
import me.him188.ani.app.ui.lang.image_viewer_window_title
import me.him188.ani.utils.io.absolutePath
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File

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
        val window = this.window
        val saveDialogTitle = stringResource(Lang.image_viewer_save)
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
            extraActions = { exported -> ImageDragOutHandle(exported) },
        )
    }
}

/**
 * 拖拽把手: 按住拖到其他应用 (访达, 聊天软件等) 即可把图片文件传过去.
 * 图片本体的拖动手势用于平移, 所以拖出用单独的把手.
 */
@Composable
private fun ImageDragOutHandle(exported: ImageViewerExportedFile?) {
    val enabled = exported != null
    val dragSource = if (exported != null) {
        val file = File(exported.path.absolutePath)
        Modifier.dragAndDropSource {
            DragAndDropTransferData(
                transferable = DragAndDropTransferable(FileListTransferable(listOf(file))),
                supportedActions = listOf(DragAndDropTransferAction.Copy),
            )
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .testTag(ImageViewerTestTags.DRAG_OUT)
            .then(dragSource)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Lang.image_viewer_drag_out),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

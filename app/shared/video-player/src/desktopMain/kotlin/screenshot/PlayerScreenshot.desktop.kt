/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.features.DesktopFileRevealer
import me.him188.ani.app.platform.features.SystemShareSheet
import me.him188.ani.app.platform.files
import me.him188.ani.app.ui.foundation.decodeImageBitmap
import me.him188.ani.app.ui.foundation.imageviewer.ImageClipboard
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerExportedFile
import me.him188.ani.app.ui.foundation.imageviewer.rememberImageClipboard
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toFile
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Screenshots
import java.io.File
import java.io.IOException

@Composable
actual fun rememberPlayerScreenshotCapturer(): PlayerScreenshotCapturer {
    val context = LocalContext.current
    return remember(context) { DesktopPlayerScreenshotCapturer(context) }
}

@Composable
actual fun rememberPlayerScreenshotSharer(): PlayerScreenshotSharer {
    val clipboard = rememberImageClipboard()
    val window = LocalPlatformWindow.current
    return remember(clipboard, window) { DesktopPlayerScreenshotSharer(clipboard, window) }
}

private const val SCREENSHOT_DIRECTORY = "Animeko"

/**
 * 通过播放器后端的 [Screenshots] 把当前帧写入用户图片目录下的 `Animeko` 文件夹 (`~/Pictures/Animeko`);
 * 图片目录不可用时退回应用数据目录下的 `screenshots`. 桌面系统写入用户目录不需要权限.
 */
private class DesktopPlayerScreenshotCapturer(
    private val context: Context,
) : PlayerScreenshotCapturer {
    override fun isSupported(player: MediampPlayer): Boolean = player.features[Screenshots] != null

    override suspend fun capture(player: MediampPlayer, fileName: String): PlayerScreenshotResult {
        val screenshots = player.features[Screenshots]
            ?: return PlayerScreenshotResult.Failure(PlayerScreenshotFailure.Unsupported)
        if (player.mediaProperties.value == null) {
            return PlayerScreenshotResult.Failure(PlayerScreenshotFailure.NoFrame)
        }
        return runCatchingCancellable {
            val file = withContext(Dispatchers.IO_) { screenshotDirectory().resolve(fileName) }
            screenshots.takeScreenshot(file.absolutePath)
            // 后端不报告失败, 以文件是否写出为准; 解码整图也留在 IO 线程
            val preview = withContext(Dispatchers.IO_) {
                if (!file.isFile || file.length() == 0L) throw IOException("Screenshot was not written to $file")
                decodeImageBitmap(file.readBytes()).limitedToPreviewSize()
            }
            PlayerScreenshotResult.Success(SavedPlayerScreenshot(preview, fileName, file.absolutePath))
        }.getOrElse { PlayerScreenshotResult.Failure(PlayerScreenshotFailure.SaveFailed(it)) }
    }

    private fun screenshotDirectory(): File {
        val pictures = File(System.getProperty("user.home"), "Pictures")
        val preferred = pictures.resolve(SCREENSHOT_DIRECTORY)
        if (pictures.isDirectory && (preferred.isDirectory || preferred.mkdirs())) return preferred
        return context.files.dataDir.toFile().resolve("screenshots").also { it.mkdirs() }
    }
}

/**
 * 「分享」打开系统分享面板 ([SystemShareSheet]: Windows 的 Share UI, macOS 的分享菜单); 面板不可用 (Linux) 或没能显示时,
 * 退回到在文件管理器中定位截图文件. 「复制」把图片连同文件一起放进剪贴板.
 */
private class DesktopPlayerScreenshotSharer(
    private val clipboard: ImageClipboard?,
    private val window: PlatformWindow,
) : PlayerScreenshotSharer {
    override suspend fun share(screenshot: SavedPlayerScreenshot, anchor: DpRect?): Boolean {
        val file = File(screenshot.location)
        val sheet = SystemShareSheet.current
        if (sheet != null && sheet.shareFile(window.windowHandle, file, anchor)) {
            return true
        }
        return DesktopFileRevealer.revealFile(file)
    }

    override suspend fun copy(screenshot: SavedPlayerScreenshot): Boolean {
        val clipboard = clipboard ?: return false
        return runCatchingCancellable {
            clipboard.copy(
                ImageViewerExportedFile(
                    path = Path(screenshot.location).inSystem,
                    baseName = screenshot.fileName.substringBeforeLast('.'),
                    extension = screenshot.fileName.substringAfterLast('.', "png"),
                ),
            )
        }.isSuccess
    }
}

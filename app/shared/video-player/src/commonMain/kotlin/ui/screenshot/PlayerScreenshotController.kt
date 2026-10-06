/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.ImageViewerHandler
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_screenshot_copied
import me.him188.ani.app.ui.lang.video_player_screenshot_copy_failed
import me.him188.ani.app.ui.lang.video_player_screenshot_failed_no_frame
import me.him188.ani.app.ui.lang.video_player_screenshot_failed_permission
import me.him188.ani.app.ui.lang.video_player_screenshot_failed_save
import me.him188.ani.app.ui.lang.video_player_screenshot_failed_save_reason
import me.him188.ani.app.ui.lang.video_player_screenshot_failed_unsupported
import me.him188.ani.app.ui.lang.video_player_screenshot_share_failed
import me.him188.ani.app.videoplayer.screenshot.PlayerScreenshotCapturer
import me.him188.ani.app.videoplayer.screenshot.PlayerScreenshotFailure
import me.him188.ani.app.videoplayer.screenshot.PlayerScreenshotResult
import me.him188.ani.app.videoplayer.screenshot.PlayerScreenshotSharer
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot
import me.him188.ani.app.videoplayer.screenshot.rememberPlayerScreenshotCapturer
import me.him188.ani.app.videoplayer.screenshot.rememberPlayerScreenshotSharer
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.getString
import org.openani.mediamp.MediampPlayer

/**
 * 播放器截图流程的状态持有者: 截图并展示到 [panelState], 面板上的分享 / 复制 / 打开, 以及失败提示.
 * 一次只截一张, 上一张还在保存时忽略再次截图.
 */
@Stable
class PlayerScreenshotController(
    private val player: MediampPlayer,
    private val capturer: PlayerScreenshotCapturer,
    private val sharer: PlayerScreenshotSharer,
    private val imageViewer: ImageViewerHandler,
    private val toaster: Toaster,
    private val scope: CoroutineScope,
) {
    val panelState = PlayerScreenshotPanelState()

    /** 当前平台与播放器是否支持截图. 为 `false` 时不显示截图按钮. */
    val isSupported: Boolean get() = capturer.isSupported(player)

    private val captureTasker = MonoTasker(scope)

    /** 截图并保存为 [fileName]; 成功则展示面板, 失败则 toast 说明原因. */
    fun take(fileName: String) {
        if (captureTasker.isRunning.value) return
        captureTasker.launch {
            when (val result = capturer.capture(player, fileName)) {
                is PlayerScreenshotResult.Success -> panelState.present(result.screenshot)
                is PlayerScreenshotResult.Failure -> {
                    val reason = result.reason
                    logger.warn((reason as? PlayerScreenshotFailure.SaveFailed)?.cause) {
                        "Player screenshot failed: $reason"
                    }
                    toaster.toast(reason.toastMessage())
                }
            }
        }
    }

    /** [anchor] 是分享按钮在窗口中的位置, 见 [PlayerScreenshotSharer.share]. */
    fun share(screenshot: SavedPlayerScreenshot, anchor: DpRect?) {
        scope.launch {
            if (!sharer.share(screenshot, anchor)) toaster.toast(getString(Lang.video_player_screenshot_share_failed))
        }
    }

    fun copy(screenshot: SavedPlayerScreenshot) {
        scope.launch {
            val message = if (sharer.copy(screenshot)) {
                Lang.video_player_screenshot_copied
            } else {
                Lang.video_player_screenshot_copy_failed
            }
            toaster.toast(getString(message))
        }
    }

    /** 截图在相册 / 图片目录里, 用应用内的图片查看器打开. */
    fun open(screenshot: SavedPlayerScreenshot) {
        imageViewer.viewImage(screenshot.location)
    }
}

private val logger = logger<PlayerScreenshotController>()

private suspend fun PlayerScreenshotFailure.toastMessage(): String = when (this) {
    PlayerScreenshotFailure.PermissionDenied -> getString(Lang.video_player_screenshot_failed_permission)
    PlayerScreenshotFailure.Unsupported -> getString(Lang.video_player_screenshot_failed_unsupported)
    PlayerScreenshotFailure.NoFrame -> getString(Lang.video_player_screenshot_failed_no_frame)
    is PlayerScreenshotFailure.SaveFailed -> {
        val detail = cause.message?.takeIf { it.isNotBlank() }
        if (detail == null) {
            getString(Lang.video_player_screenshot_failed_save)
        } else {
            getString(Lang.video_player_screenshot_failed_save_reason, detail)
        }
    }
}

/** 当前平台的截图器与分享实现, 加上页面的图片查看器和 toast. */
@Composable
fun rememberPlayerScreenshotController(player: MediampPlayer): PlayerScreenshotController {
    val capturer = rememberPlayerScreenshotCapturer()
    val sharer = rememberPlayerScreenshotSharer()
    val imageViewer = LocalImageViewerHandler.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    return remember(player, capturer, sharer, imageViewer, toaster, scope) {
        PlayerScreenshotController(player, capturer, sharer, imageViewer, toaster, scope)
    }
}

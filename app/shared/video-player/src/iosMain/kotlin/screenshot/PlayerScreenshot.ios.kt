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
import androidx.compose.ui.unit.DpRect
import org.openani.mediamp.MediampPlayer

/** iOS 使用 AVKit 后端, 它没有读取当前帧的能力, 因此不支持截图, 播放器不显示截图按钮. */
private object UnsupportedPlayerScreenshotCapturer : PlayerScreenshotCapturer {
    override fun isSupported(player: MediampPlayer): Boolean = false

    override suspend fun capture(player: MediampPlayer, fileName: String): PlayerScreenshotResult =
        PlayerScreenshotResult.Failure(PlayerScreenshotFailure.Unsupported)
}

private object NoOpPlayerScreenshotSharer : PlayerScreenshotSharer {
    override suspend fun share(screenshot: SavedPlayerScreenshot, anchor: DpRect?): Boolean = false
    override suspend fun copy(screenshot: SavedPlayerScreenshot): Boolean = false
}

@Composable
actual fun rememberPlayerScreenshotCapturer(): PlayerScreenshotCapturer = UnsupportedPlayerScreenshotCapturer

@Composable
actual fun rememberPlayerScreenshotSharer(): PlayerScreenshotSharer = NoOpPlayerScreenshotSharer

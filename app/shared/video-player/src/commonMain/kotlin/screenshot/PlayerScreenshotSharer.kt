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

/** 截图面板「分享」的结果, 决定要不要提示用户. */
enum class PlayerScreenshotShareOutcome {
    /** 已交给系统分享面板. */
    Shared,

    /** 平台没有系统分享面板, 已把图片复制到剪贴板. */
    CopiedToClipboard,

    /** 没有可用的分享途径. */
    Failed,
}

/** 截图预览面板上的动作. */
interface PlayerScreenshotSharer {
    suspend fun share(screenshot: SavedPlayerScreenshot): PlayerScreenshotShareOutcome

    /**
     * 打开截图: Android 用系统图片查看器, 桌面在文件管理器中定位文件.
     * @return 是否成功
     */
    suspend fun open(screenshot: SavedPlayerScreenshot): Boolean
}

/** 当前平台的截图分享实现. */
@Composable
expect fun rememberPlayerScreenshotSharer(): PlayerScreenshotSharer

/** 没有任何分享途径的平台使用的实现. */
object NoOpPlayerScreenshotSharer : PlayerScreenshotSharer {
    override suspend fun share(screenshot: SavedPlayerScreenshot): PlayerScreenshotShareOutcome =
        PlayerScreenshotShareOutcome.Failed

    override suspend fun open(screenshot: SavedPlayerScreenshot): Boolean = false
}

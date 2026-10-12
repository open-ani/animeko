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

/** 截图预览面板上的动作. 两个方法都不抛出异常, 返回是否成功. */
interface PlayerScreenshotSharer {
    /**
     * 分享: 交给系统分享面板. Android 用分享 Intent; Windows 用 Share UI, macOS 用分享菜单; Linux 没有系统分享面板,
     * 在文件管理器中定位截图文件.
     *
     * @param anchor 分享按钮在窗口中的位置 (dp). 弹出式的分享面板从它旁边弹出, 由系统定位的面板忽略它; 不知道时传 `null`.
     */
    suspend fun share(screenshot: SavedPlayerScreenshot, anchor: DpRect?): Boolean

    /** 把截图图片复制到系统剪贴板. */
    suspend fun copy(screenshot: SavedPlayerScreenshot): Boolean
}

/** 当前平台的截图分享实现. */
@Composable
expect fun rememberPlayerScreenshotSharer(): PlayerScreenshotSharer

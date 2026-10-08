/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot

/**
 * 播放器截图预览面板的状态.
 *
 * 截图成功后调用 [present]: [PlayerScreenshotOverlay] 闪光并把截图从视频区域收进角落.
 * 面板超时或用户点开截图后由覆盖层调用 [dismiss].
 */
@Stable
class PlayerScreenshotPanelState {
    /** 当前展示的截图; null 表示面板隐藏. */
    var current: PlayerScreenshotPresentation? by mutableStateOf(null)
        private set

    fun present(screenshot: SavedPlayerScreenshot) {
        current = PlayerScreenshotPresentation(screenshot)
    }

    /** 收起 [presentation]. 它已被更新的截图替换时不做任何事. */
    fun dismiss(presentation: PlayerScreenshotPresentation) {
        if (current === presentation) current = null
    }
}

/** 一次截图的展示. 每次 [PlayerScreenshotPanelState.present] 都是新的实例, 覆盖层据此重放入场动画. */
@Stable
class PlayerScreenshotPresentation(
    val screenshot: SavedPlayerScreenshot,
) {
    /**
     * 是否已经停靠在角落.
     * 覆盖层在面板停靠期间被重新组合 (例如进出画中画) 时, 面板直接停在角落, 不重放入场.
     */
    var docked: Boolean by mutableStateOf(false)
}

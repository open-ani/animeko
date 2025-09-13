/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.ui.geometry.Rect
import me.him188.ani.app.platform.Context
import org.openani.mediamp.MediampPlayer

/**
 * Controller for Picture-in-Picture functionality.
 *
 * This is a platform-specific implementation that will be provided for Android and iOS.
 * The controller should be initialized early and share the lifecycle with the composable.
 *
 * @param context The application context
 * @param player The MediampPlayer instance that should be used for PiP mode
 */
expect class PictureInPictureController(context: Context, player: MediampPlayer) {


    /**
     * Enters Picture-in-Picture mode and returns to the system home page.
     * The video will continue playing in a small window.
     *
     * @param rect The bounds of the video view in window coordinates
     */
    fun enterPictureInPictureMode(rect: Rect)
}
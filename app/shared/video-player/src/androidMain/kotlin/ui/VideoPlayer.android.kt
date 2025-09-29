/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
    aspectRatioMode: AspectRatioMode,
) {
    val isPreviewing by rememberUpdatedState(LocalIsPreviewing.current)

    if (isPreviewing) {
        Box(modifier)
    } else {
        key(aspectRatioMode) {
            // 设置 mediamp 的 aspect ratio mode
            player.features[VideoAspectRatio.Key]?.setMode(aspectRatioMode)

            ExoPlayerMediampPlayerSurface(player as ExoPlayerMediampPlayer, modifier) {
                controllerAutoShow = false
                useController = false
                controllerHideOnTouch = false
                controllerAutoShow = false
                useController = false
                controllerHideOnTouch = false
                subtitleView?.apply {
                    this.setStyle(
                        CaptionStyleCompat(
                            Color.WHITE,
                            0x000000FF,
                            0x00000000,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            Color.BLACK,
                            Typeface.DEFAULT,
                        ),
                    )
                }
                setControllerVisibilityListener(
                    ControllerVisibilityListener { visibility ->
                        if (visibility == View.VISIBLE) {
                            hideController()
                        }
                    },
                )
            }
        }
    }
}
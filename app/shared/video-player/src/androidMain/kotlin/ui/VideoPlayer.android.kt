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
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.flow.combine
import me.him188.ani.app.videoplayer.media.LibassExoPlayerMediampPlayer
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface
import org.openani.mediamp.features.subtitleAdjustment

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier
) {
    val isPreviewing by rememberUpdatedState(me.him188.ani.app.ui.foundation.LocalIsPreviewing.current)

    if (isPreviewing) {
        Box(modifier)
    } else {
        val libassPlayer = player as? LibassExoPlayerMediampPlayer
        val exoPlayer = libassPlayer?.exoMediampPlayer ?: player as ExoPlayerMediampPlayer
        var subtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }

        // Adjustments that libass applies itself are handled by the player; these two only reach
        // the cues that Media3 renders (plain-text and other non-ASS subtitles).
        val adjustment = player.subtitleAdjustment
        LaunchedEffect(subtitleViewRef, adjustment) {
            val subtitleView = subtitleViewRef ?: return@LaunchedEffect
            if (adjustment == null) return@LaunchedEffect
            combine(adjustment.fontScale, adjustment.verticalPosition, ::Pair)
                .collect { (fontScale, verticalPosition) ->
                    subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * fontScale)
                    subtitleView.setBottomPaddingFraction(bottomPaddingFractionOf(verticalPosition))
                }
        }

        ExoPlayerMediampPlayerSurface(exoPlayer, modifier) {
            (videoSurfaceView as? SurfaceView)?.let { registerAndroidVideoSurface(player, it) }
            controllerAutoShow = false
            useController = false
            controllerHideOnTouch = false
            subtitleView?.apply {
                subtitleViewRef = this
                libassPlayer?.let { addView(AssSubtitleView(context, it.assHandler)) }
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

/**
 * Maps a [org.openani.mediamp.features.SubtitleAdjustment.verticalPosition] to the bottom padding
 * of a [SubtitleView], keeping the Media3 default padding at both ends of the range so that
 * subtitles never touch the video edges.
 */
@OptIn(UnstableApi::class)
private fun bottomPaddingFractionOf(verticalPosition: Float): Float {
    val default = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
    return default + (1f - verticalPosition.coerceIn(0f, 1f)) * (1f - 2 * default)
}

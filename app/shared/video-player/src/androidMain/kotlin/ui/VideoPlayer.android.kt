/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.videoplayer.media.LibassExoPlayerMediampPlayer
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier
) {
    val isPreviewing by rememberUpdatedState(LocalIsPreviewing.current)

    if (isPreviewing) {
        Box(modifier)
    } else {
        val libassPlayer = player as? LibassExoPlayerMediampPlayer
        val exoPlayer = libassPlayer?.exoMediampPlayer ?: player as ExoPlayerMediampPlayer
        var videoSurface by remember { mutableStateOf<SurfaceView?>(null) }
        // A paused effect graph retains pixels rendered for its last buffer dimensions. Keep that
        // buffer intact while SurfaceView scales it with the layout; resizing the buffer itself
        // leaves the retained frame cropped or aligned to the old left edge. Active playback and
        // video-size changes use layout-sized buffers so newly rendered frames match the viewport.
        DisposableEffect(exoPlayer, videoSurface) {
            val impl = exoPlayer.impl
            val surface = videoSurface
            fun updateBufferSize() {
                if (surface == null) return
                val hasRetainedFrame = impl.playbackState == Player.STATE_READY ||
                    impl.playbackState == Player.STATE_ENDED
                if (!impl.isPlaying && hasRetainedFrame) {
                    val frame = surface.holder.surfaceFrame
                    if (frame.width() > 0 && frame.height() > 0) {
                        surface.holder.setFixedSize(frame.width(), frame.height())
                    }
                } else {
                    surface.holder.setSizeFromLayout()
                }
            }
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateBufferSize()

                override fun onPlaybackStateChanged(playbackState: Int) = updateBufferSize()

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    surface?.holder?.setSizeFromLayout()
                }

                override fun onRenderedFirstFrame() = updateBufferSize()
            }
            impl.addListener(listener)
            updateBufferSize()
            onDispose {
                impl.removeListener(listener)
                surface?.holder?.setSizeFromLayout()
            }
        }
        ExoPlayerMediampPlayerSurface(exoPlayer, modifier) {
            (videoSurfaceView as? SurfaceView)?.let {
                videoSurface = it
                registerAndroidVideoSurface(player, it)
            }
            controllerAutoShow = false
            useController = false
            controllerHideOnTouch = false
            subtitleView?.apply {
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

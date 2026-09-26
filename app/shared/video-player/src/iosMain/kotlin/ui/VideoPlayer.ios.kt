/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.avkit.PlayerUIView
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRect
import platform.UIKit.UIColor

/**
 * Displays a video player itself. There is no control bar or any other UI elements.
 *
 * The size of the video player is undefined by default. It may take the entire screen or vise versa.
 * Please apply a size [Modifier] to control the size of the video player.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier
) {
    check(player is AVKitMediampPlayer) { "MediampPlayer in iOS must be AVKitMediampPlayer, but got $player" }

    val avPlayer = player.impl
    val aspectRatioMode by produceState(AspectRatioMode.FIT) {
        val feature = player.features[VideoAspectRatio.Key] ?: return@produceState
        feature.mode.collect { value = it }
    }

    UIKitView(
        factory = {
            val view = PlayerUIView(frame = cValue<CGRect>())
            view.backgroundColor = UIColor.blackColor
            view.player = avPlayer
            IosVideoLayerRegistry.register(
                player,
                checkNotNull(view.layer as AVPlayerLayer) { "PlayerUIView.layer must be AVPlayerLayer" },
            )
            view
        },
        modifier = modifier,
        update = { view ->
            view.player = avPlayer
            view.videoGravity = when (aspectRatioMode) {
                AspectRatioMode.FIT -> AVLayerVideoGravityResizeAspect
                AspectRatioMode.STRETCH -> AVLayerVideoGravityResize
                AspectRatioMode.CROP -> AVLayerVideoGravityResizeAspectFill
            }
        },
        onRelease = { view ->
            IosVideoLayerRegistry.unregister(player)
            view.player = null
        },
    )
}

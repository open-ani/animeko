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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import me.him188.ani.app.platform.SwiftBridge
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import platform.AVFoundation.AVPlayerLayer

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
    val enhancementMode by IosVideoLayerRegistry.enhancementMode(player).collectAsState()
    val aspectRatioMode by produceState(AspectRatioMode.FIT, player) {
        val feature = player.features[VideoAspectRatio.Key] ?: return@produceState
        feature.mode.collect { value = it }
    }

    key(player) {
        UIKitView(
            factory = {
                val view = SwiftBridge.createEnhancedPlayerView(avPlayer) {
                    IosVideoLayerRegistry.enhancementFailures(player).tryEmit(Unit)
                }
                IosVideoLayerRegistry.register(player, view.layer as AVPlayerLayer)
                view
            },
            modifier = modifier,
            update = { view ->
                SwiftBridge.updateEnhancedPlayerView(
                    view,
                    enhancementMode.ordinal,
                    when (aspectRatioMode) {
                        AspectRatioMode.FIT -> 0
                        AspectRatioMode.STRETCH -> 1
                        AspectRatioMode.CROP -> 2
                    },
                )
            },
            onRelease = { view ->
                IosVideoLayerRegistry.unregister(player)
                SwiftBridge.releaseEnhancedPlayerView(view)
            },
        )
    }
}

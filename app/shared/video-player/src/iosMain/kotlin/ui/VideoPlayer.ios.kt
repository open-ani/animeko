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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import me.him188.ani.app.platform.SwiftBridge
import me.him188.ani.app.videoplayer.player.AniAVKitMediampPlayer
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import platform.AVFoundation.AVPlayerLayer
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel

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
    check(player is AniAVKitMediampPlayer) { "MediampPlayer in iOS must be AniAVKitMediampPlayer, but got $player" }

    val avPlayer = player.impl
    val subtitleText by player.subtitleText.collectAsState()
    val subtitleLabel = remember(player) {
        UILabel().apply {
            translatesAutoresizingMaskIntoConstraints = false
            numberOfLines = 0
            textAlignment = NSTextAlignmentCenter
            textColor = UIColor.whiteColor
            font = UIFont.systemFontOfSize(22.0)
            layer.shadowColor = UIColor.blackColor.CGColor
            layer.shadowOpacity = 1f
            layer.shadowRadius = 2.0
            userInteractionEnabled = false
        }
    }
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
                view.addSubview(subtitleLabel)
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        subtitleLabel.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor, 24.0),
                        subtitleLabel.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, -24.0),
                        subtitleLabel.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor, -24.0),
                    ),
                )
                IosVideoLayerRegistry.register(player, view.layer as AVPlayerLayer)
                view
            },
            modifier = modifier,
            update = { view ->
                subtitleLabel.text = subtitleText
                subtitleLabel.hidden = subtitleText.isEmpty()
                SwiftBridge.updateEnhancedPlayerView(
                    view,
                    enhancementMode.ordinal,
                    when (aspectRatioMode) {
                        AspectRatioMode.FIT -> 0
                        AspectRatioMode.STRETCH -> 1
                        AspectRatioMode.CROP -> 2
                    },
                )
                view.bringSubviewToFront(subtitleLabel)
            },
            onRelease = { view ->
                IosVideoLayerRegistry.unregister(player)
                SwiftBridge.releaseEnhancedPlayerView(view)
            },
        )
    }
}

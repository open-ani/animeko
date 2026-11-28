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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.platform.SwiftBridge
import me.him188.ani.app.videoplayer.player.AniAVKitMediampPlayer
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleDelayMillis
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleSafeArea
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleTextConversion
import me.him188.ani.app.videoplayer.subtitle.rememberConvertedSubtitleText
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.subtitleTracks
import platform.AVFoundation.AVPlayerLayer
import platform.UIKit.NSLayoutConstraint

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
    val selectedSubtitle by checkNotNull(player.subtitleTracks).selected.collectAsState()
    var nativeText by remember(player) { mutableStateOf("") }
    val subtitleAppearance = LocalSubtitleAppearance.current
    val subtitleDelay = LocalSubtitleDelayMillis.current
    LaunchedEffect(player, subtitleDelay) { player.subtitleDelayMillis.value = subtitleDelay }
    val capture = LocalSubtitleTextConversion.current != SubtitleTextConversion.ORIGINAL || subtitleAppearance.requiresTextOverlay || subtitleDelay != 0L
    val displayedText = rememberConvertedSubtitleText(when {
        selectedSubtitle == null -> ""
        selectedSubtitle?.id?.startsWith("file:") == true -> subtitleText
        else -> nativeText
    })
    val subtitleSafeArea = LocalSubtitleSafeArea.current
    val subtitleOverlay = remember(player) { IosSubtitleOverlay() }
    val nativeSubtitleStyle = remember(player) { IosNativeSubtitleStyle(avPlayer) }
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
                view.addSubview(subtitleOverlay)
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        subtitleOverlay.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor, 0.0),
                        subtitleOverlay.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, 0.0),
                        subtitleOverlay.topAnchor.constraintEqualToAnchor(view.topAnchor),
                        subtitleOverlay.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor, 0.0),
                    ),
                )
                IosVideoLayerRegistry.register(player, view.layer as AVPlayerLayer)
                view
            },
            modifier = modifier,
            update = { view ->
                SwiftBridge.setPlayerSubtitleCapture(view, capture) { nativeText = it }
                SwiftBridge.setPlayerSubtitleDelay(view, subtitleDelay)
                subtitleOverlay.update(displayedText, subtitleAppearance, subtitleSafeArea)
                nativeSubtitleStyle.update(subtitleAppearance, subtitleSafeArea, view.bounds.useContents { size.height })
                SwiftBridge.updateEnhancedPlayerView(
                    view,
                    enhancementMode.ordinal,
                    when (aspectRatioMode) {
                        AspectRatioMode.FIT -> 0
                        AspectRatioMode.STRETCH -> 1
                        AspectRatioMode.CROP -> 2
                    },
                )
                view.bringSubviewToFront(subtitleOverlay)
            },
            onRelease = { view ->
                IosVideoLayerRegistry.unregister(player)
                SwiftBridge.releaseEnhancedPlayerView(view)
            },
        )
    }
}

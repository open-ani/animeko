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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val avPlayer = (player as? AVKitMediampPlayer)?.impl

    // App 自有的 surface 包装, 而非 mediamp 的 `MediampPlayerSurface`:
    // 1. 依赖 UIKitView 默认的 cooperative 互操作 (UIKitInteropProperties 默认即
    //    Cooperative 模式) — 非 cooperative 在画中画进出/前后台切换时有已知的黑帧问题
    // 2. 创建时把 AVPlayerLayer 注册进 IosVideoLayerRegistry, 供 AVPictureInPictureController 使用
    // 复用 mediamp-avkit 的 public `PlayerUIView` (其 layerClass 即 AVPlayerLayer).
    val aspectFeature = remember(player) { player.features[VideoAspectRatio.Key] }
    val aspectRatioModeState = aspectFeature?.mode?.collectAsState()
    val aspectRatioMode by (aspectRatioModeState ?: remember { mutableStateOf(AspectRatioMode.FIT) })

    UIKitView(
        factory = {
            PlayerUIView(frame = cValue<CGRect>()).apply {
                backgroundColor = UIColor.blackColor
                avPlayer?.let { this.player = it }
                (layer as? AVPlayerLayer)?.let { IosVideoLayerRegistry.register(player, it) }
            }
        },
        modifier = modifier,
        update = { view ->
            avPlayer?.let { view.player = it }
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

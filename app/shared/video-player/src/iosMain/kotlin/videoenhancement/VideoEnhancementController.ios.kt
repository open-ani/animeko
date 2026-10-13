/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.videoplayer.ui.IosVideoLayerRegistry
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import kotlin.coroutines.CoroutineContext

actual fun createVideoEnhancementController(
    player: MediampPlayer,
    playerKernelConfig: Flow<PlayerKernelConfig>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? = if (player is AVKitMediampPlayer) {
    IosVideoEnhancementController(player, parentCoroutineContext)
} else null

private class IosVideoEnhancementController(
    player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private val surfaceMode = IosVideoLayerRegistry.enhancementMode(player)

    init {
        startObserving()
        scope.launch {
            IosVideoLayerRegistry.enhancementFailures(player).collect {
                setMode(VideoEnhancementMode.OFF)
            }
        }
    }

    override suspend fun apply(mode: VideoEnhancementMode, videoSize: VideoDimensions?, viewportSize: VideoDimensions?) {
        surfaceMode.value = mode
    }

    override fun restore() {
        surfaceMode.value = VideoEnhancementMode.OFF
    }
}

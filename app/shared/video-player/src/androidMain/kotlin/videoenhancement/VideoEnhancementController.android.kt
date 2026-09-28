/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package me.him188.ani.app.videoplayer.videoenhancement

import androidx.media3.common.Effect
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.CoroutineContext

actual fun createVideoEnhancementController(
    player: MediampPlayer,
    playerKernelConfig: Flow<PlayerKernelConfig>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? {
    val exoPlayer = player.impl as? ExoPlayer ?: return null
    return ExoPlayerVideoEnhancementController(
        player,
        exoPlayer::setVideoEffects,
        playerKernelConfig.map { it.exoPlayerInitEffectGraphInAdvance },
        parentCoroutineContext,
    )
}

internal class ExoPlayerVideoEnhancementController(
    player: MediampPlayer,
    private val setVideoEffects: (List<Effect>) -> Unit,
    preinitVideoEffects: Flow<Boolean>,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private var appliedMode = VideoEnhancementMode.OFF
    private var scalerApplied = false
    private var appliedWidth = 0
    private var appliedHeight = 0

    init {
        // Media3 requires the effect graph to exist before the first prepare in order to
        // support switching effects while playback is active.
        scope.launch {
            if (preinitVideoEffects.first()) {
                setVideoEffects(emptyList())
            }
        }
        startObserving()
    }

    override suspend fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        if (mode == VideoEnhancementMode.OFF) {
            restore()
            return
        }

        // The shader receives input dimensions in configure(). Metadata availability must not
        // rebuild the effect graph: compiling the quality shaders can stall playback.
        val shouldApplyScaler = viewportSize != null
        if (
            appliedMode == mode && scalerApplied == shouldApplyScaler &&
            (!shouldApplyScaler || appliedWidth == viewportSize.width && appliedHeight == viewportSize.height)
        ) return

        setVideoEffects(
            buildList {
                when (mode) {
                    VideoEnhancementMode.OFF -> Unit
                    VideoEnhancementMode.PERFORMANCE -> add(Anime4kRestoreEffect)
                    VideoEnhancementMode.QUALITY -> {
                        add(Anime4kRestoreQualityEffect)
                        add(Anime4kUpscaleQualityEffect)
                    }
                }
                if (shouldApplyScaler) {
                    add(DesktopStyleLanczosSharpEffect(viewportSize.width, viewportSize.height))
                }
            },
        )
        appliedMode = mode
        scalerApplied = shouldApplyScaler
        appliedWidth = if (shouldApplyScaler) viewportSize.width else 0
        appliedHeight = if (shouldApplyScaler) viewportSize.height else 0
    }

    override fun restore() {
        if (appliedMode == VideoEnhancementMode.OFF) return
        setVideoEffects(emptyList())
        appliedMode = VideoEnhancementMode.OFF
        scalerApplied = false
        appliedWidth = 0
        appliedHeight = 0
    }
}

internal const val exoEffectShaderDirectory = "exo-effects"

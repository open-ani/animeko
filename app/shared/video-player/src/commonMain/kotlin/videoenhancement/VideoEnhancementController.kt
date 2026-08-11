/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.VideoDimensions
import kotlin.coroutines.CoroutineContext

enum class VideoEnhancementMode {
    OFF,
    PERFORMANCE,
    QUALITY,
}

interface VideoEnhancementController : AutoCloseable {
    val mode: StateFlow<VideoEnhancementMode>

    fun setMode(mode: VideoEnhancementMode)
}

expect fun createVideoEnhancementController(
    player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController?

internal abstract class BaseVideoEnhancementController(
    protected val player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
) : VideoEnhancementController {
    private val controllerJob = SupervisorJob(parentCoroutineContext[Job])
    private val scope = CoroutineScope(parentCoroutineContext + controllerJob + player.mainDispatcher)
    private val mutableMode = MutableStateFlow(VideoEnhancementMode.OFF)

    final override val mode: StateFlow<VideoEnhancementMode> = mutableMode.asStateFlow()

    protected fun startObserving() {
        combine(mode, player.videoSize, player.viewportSize, ::EnhancementState)
            .onEach { apply(it.mode, it.videoSize, it.viewportSize) }
            .launchIn(scope)
    }

    final override fun setMode(mode: VideoEnhancementMode) {
        mutableMode.value = mode
    }

    final override fun close() {
        scope.cancel()
        restore()
    }

    protected abstract fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    )

    protected abstract fun restore()
}

private data class EnhancementState(
    val mode: VideoEnhancementMode,
    val videoSize: VideoDimensions?,
    val viewportSize: VideoDimensions?,
)

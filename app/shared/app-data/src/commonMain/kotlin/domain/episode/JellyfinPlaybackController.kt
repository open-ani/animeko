/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.resolver.JellyfinMediaDataProvider
import me.him188.ani.app.domain.media.resolver.JellyfinPlaybackQualityState
import me.him188.ani.app.domain.media.resolver.OpenedJellyfinPlayback
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQualityMode
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

internal data class ActiveJellyfinPlayback(
    val provider: JellyfinMediaDataProvider,
    val opened: OpenedJellyfinPlayback,
)

/**
 * Owns only the active Jellyfin playback plan.
 *
 * Quality changes deliberately stop the current playback before opening the replacement. The
 * mutex keeps a later media load or episode switch from being overwritten by an older reload.
 */
internal class JellyfinPlaybackController {
    private var activePlayback: ActiveJellyfinPlayback? = null
    private val _qualityState = MutableStateFlow<JellyfinPlaybackQualityState?>(null)
    private val playbackTransitionMutex = Mutex()

    val qualityState: StateFlow<JellyfinPlaybackQualityState?> = _qualityState.asStateFlow()
    val hasPlayback: Boolean get() = activePlayback != null

    fun install(playback: ActiveJellyfinPlayback) {
        activePlayback = playback
        _qualityState.value = playback.opened.state
    }

    suspend fun detach(): ActiveJellyfinPlayback? = playbackTransitionMutex.withLock {
        activePlayback.also {
            activePlayback = null
            _qualityState.value = null
        }
    }

    suspend fun discard(playback: ActiveJellyfinPlayback) {
        withContext(NonCancellable) {
            stopEncodingSafely(playback)
        }
    }

    suspend fun stopIfActive(
        stopLocalPlayback: suspend () -> Unit,
    ): Boolean {
        if (!hasPlayback) return false
        val playback = detach() ?: return false
        var stopFailure: Throwable? = null
        try {
            stopLocalPlayback()
        } catch (e: Throwable) {
            stopFailure = e
        }
        withContext(NonCancellable) {
            stopEncodingSafely(playback)
        }
        stopFailure?.let { throw it }
        return true
    }

    suspend fun reload(
        quality: JellyfinPlaybackQuality,
        startPositionMillis: Long,
        stopLocalPlayback: suspend () -> Unit,
        openAndPlay: suspend (OpenedJellyfinPlayback, startPositionMillis: Long) -> Unit,
    ): Result<Unit> = playbackTransitionMutex.withLock {
        val current = activePlayback
            ?: return@withLock Result.failure(
                IllegalStateException("The current media is not from Jellyfin"),
            )
        if (current.opened.state.selected == quality) {
            return@withLock Result.success(Unit)
        }

        _qualityState.value = current.opened.state.copy(isSwitching = true)
        var replacement: OpenedJellyfinPlayback? = null
        var oldEncodingStopped = false
        try {
            stopLocalPlayback()
            withContext(NonCancellable) {
                stopEncodingSafely(current)
            }
            oldEncodingStopped = true

            current.provider.rememberQuality(quality)
            val opened = current.provider.openPlayback(
                quality = quality,
                requestedMediaSourceId = current.opened.plan.mediaSourceId,
                forceAutoDetection = quality.mode == JellyfinPlaybackQualityMode.AUTO,
            ).also { replacement = it }

            openAndPlay(opened, startPositionMillis)
            activePlayback = ActiveJellyfinPlayback(current.provider, opened)
            _qualityState.value = opened.state
            Result.success(Unit)
        } catch (e: CancellationException) {
            cleanupFailedReload(current, replacement, oldEncodingStopped, stopLocalPlayback)
            throw e
        } catch (e: Throwable) {
            cleanupFailedReload(current, replacement, oldEncodingStopped, stopLocalPlayback)
            logger.warn(e) { "Failed to reload Jellyfin playback quality" }
            Result.failure(e)
        }
    }

    private suspend fun cleanupFailedReload(
        previous: ActiveJellyfinPlayback,
        replacement: OpenedJellyfinPlayback?,
        oldEncodingStopped: Boolean,
        stopLocalPlayback: suspend () -> Unit,
    ) {
        withContext(NonCancellable) {
            try {
                stopLocalPlayback()
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to stop the player after a Jellyfin reload error" }
            }
            if (!oldEncodingStopped) {
                stopEncodingSafely(previous)
            }
            replacement?.let {
                stopEncodingSafely(ActiveJellyfinPlayback(previous.provider, it))
            }
        }
        activePlayback = null
        _qualityState.value = null
    }

    private suspend fun stopEncodingSafely(playback: ActiveJellyfinPlayback) {
        if (!playback.opened.plan.isTranscoding) return
        val completed = withContext(Dispatchers.IO_) {
            withTimeoutOrNull(STOP_ENCODING_TIMEOUT_MILLIS) {
                try {
                    playback.provider.stopEncoding(playback.opened.plan)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to stop a Jellyfin transcoding session" }
                }
                true
            }
        }
        if (completed == null) {
            logger.warn {
                "Timed out stopping Jellyfin transcoding session " +
                        "${playback.opened.plan.playSessionId}"
            }
        }
    }

    private companion object {
        const val STOP_ENCODING_TIMEOUT_MILLIS = 5_000L
        val logger = logger<JellyfinPlaybackController>()
    }
}

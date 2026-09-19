/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * Starts [PikPakCacheService] while any PikPak download is in progress and stops it afterwards.
 *
 * The decision lives here rather than in the service so that there is a single place that knows
 * whether the service should exist; the service renders the notification and handles system timeouts.
 */
class PikPakCacheServiceController(
    private val context: Context,
    private val downloadManager: MediaDownloadManager,
    private val processLifecycle: Lifecycle,
    private val scope: CoroutineScope,
) {
    private val logger = logger<PikPakCacheServiceController>()

    fun start() {
        scope.launch {
            combine(
                pikPakDownloadActivity(downloadManager).map { it.taskCount > 0 },
                // Not part of the decision, only a retry trigger: a start refused in the background
                // gets another attempt when the process returns to the foreground.
                processLifecycle.currentStateFlow,
                PikPakCacheService.backgroundTimedOut,
                PikPakCacheService.isRunning,
            ) { shouldRun, processState, timedOut, serviceRunning ->
                ServiceControlState(shouldRun, processState, timedOut, serviceRunning)
            }
                .distinctUntilChanged()
                .onEach { (shouldRun, processState, timedOut, serviceRunning) ->
                    if (timedOut) {
                        stopService()
                        if (processState.isAtLeast(Lifecycle.State.RESUMED)) {
                            PikPakCacheService.backgroundTimedOut.value = false
                        }
                        return@onEach
                    }
                    when {
                        shouldRun && !serviceRunning -> startService()
                        !shouldRun && serviceRunning -> stopService()
                    }
                }
                .collect()
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PikPakCacheService::class.java))
            logger.info { "PikPakCacheService start requested." }
        } catch (e: IllegalStateException) {
            // The next process-lifecycle change retries while the service still reports stopped.
            logger.warn { "Failed to start PikPakCacheService: $e" }
        }
    }

    private fun stopService() {
        context.stopService(Intent(context, PikPakCacheService::class.java))
        logger.info { "PikPakCacheService stopped." }
    }
}

private data class ServiceControlState(
    val shouldRun: Boolean,
    val processState: Lifecycle.State,
    val timedOut: Boolean,
    val serviceRunning: Boolean,
)

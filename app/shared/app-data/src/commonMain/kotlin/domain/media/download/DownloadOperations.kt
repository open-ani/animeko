/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.utils.logging.logger

data class DownloadOperationResult(val failures: Map<String, Throwable>)

/**
 * Accepts commands synchronously and executes them in submission order in the application scope.
 * Awaiting a result is optional; cancelling the caller does not cancel an accepted command.
 */
class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
) {
    private val logger = logger<DownloadOperations>()
    enum class Action { Pause, Resume, Delete }

    private data class Command(
        val ids: Set<String>,
        val action: Action,
        val result: CompletableDeferred<DownloadOperationResult>,
    )

    private val commands = Channel<Command>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { it.result.cancel() },
    )

    init {
        downloadManager.backgroundScope.launch {
            for (command in commands) {
                try {
                    command.result.complete(execute(command.ids, command.action))
                } catch (e: CancellationException) {
                    command.result.cancel(e)
                    // An individual download may cancel its operation while the application stays active.
                    currentCoroutineContext().ensureActive()
                } catch (e: Throwable) {
                    command.result.completeExceptionally(e)
                    throw e
                }
            }
        }.invokeOnCompletion {
            // Also release queued results when cancelled before the worker's first dispatch.
            commands.cancel()
        }
    }

    /** Call from the event handler before dispatching any coroutine. IDs are captured at submission. */
    fun submit(ids: Set<String>, action: Action): Deferred<DownloadOperationResult> {
        val result = CompletableDeferred<DownloadOperationResult>()
        val command = Command(ids.toSet(), action, result)
        if (commands.trySend(command).isFailure) {
            result.cancel(CancellationException("Download operations have stopped"))
        }
        return result
    }

    private suspend fun execute(ids: Set<String>, action: Action): DownloadOperationResult {
        val failures = mutableMapOf<String, Throwable>()
        for (id in ids) {
            try {
                val cache = downloadManager.findFirstDownload { it.cacheId == id } ?: continue
                when (action) {
                    Action.Pause -> if (cache.state.first() == MediaCacheState.IN_PROGRESS) cache.pause()
                    Action.Resume -> if (cache.state.first() == MediaCacheState.PAUSED) cache.resume()
                    Action.Delete -> deleteCache(cache)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Download operation $action failed for $id", e)
                failures[id] = e
            }
        }
        return DownloadOperationResult(failures)
    }
}

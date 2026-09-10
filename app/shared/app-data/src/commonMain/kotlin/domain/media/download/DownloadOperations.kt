/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.utils.logging.logger

data class DownloadOperationResult(
    val failures: Map<String, Throwable>,
    val rejectedIds: Set<String> = emptySet(),
)

/**
 * Runs independent downloads concurrently; each download accepts only one operation at a time.
 * [executionScope] owns accepted operations and must provide a serial dispatcher for admission and busy state.
 */
class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
    private val executionScope: CoroutineScope,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val logger = logger<DownloadOperations>()
    private val mutableBusyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds = mutableBusyIds.asStateFlow()

    enum class Action { Pause, Resume, Delete }

    /** Admission and busy state updates run in [executionScope], independently of the caller. */
    fun submit(ids: Set<String>, action: Action): Deferred<DownloadOperationResult> {
        val targets = ids.toSet()
        return executionScope.async {
            val rejected = targets.intersect(mutableBusyIds.value)
            val accepted = targets - rejected
            mutableBusyIds.value += accepted
            val tasks = accepted.map { id ->
                // Enter finally before dispatching work so cancellation always releases the ID.
                executionScope.async(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        withContext(workerDispatcher) { execute(id, action) }
                        id to null
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        logger.warn("Download operation $action failed for $id", e)
                        id to e
                    } finally {
                        mutableBusyIds.value -= id
                    }
                }
            }
            DownloadOperationResult(
                tasks.awaitAll().mapNotNull { (id, error) -> error?.let { id to it } }.toMap(),
                rejectedIds = rejected,
            )
        }
    }

    private suspend fun execute(id: String, action: Action) {
        val cache = downloadManager.findFirstDownload { it.cacheId == id } ?: return
        when (action) {
            Action.Pause -> if (cache.state.first() == MediaCacheState.IN_PROGRESS) cache.pause()
            Action.Resume -> if (cache.state.first() == MediaCacheState.PAUSED) cache.resume()
            Action.Delete -> deleteCache(cache)
        }
    }
}

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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.utils.logging.logger

data class DownloadOperationResult(val failures: Map<String, Throwable>)

/** Resolves IDs at execution time, so commands never rely on a stale row's status. */
class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
) {
    private val logger = logger<DownloadOperations>()
    enum class Action { Pause, Resume, Delete }

    private val mutex = Mutex()

    suspend fun execute(ids: Set<String>, action: Action): DownloadOperationResult = downloadManager.backgroundScope.async {
        mutex.withLock {
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
            DownloadOperationResult(failures)
        }
    }.await()
}

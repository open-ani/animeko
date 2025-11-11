/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.KtorHttpDownloader
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Parser
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * A persistent version of [me.him188.ani.utils.httpdownloader.KtorHttpDownloader] that automatically:
 * - Loads saved download states from [dao] on construction.
 * - Saves new/updated states whenever [_downloadStatesFlow] changes.
 */
class KtorPersistentHttpDownloader(
    private val dao: HttpCacheDownloadStateDao,
    client: ScopedHttpClient,
    fileSystem: FileSystem,
    baseSaveDir: Path,
    ioDispatcher: CoroutineContext = Dispatchers.IO_,
    clock: Clock = Clock.System,
    m3u8Parser: M3u8Parser = DefaultM3u8Parser,
    scope: CoroutineScope,
) : KtorHttpDownloader(
    client = client,
    fileSystem = fileSystem,
    baseSaveDir = baseSaveDir,
    clock = clock,
    m3u8Parser = m3u8Parser,
    parentScope = scope,
    ioDispatcher = ioDispatcher,
) {
    override suspend fun init() {
        super.init()
        restoreStates()
    }

    /**
     * Replaces the current in-memory map with data loaded from [dataStore], but does not resume them.
     * To resume downloads, call [resume] for each entry in the restored map.
     */
    private suspend fun restoreStates() {
        val savedList: List<DownloadState> = dao.getAll().first()
        stateMutex.withLock {
            val currentMap: MutableMap<DownloadId, DownloadEntry> = LinkedHashMap(savedList.size)

            savedList.forEach { st ->
                currentMap[st.downloadId] = DownloadEntry(
                    job = null,
                    state = st.copy(
                        status = when (val status = st.status) {
                            // 恢复时必须将原本的下载中状态设置为 PAUSED, 否则无法 resume.
                            DownloadStatus.INITIALIZING,
                            DownloadStatus.DOWNLOADING,
                            DownloadStatus.MERGING -> DownloadStatus.PAUSED

                            DownloadStatus.PAUSED,
                            DownloadStatus.COMPLETED,
                            DownloadStatus.FAILED,
                            DownloadStatus.CANCELED -> status
                        },
                    ),
                )
            }
            _downloadStatesFlow.value = currentMap.toPersistentMap()
            logger.info { "Restored ${currentMap.size} downloads from DataStore" }
        }
    }

    override fun onCreateDownloadState(state: DownloadState) {
        scope.launch {
            dao.upsert(state)
        }
    }

    override fun onUpdateDownloadState(downloadId: DownloadId, state: DownloadState) {
        scope.launch {
            dao.upsert(state)
        }
    }

    override fun onUpdateDownloadStatus(downloadId: DownloadId, status: DownloadStatus) {
        scope.launch {
            dao.updateStatus(downloadId, status)
        }
    }

    override fun onRemoveAllDownloads() {
        scope.launch {
            dao.deleteAll()
        }
    }

    private companion object {
        private val logger = logger<KtorPersistentHttpDownloader>()
    }
}
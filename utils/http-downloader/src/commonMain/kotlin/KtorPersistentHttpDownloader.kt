/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Parser
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

/**
 * A persistent version of [KtorHttpDownloader] that automatically:
 * - Loads saved download states from [dataStore] on construction.
 * - Saves new/updated states whenever [_downloadStatesFlow] changes.
 */
class KtorPersistentHttpDownloader(
    private val dataStore: DataStore<List<DownloadState>>,
    client: ScopedHttpClient,
    fileSystem: FileSystem,
    baseSaveDir: Path,
    computeDispatcher: CoroutineContext = Dispatchers.Default,
    ioDispatcher: CoroutineContext = Dispatchers.IO_,
    clock: Clock = Clock.System,
    m3u8Parser: M3u8Parser = DefaultM3u8Parser,
) : KtorHttpDownloader(
    client = client,
    fileSystem = fileSystem,
    baseSaveDir = baseSaveDir,
    computeDispatcher = computeDispatcher,
    ioDispatcher = ioDispatcher,
    clock = clock,
    m3u8Parser = m3u8Parser,
) {
    override suspend fun init() {
        super.init()
        scope.launch(
            CoroutineName("KtorPersistentHttpDownloader.saver"),
            start = CoroutineStart.UNDISPATCHED, // register job now
        ) {
            downloadStatesFlow.buffer(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST, // 当来不及保存时, 不需要保存中间状态
            ).collect { states ->
                dataStore.updateData { states } // override the entire list
            }
        }
        restoreStates()
    }

    /**
     * Replaces the current in-memory map with data loaded from [dataStore], but does not resume them.
     * To resume downloads, call [KtorHttpDownloader.resume] for each entry in the restored map.
     */
    private suspend fun restoreStates() {
        val savedList: List<DownloadState> = dataStore.data.first()
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
            _downloadStatesFlow.value = currentMap
            logger.info { "Restored ${currentMap.size} downloads from DataStore" }
        }
    }

    private companion object {
        private val logger = logger<KtorPersistentHttpDownloader>()
    }
}

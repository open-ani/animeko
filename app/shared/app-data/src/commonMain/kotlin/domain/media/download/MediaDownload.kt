/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * 对单个下载执行的操作.
 */
enum class DownloadOperation {
    Pause,
    Resume,
    Delete,
}

/**
 * 下载某一时刻的不可变快照.
 */
data class DownloadSnapshot(
    val id: String,
    val metadata: MediaCacheMetadata,
    val status: MediaCacheState,
    val progress: Progress,
    val totalSize: FileSize,
    /**
     * 每秒字节数.
     */
    val downloadSpeed: FileSize,
    val canPlay: Boolean,
    val mediaSourceId: String,
    val engineKey: MediaCacheEngineKey,
    /**
     * 已排队或正在执行的操作, `null` 表示空闲.
     */
    val operation: DownloadOperation?,
) {
    val isBusy: Boolean get() = operation != null
}

/**
 * 一个持久化的视频下载, 与 [MediaCache] 一一对应, 由 [MediaDownloadManager] 创建并保持实例稳定.
 */
class MediaDownload internal constructor(
    val cache: MediaCache,
    val storage: MediaCacheStorage,
    sharingScope: CoroutineScope,
) {
    val id: String = cache.cacheId
    val metadata: MediaCacheMetadata get() = cache.metadata
    val origin: Media get() = cache.origin
    val engineKey: MediaCacheEngineKey get() = storage.engine.engineKey

    private val scope = sharingScope.childScope()

    private val queuedOperation = MutableStateFlow<DownloadOperation?>(null)

    /**
     * 已排队或正在执行的操作, `null` 表示空闲. 由 [DownloadOperations] 通过 [claim] 与 [release] 维护.
     */
    val operation: StateFlow<DownloadOperation?> = queuedOperation.asStateFlow()

    /**
     * 共享的快照流: 进度与速度每秒最多更新一次, 状态、可播放性与操作立即反映; 最后一个订阅者离开 5 秒后停止.
     * 上游抛出异常时发出 [MediaCacheState.FAILED] 快照, 下次重新订阅时重试.
     */
    val snapshot: Flow<DownloadSnapshot> = flow {
        coroutineScope {
            // 文件统计只订阅一次.
            val fileStats = cache.fileStats.shareIn(this, SharingStarted.Lazily, replay = 1)
            val downloadSpeed = fileStats
                .map { stats -> stats.downloadedBytes.takeUnless { it.isUnspecified }?.inBytes ?: 0L }
                .averageRate()
            val transfer = combine(fileStats, downloadSpeed) { stats, speed -> stats to speed }
                .sampleWithInitial(1.seconds)
            emitAll(
                combine(
                    transfer,
                    cache.state,
                    cache.canPlay,
                    queuedOperation,
                ) { (stats, speed), state, canPlay, operation ->
                    DownloadSnapshot(
                        id = id,
                        metadata = metadata,
                        status = state,
                        progress = stats.downloadProgress,
                        totalSize = stats.totalSize,
                        downloadSpeed = speed.bytes,
                        canPlay = canPlay,
                        mediaSourceId = origin.mediaSourceId,
                        engineKey = engineKey,
                        operation = operation,
                    )
                },
            )
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        logger.warn(e) { "Snapshot of download $id failed, reporting it as FAILED" }
        emit(
            DownloadSnapshot(
                id = id,
                metadata = metadata,
                status = MediaCacheState.FAILED,
                progress = Progress.Unspecified,
                totalSize = FileSize.Unspecified,
                downloadSpeed = FileSize.Unspecified,
                canPlay = false,
                mediaSourceId = origin.mediaSourceId,
                engineKey = engineKey,
                operation = queuedOperation.value,
            ),
        )
    }.distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SNAPSHOT_STOP_TIMEOUT_MILLIS), replay = 1)

    /**
     * 只暂停 [MediaCacheState.IN_PROGRESS] 的下载.
     */
    suspend fun pause() {
        if (cache.state.first() == MediaCacheState.IN_PROGRESS) cache.pause()
    }

    /**
     * 只继续 [MediaCacheState.PAUSED] 的下载. 这是用户操作, 自动保存的记录由此转为持久下载.
     */
    suspend fun resume() {
        if (cache.state.first() == MediaCacheState.PAUSED) cache.resumeByUser()
    }

    /**
     * 把 [operation] 标记为已排队. 已有操作排队或执行中时返回 `false`.
     */
    internal fun claim(operation: DownloadOperation): Boolean = queuedOperation.compareAndSet(null, operation)

    internal fun release() {
        queuedOperation.value = null
    }

    /**
     * 释放快照共享协程, 记录离开 [MediaDownloadManager.downloads] 时由管理器调用.
     */
    internal fun close() {
        scope.cancel()
    }

    override fun toString(): String = "MediaDownload(id=$id, engine=${engineKey.key})"

    private companion object {
        private const val SNAPSHOT_STOP_TIMEOUT_MILLIS = 5_000L
        private val logger = logger<MediaDownload>()
    }
}

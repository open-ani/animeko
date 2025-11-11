/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage
import me.him188.ani.app.tools.Progress
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.length

/**
 * 存储于本地文件的 [MediaCache], [MediaCacheEngine] 在[恢复][MediaCacheEngine.restore] 时可以根据其参数
 * [Media] 和 [MediaCacheMetadata] 判断是否需要返回此类型的 MediaCache.
 *
 * [MediaCacheStorage] 可能会特殊处理此类型的 MediaCache, 例如 [TorrentMediaCacheStorage].
 *
 * 调用 [LocalFileMediaCache] 的 [pause], [close] 和 [resume] 没有效果.
 * 调用 [closeAndDeleteFiles] 默认后会删除 [file] 对应的文件, 通过 [onCloseAndDeleteFiles] 来实现其他逻辑.
 * (例如 [TorrentMediaCacheEngine] 引擎需要删除对应的 fastresume 文件和其文件夹).
 *
 * @see MediaCacheEngine.restore
 */
class LocalFileMediaCache(
    override val origin: Media,
    override val metadata: MediaCacheMetadata,
    val file: SystemPath,
    uploadedSize: FileSize = 0.bytes,
    private val backedMediaSourceId: String = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
    private val onCloseAndDeleteFiles: LocalFileMediaCache.(SystemPath) -> Unit = { file.deleteRecursively() },
) : MediaCache {
    override val state: Flow<MediaCacheState> = MutableStateFlow(MediaCacheState.IN_PROGRESS)
    override val canPlay: Flow<Boolean> = MutableStateFlow(true)

    private val fileSize = file.length().bytes

    override val fileStats: Flow<MediaCache.FileStats> = MutableStateFlow(
        MediaCache.FileStats(fileSize, fileSize, Progress.fromZeroToOne(1f)),
    )

    override val sessionStats: Flow<MediaCache.SessionStats> = MutableStateFlow(
        MediaCache.SessionStats(
            totalSize = fileSize,
            downloadedBytes = fileSize,
            downloadSpeed = 0.bytes,
            uploadedBytes = uploadedSize,
            uploadSpeed = 0.bytes,
            downloadProgress = Progress.fromZeroToOne(1f),
        ),
    )

    private val _isDeleted = MutableStateFlow(false)
    override val isDeleted: StateFlow<Boolean> = _isDeleted

    override suspend fun getCachedMedia(): CachedMedia {
        return CachedMedia(origin, backedMediaSourceId, ResourceLocation.LocalFile(file.absolutePath))
    }

    override suspend fun pause() {

    }

    override suspend fun close() {

    }

    override suspend fun resume() {

    }

    override suspend fun closeAndDeleteFiles() {
        _isDeleted.value = true
        onCloseAndDeleteFiles(file)
    }
}
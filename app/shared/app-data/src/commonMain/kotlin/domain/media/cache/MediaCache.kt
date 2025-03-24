/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue

/**
 * 表示一个进行中的 [Media] 缓存.
 *
 * [MediaCache] 有状态,
 *
 * 可能有用的属性:
 * - 该媒体的实际存储位置: [origin] 的 [Media.download]
 */
interface MediaCache {
    /**
     * 唯一缓存 id
     */
    val cacheId: String
        get() {
            return calculateCacheId(
                origin.mediaId,
                metadata,
            )
        }

    /**
     * Original media that is being cached.
     */
    val origin: Media

    /**
     * 缓存元数据.
     */
    val metadata: MediaCacheMetadata

    val state: Flow<MediaCacheState>

    /**
     * Returns the [CachedMedia] instance for this cache.
     * The instance is cached so this function will immediately return the cached instance after the first successful call.
     */
    suspend fun getCachedMedia(): CachedMedia

    /**
     * 缓存
     */
    fun isValid(): Boolean // todo: deprecate this

    /**
     * @see TorrentFileEntry.Stats
     * @see TorrentMediaCacheEngine.TorrentMediaCache.fileStats
     */
    data class FileStats(
        val totalSize: FileSize,
        /**
         * 已经下载成功的字节数.
         *
         * @return `0L`..[TorrentFileEntry.length]
         * @see TorrentFileEntry.Stats.downloadedBytes
         */
        val downloadedBytes: FileSize,
        /**
         * 已完成比例.
         *
         * @return `0f`..`1f`, 在未开始下载时, 该值为 [Progress.Unspecified].
         */
        val downloadProgress: Progress = if (totalSize.isUnspecified || downloadedBytes.isUnspecified) {
            Progress.Unspecified
        } else {
            if (totalSize.inBytes == 0L) {
                0f.toProgress()
            } else {
                (downloadedBytes.inBytes.toFloat() / totalSize.inBytes).toProgress()
            }
        },

        // 没有上传信息
        // hint: 要获取下载速度: downloadedBytes.sampleWithInitial(1000).averageRate()
    ) {
        /**
         * 下载是否已经完成.
         *
         * 在刚创建时, [MediaCache] 可能需要时间扫描已经下载的文件状态.
         * 在扫描完成前, 即使文件已经下载成功, 该值也为 `false`.
         */
        val isDownloadFinished: Boolean get() = downloadProgress.isFinished

        companion object {
            val Unspecified =
                FileStats(
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    Progress.Unspecified,
                )
        }
    }

    /**
     * 当前文件的下载状态.
     */
    val fileStats: Flow<FileStats>

    /**
     * 所属的 [Media] 的下载状态, 也就是会包含其他剧集的下载状态.
     *
     * 注意, 所有属性都需要检查 Unspecified
     *
     * @see sessionStats
     * @see TorrentSession.Stats
     * @see TorrentMediaCacheEngine.TorrentMediaCache.sessionStats
     */
    data class SessionStats(
        /**
         * 所有请求了的文件的大小
         */
        val totalSize: FileSize,
        /**
         * 已经下载成功的字节数.
         *
         * @return `0L`..[TorrentFileEntry.length]
         */
        val downloadedBytes: FileSize,
        /**
         * 下载速度, 每秒. 对于不支持下载的缓存, 该值为 [FileSize.Zero].
         *
         * 为单个文件的下载速度.
         *
         * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
         * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
         */
        val downloadSpeed: FileSize,
        /**
         * 已经上传成功的字节数.
         *
         * @return `0L`..INF
         */
        val uploadedBytes: FileSize,
        /**
         * 上传速度, 每秒. 对于不支持上传的缓存, 该值为 [FileSize.Zero].
         *
         * 注意, 这实际上是整个 media 的下载速度.
         *
         * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
         * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
         */
        val uploadSpeed: FileSize,
        /**
         * Bytes per second.
         */
        val downloadProgress: Progress,
    ) {
        companion object {
            val Unspecified =
                SessionStats(
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    Progress.Unspecified,
                )
        }
    }

    /**
     * 该文件所属的 [Media] 的下载状态, 也就是会包含其他剧集的下载状态.
     *
     * 因为每个 [MediaCache] 只对应单个文件 (剧集), 而 [Media] 可能包含多个文件 (剧集).
     */
    val sessionStats: Flow<SessionStats>

    /**
     * 请求暂停下载.
     *
     * 若当前状态不支持暂停, 例如已经被删除, 则忽略本次请求, 不会抛出异常.
     */
    suspend fun pause()

    /**
     * 从引擎中关闭此缓存任务, 关闭后不能再开启.
     *
     * 此函数会等待此资源完全关闭并释放资源后返回.
     *
     * 若当前状态不支持暂停, 例如已经被删除, 则忽略本次请求, 不会抛出异常.
     */
    suspend fun close()

    /**
     * 请求恢复下载.
     *
     * 若当前状态不支持恢复, 例如已经被删除, 则忽略本次请求, 不会抛出异常.
     */
    suspend fun resume()

    /**
     * 该缓存的文件是否已经被删除. 删除后不可恢复.
     */
    val isDeleted: StateFlow<Boolean>

    /**
     * 尝试删除此 [MediaCache] 所涉及的文件.
     *
     * 注意! 你很可能需要使用 [MediaCacheManager.deleteCache]. 因为单独 [MediaCache.closeAndDeleteFiles] 并不会从 storage 中删除.
     *
     * 此函数必须关闭所有使用的资源, 清理潜在的缓存文件, 且不得抛出异常 (除非是 [CancellationException]).
     */
    suspend fun closeAndDeleteFiles()

    companion object {
        fun calculateCacheId(originMediaId: String, metadata: MediaCacheMetadata): String {
            val hash = (originMediaId.hashCode() * 31
                    + metadata.subjectId.hashCode() * 31
                    + metadata.episodeId.hashCode()).absoluteValue.toString()
            val subjectName = metadata.subjectNames.firstOrNull() ?: metadata.subjectId
            fun removeSpecials(value: String): String {
                return value.replace(Regex("""[-\\|/.,;'\[\]{}()=_ ~!@#$%^&*]"""), "")
            }
            return "${removeSpecials(subjectName).take(8)}-$hash"
        }
    }
}

suspend inline fun MediaCache.isFinished(): Boolean = sessionStats.first().downloadProgress.isFinished

enum class MediaCacheState {
    IN_PROGRESS,
    PAUSED,
}

open class TestMediaCache(
    val media: CachedMedia,
    override val metadata: MediaCacheMetadata,
    override val sessionStats: MutableStateFlow<MediaCache.SessionStats> =
        MutableStateFlow(
            MediaCache.SessionStats(
                0.bytes,
                0.bytes,
                0.bytes,
                0.bytes,
                0.bytes,
                0f.toProgress(),
            ),
        ),
    override val fileStats: MutableStateFlow<MediaCache.FileStats> =
        MutableStateFlow(
            MediaCache.FileStats(
                FileSize.Unspecified,
                FileSize.Unspecified,
                0f.toProgress(),
            ),
        ),
) : MediaCache {
    override val origin: Media get() = media.origin
    override val state: MutableStateFlow<MediaCacheState> = MutableStateFlow(
        MediaCacheState.IN_PROGRESS,
    )

    override suspend fun getCachedMedia(): CachedMedia = media
    override fun isValid(): Boolean = true

    private val resumeCalled = atomic(0)

    @TestOnly
    fun getResumeCalled() = resumeCalled.value

    override suspend fun pause() {
        println("pause")
    }

    override suspend fun close() {
        println("close")
    }

    override suspend fun resume() {
        resumeCalled.incrementAndGet()
        println("resume")
    }

    override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun closeAndDeleteFiles() {
        println("delete called")
        isDeleted.value = true
    }
}

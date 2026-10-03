/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation

/**
 * 下载测试用的缓存记录. 记录 [pause] 与 [resume] 的调用次数, 并允许在状态改变前插入挂起逻辑, 以模拟耗时或失败的操作.
 */
internal class DownloadTestCache(
    media: CachedMedia,
    metadata: MediaCacheMetadata,
) : TestMediaCache(media, metadata) {
    override val canPlay = MutableStateFlow(true)

    var pauseCalls = 0
        private set
    var resumeCalls = 0
        private set

    /**
     * 在 [pause] 把状态改为 [me.him188.ani.app.domain.media.cache.MediaCacheState.PAUSED] 之前调用.
     */
    var onPause: suspend () -> Unit = {}

    /**
     * 在 [resume] 把状态改为 [me.him188.ani.app.domain.media.cache.MediaCacheState.IN_PROGRESS] 之前调用.
     */
    var onResume: suspend () -> Unit = {}

    override suspend fun pause() {
        pauseCalls++
        onPause()
        super.pause()
    }

    override suspend fun resume() {
        resumeCalls++
        onResume()
        super.resume()
    }
}

/**
 * [fileStats] 的前 [failures] 次收集抛出 [IllegalStateException], 之后转发 [delegate] 的统计; 其余成员委托给 [delegate].
 * 用于模拟引擎统计读取失败.
 */
internal class FailingStatsCache(
    private val delegate: DownloadTestCache,
    private val failures: Int = Int.MAX_VALUE,
) : MediaCache by delegate {
    /**
     * [fileStats] 被收集的次数.
     */
    var attempts = 0
        private set

    override val fileStats: Flow<MediaCache.FileStats> = flow {
        attempts++
        check(attempts > failures) { "file stats unavailable" }
        emitAll(delegate.fileStats)
    }
}

internal fun testMetadata(episodeId: Int, subjectId: Int = 1): MediaCacheMetadata = MediaCacheMetadata(
    subjectId = subjectId.toString(),
    episodeId = episodeId.toString(),
    subjectNames = listOf("Subject"),
    episodeSort = EpisodeSort(episodeId),
    episodeName = "Episode $episodeId",
)

/**
 * @param id 决定资源 id, 也是默认的剧集 id. [id] 与 [subjectId] 都相同的两条记录拥有相同的 [MediaCache.cacheId].
 * @param episodeId 剧集 id, 用于为同一集创建多条不同资源的记录.
 */
internal fun testDownload(
    id: Int,
    subjectId: Int = 1,
    range: EpisodeRange? = null,
    episodeId: Int = id,
): DownloadTestCache {
    val media = TestMediaList.first().copy(mediaId = "media-$id", episodeRange = range)
    return DownloadTestCache(
        CachedMedia(media, "test-storage", ResourceLocation.LocalFile("/download-$id")),
        testMetadata(episodeId, subjectId),
    )
}

/**
 * 内存中的 [MediaCacheStorage]. [listFlow] 可直接修改; [cache] 的行为由 [create] 决定; 删除前先调用 [onDelete], 再从 [listFlow] 移除.
 */
internal class DownloadTestStorage(
    override val engine: MediaCacheEngine = DummyMediaCacheEngine("test-storage"),
    override val mediaSourceId: String = "test-storage",
) : MediaCacheStorage {
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
    override val stats = MutableStateFlow(MediaStats.Zero)

    var create: suspend (Media, MediaCacheMetadata, EpisodeMetadata) -> MediaCache = { _, _, _ -> error("Not used") }
    var onDelete: suspend (MediaCache) -> Unit = {}

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = create(media, metadata, episodeMetadata)

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val selected = listFlow.value.firstOrNull(predicate) ?: return false
        onDelete(selected)
        listFlow.value -= selected
        return true
    }

    override fun close() = Unit
}

/**
 * 指定 [engineKey][MediaCacheEngine.engineKey] 与 [supports][MediaCacheEngine.supports] 结果的引擎, 用于存储选择测试.
 */
internal fun testDownloadEngine(key: MediaCacheEngineKey, supports: Boolean = true): MediaCacheEngine =
    object : MediaCacheEngine by DummyMediaCacheEngine("test-storage") {
        override val engineKey: MediaCacheEngineKey = key
        override fun supports(media: Media): Boolean = supports
    }

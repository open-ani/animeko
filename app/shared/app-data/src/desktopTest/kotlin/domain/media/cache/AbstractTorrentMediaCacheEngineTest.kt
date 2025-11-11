/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.persistent.database.dao.createMemoryTorrentCacheInfoDao
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.anitorrent.test.TestAnitorrentTorrentDownloader
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

abstract class AbstractTorrentMediaCacheEngineTest {
    companion object {
        const val CACHE_MEDIA_SOURCE_ID = "local-test"
        val CacheEngineKey = MediaCacheEngineKey("test-media-cache-engine")
    }

    @TempDir
    private lateinit var dir: File
    protected val torrentInfoDatabase = createMemoryTorrentCacheInfoDao()

    protected lateinit var cacheEngine: TorrentMediaCacheEngine
    protected lateinit var torrentEngine: TorrentEngine

    protected val testMedia = createTestDefaultMedia(
        mediaId = "dmhy.2",
        mediaSourceId = "dmhy",
        originalTitle = "夜晚的水母不会游泳 02 测试剧集",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1724493292758,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf("CHT"),
            resolution = "1080P",
            alliance = "北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组",
            size = 233.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    )

    private val client = createDefaultHttpClient()

    protected fun createTestAnitorrentEngine(coroutineContext: CoroutineContext): AnitorrentEngine {
        return AnitorrentEngine(
            config = flowOf(AnitorrentConfig()),
            client = client.asScopedHttpClient(),
            peerFilterSettings = flowOf(PeerFilterSettings.Empty),
            saveDir = dir.toKtPath().inSystem,
            parentCoroutineContext = coroutineContext,
            anitorrentFactory = TestAnitorrentTorrentDownloader.Factory,
        )
    }

    protected fun TestScope.createEngine(
        engine: TorrentEngine = createTestAnitorrentEngine(coroutineContext),
        onDownloadStarted: suspend (session: AnitorrentDownloadSession) -> Unit = {},
    ): TorrentMediaCacheEngine {
        this.coroutineContext.job.invokeOnCompletion {
            client.close()
        }
        return TorrentMediaCacheEngine(
            CACHE_MEDIA_SOURCE_ID,
            CacheEngineKey,
            engine.also { torrentEngine = it },
            engineAccess = AlwaysUseTorrentEngineAccess,
            dao = torrentInfoDatabase,
            flowDispatcher = coroutineContext[ContinuationInterceptor]!!,
            baseSaveDirProvider = object : MediaSaveDirProvider {
                override val saveDir: String = dir.absolutePath
            },
            onDownloadStarted = { onDownloadStarted(it as AnitorrentDownloadSession) },
        ).also { cacheEngine = it }
    }
}
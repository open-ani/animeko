/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DirectoryMediaCacheStorage
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.anitorrent.test.TestAnitorrentTorrentDownloader
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.unwrapCached
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.io.writeText
import me.him188.ani.utils.serialization.putAll
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @see DirectoryMediaCacheStorage
 */
class DirectoryMediaCacheStorageTest {
    companion object {
        private const val CACHE_MEDIA_SOURCE_ID = "local-test"
    }

    @TempDir
    private lateinit var dir: File
    private val metadataDir by lazy { dir.toKtPath().inSystem }
    private val storages = mutableListOf<DirectoryMediaCacheStorage>()
    private lateinit var cacheEngine: TorrentMediaCacheEngine
    private lateinit var torrentEngine: TorrentEngine
    private suspend fun torrentDownloader(): TestAnitorrentTorrentDownloader =
        torrentEngine.getDownloader() as TestAnitorrentTorrentDownloader

    private val json = Json {
        prettyPrint = true
    }

    private fun TestScope.createEngine(
        onDownloadStarted: suspend (session: AnitorrentDownloadSession) -> Unit = {},
    ): TorrentMediaCacheEngine {
        return TorrentMediaCacheEngine(
            CACHE_MEDIA_SOURCE_ID,
            AnitorrentEngine(
                config = flowOf(AnitorrentConfig()),
                proxyConfig = flowOf(null),
                peerFilterSettings = flowOf(PeerFilterSettings.Empty),
                saveDir = dir.toKtPath().inSystem,
                parentCoroutineContext = coroutineContext,
                anitorrentFactory = TestAnitorrentTorrentDownloader.Factory,
            ).also { torrentEngine = it },
            flowDispatcher = coroutineContext[ContinuationInterceptor]!!,
            onDownloadStarted = { onDownloadStarted(it as AnitorrentDownloadSession) },
        )
    }

    private val media = createTestDefaultMedia(
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

    private fun cleanup() {
        storages.forEach { it.close() }
        storages.clear()
    }

    private fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        timeout: Duration = 5.seconds,
        testBody: suspend TestScope.() -> Unit
    ) = kotlinx.coroutines.test.runTest(context, timeout) {
        try {
            testBody()
        } finally {
            cleanup()
        }
    }

    private fun TestScope.createStorage(engine: TorrentMediaCacheEngine = createEngine()): DirectoryMediaCacheStorage {
        return DirectoryMediaCacheStorage(
            CACHE_MEDIA_SOURCE_ID,
            metadataDir,
            engine.also { cacheEngine = it },
            this.coroutineContext,
            clock = object : Clock {
                override fun now(): Instant {
                    return Instant.fromEpochMilliseconds(1725107383853)
                }
            },
        ).also {
            storages.add(it)
        }
    }

    private suspend fun TorrentMediaCacheEngine.TorrentMediaCache.getSession() =
        lazyFileHandle.state.first()!!.session as AnitorrentDownloadSession

    private fun amendJsonString(
        @Language("json") string: String,
        block: JsonObjectBuilder.(origin: JsonObject) -> Unit
    ): String {
        json.decodeFromString(JsonObject.serializer(), string).let {
            return json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    putAll(it)
                    block(it)
                },
            )
        }
    }


    private fun mediaCacheMetadata() = MediaCacheMetadata(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "1",
        subjectNames = emptyList(),
        episodeSort = EpisodeSort("02"),
        episodeEp = EpisodeSort("02"),
        episodeName = "测试剧集",
    )

    ///////////////////////////////////////////////////////////////////////////
    // simple create, restore, find
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `create cache then get from listFlow`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache
        assertSame(cache, storage.listFlow.first().single())
    }

    @Test
    fun `create cache saves metadata`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        metadataDir.resolve("${cache.cacheId}.metadata").run {
            assertEquals(true, exists())
        }

        assertSame(cache, storage.listFlow.first().single())
    }

    @Test
    fun `create same cache twice`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache
        assertSame(cache, storage.listFlow.first().single())
        assertSame(cache, storage.cache(media, mediaCacheMetadata(), resume = false))
        assertSame(cache, storage.listFlow.first().single())
    }

    @Test
    fun `create and delete`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache
        val metadataFile = metadataDir.resolve("${cache.cacheId}.metadata")
        metadataFile.run {
            assertEquals(true, exists())
        }

        assertNotNull(cache.lazyFileHandle.state.first()).run {
            assertNotNull(handle)
            assertNotNull(entry)
        }

        assertEquals(cache, storage.listFlow.first().single())
        assertEquals(true, storage.delete(cache))
        metadataFile.run {
            assertEquals(false, exists())
        }
        assertEquals(null, storage.listFlow.first().firstOrNull())
    }

    ///////////////////////////////////////////////////////////////////////////
    // restore
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `restorePersistedCaches - nothing`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )
        storage.restorePersistedCaches()
        assertEquals(0, storage.listFlow.first().size)
    }

    /**
     * 读取 3.7.0 版本的缓存数据
     */
    @Test
    fun `restorePersistedCaches - v3_7`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cacheId = MediaCache.calculateCacheId(media.mediaId, metadata)
        val metadataFile = metadataDir.resolve("${cacheId}.metadata")
        metadataFile.writeText(
            amendJsonString(
                """
                    {"origin":{"type":"me.him188.ani.datasources.api.DefaultMedia","mediaId":"dmhy.2","mediaSourceId":"dmhy","originalUrl":"https://example.com/1","download":{"type":"me.him188.ani.datasources.api.topic.ResourceLocation.MagnetLink","uri":"magnet:?xt=urn:btih:1"},"originalTitle":"夜晚的水母不会游泳 02 测试剧集","publishedTime":1723908945976,"properties":{"subtitleLanguageIds":["CHT"],"resolution":"1080P","alliance":"北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组","size":244318208},"episodeRange":{"type":"me.him188.ani.datasources.api.topic.EpisodeRange.Single","value":{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":2.0}}},"metadata":{"episodeId":"1231231","subjectNames":[],"episodeSort":{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":2.0},"episodeName":"测试剧集","extra":{"torrentData":"7b2264617461223a7b2274797065223a224d61676e6574557269222c22757269223a226d61676e65743a3f78743d75726e3a627469683a31227d2c2268747470546f7272656e7446696c6550617468223a6e756c6c7d",
                    "torrentCacheDir":""}}}
                """.trimIndent(),
            ) {
                put("torrentCacheDir", dir.resolve("pieces/2071812470").absolutePath)
            },
        )

        storage.restorePersistedCaches()
        assertEquals(1, storage.listFlow.first().size)
        val cache = storage.listFlow.first().single()
        assertEquals("0-500562845", cache.cacheId)
    }

    /**
     * 检查序列化后的结果
     */
    @Test
    fun `serialize - latest`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cacheId = MediaCache.calculateCacheId(media.mediaId, metadata)
        val metadataFile = metadataDir.resolve("${cacheId}.metadata")
        storage.cache(media, metadata, resume = false)

        assertEquals(
            """
                {
                    "origin": {
                        "type": "me.him188.ani.datasources.api.DefaultMedia",
                        "mediaId": "dmhy.2",
                        "mediaSourceId": "dmhy",
                        "originalUrl": "https://example.com/1",
                        "download": {
                            "type": "me.him188.ani.datasources.api.topic.ResourceLocation.MagnetLink",
                            "uri": "magnet:?xt=urn:btih:1"
                        },
                        "originalTitle": "夜晚的水母不会游泳 02 测试剧集",
                        "publishedTime": 1724493292758,
                        "properties": {
                            "subtitleLanguageIds": [
                                "CHT"
                            ],
                            "resolution": "1080P",
                            "alliance": "北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组",
                            "size": 244318208
                        },
                        "episodeRange": {
                            "type": "me.him188.ani.datasources.api.topic.EpisodeRange.Single",
                            "value": {
                                "type": "me.him188.ani.datasources.api.EpisodeSort.Normal",
                                "number": 2.0
                            }
                        }
                    },
                    "metadata": {
                        "subjectId": "1",
                        "episodeId": "1",
                        "subjectNameCN": "1",
                        "subjectNames": [],
                        "episodeSort": {
                            "type": "me.him188.ani.datasources.api.EpisodeSort.Normal",
                            "number": 2.0
                        },
                        "episodeName": "测试剧集",
                        "extra": {
                            "creationTime": "1725107383853",
                            "torrentData": "7b2264617461223a7b2274797065223a224d61676e6574557269222c22757269223a226d61676e65743a3f78743d75726e3a627469683a31227d2c2268747470546f7272656e7446696c6550617468223a6e756c6c7d",
                            "torrentCacheDir": "${dir.resolve("pieces/2071812470").absolutePath.replace("\\", "\\\\")}"
                        }
                    }
                }
            """.trimIndent(),
            json.encodeToString(
                JsonObject.serializer(),
                Json.decodeFromString(
                    JsonObject.serializer(),
                    metadataFile.readText(),
                ),
            ),
        )
    }

    /**
     * 忽略错误文件
     */
    @Test
    fun `restorePersistedCaches - broken metadata file`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cacheId = MediaCache.calculateCacheId(media.mediaId, metadata)
        val metadataFile = metadataDir.resolve("${cacheId}.metadata")
        metadataFile.writeText("broken file")

        storage.restorePersistedCaches()
        assertEquals(0, storage.listFlow.first().size)
    }

    /**
     * 只考虑 `*.metadata` 文件
     */
    @Test
    fun `restorePersistedCaches - ignore non-metadata files`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cacheId = MediaCache.calculateCacheId(media.mediaId, metadata)
        val metadataFile = metadataDir.resolve("${cacheId}.0")
        metadataFile.writeText(
            // A valid metadata
            amendJsonString(
                """
                    {"origin":{"type":"me.him188.ani.datasources.api.DefaultMedia","mediaId":"dmhy.2","mediaSourceId":"dmhy","originalUrl":"https://example.com/1","download":{"type":"me.him188.ani.datasources.api.topic.ResourceLocation.MagnetLink","uri":"magnet:?xt=urn:btih:1"},"originalTitle":"夜晚的水母不会游泳 02 测试剧集","publishedTime":1723908945976,"properties":{"subtitleLanguageIds":["CHT"],"resolution":"1080P","alliance":"北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组","size":244318208},"episodeRange":{"type":"me.him188.ani.datasources.api.topic.EpisodeRange.Single","value":{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":2.0}}},"metadata":{"episodeId":"1231231","subjectNames":[],"episodeSort":{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":2.0},"episodeName":"测试剧集","extra":{"torrentData":"7b2264617461223a7b2274797065223a224d61676e6574557269222c22757269223a226d61676e65743a3f78743d75726e3a627469683a31227d2c2268747470546f7272656e7446696c6550617468223a6e756c6c7d",
                    "torrentCacheDir":""}}}
                """.trimIndent(),
            ) {
                put("torrentCacheDir", dir.resolve("pieces/2071812470").absolutePath)
            },
        )

        storage.restorePersistedCaches()
        assertEquals(0, storage.listFlow.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // cacheMediaSource
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `query cacheMediaSource`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cache =
            storage.cache(media, metadata, resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        assertEquals(
            cache.getCachedMedia().unwrapCached(),
            storage.cacheMediaSource.fetch(
                MediaFetchRequest(
                    subjectId = "1",
                    episodeId = "1",
                    subjectNames = metadata.subjectNames,
                    episodeSort = metadata.episodeSort,
                    episodeName = metadata.episodeName,
                ),
            ).results.toList().single().media.unwrapCached(),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // metadata
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `cached media id`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        assertNotNull(cache.lazyFileHandle.state.first()).run {
            assertNotNull(handle)
        }

        val cachedMedia = cache.getCachedMedia()
        assertEquals("$CACHE_MEDIA_SOURCE_ID:${media.mediaId}", cachedMedia.mediaId)
        assertEquals(CACHE_MEDIA_SOURCE_ID, cachedMedia.mediaSourceId)
        assertEquals(media, cachedMedia.origin)
    }

    ///////////////////////////////////////////////////////////////////////////
    // others
    ///////////////////////////////////////////////////////////////////////////

    private suspend fun DirectoryMediaCacheStorage.getUploaded() =
        stats.map { it.uploaded }.first()
}

private suspend fun <T> MutableSharedFlow<T>.emit(
    value: (T & Any).() -> T
) = emit(value(replayCache.first()!!))

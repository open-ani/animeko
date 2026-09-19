/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.torrent.pikpak.CloudReadiness
import me.him188.ani.torrent.pikpak.PartialListing
import org.openani.mediamp.io.SeekableInput
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class TorrentMediaResolverFallbackTest {
    private val media = createTestDefaultMedia(
        mediaId = "1",
        mediaSourceId = "test",
        originalUrl = "https://example.com/1",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalTitle = "test 01",
        publishedTime = 0,
        properties = createTestMediaProperties(),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.BitTorrent,
    )

    private val episode = EpisodeMetadata("test 01", EpisodeSort(1), EpisodeSort(1))

    // The open runs on the test dispatcher so the budget is measured on runTest's virtual clock
    // rather than racing a real IO thread.
    private fun TestScope.resolver(
        engine: TorrentEngine,
        fallback: MediaResolver? = TestUniversalMediaResolver,
        cloudOpenTimeout: Duration = 50.milliseconds,
    ) = TorrentMediaResolver(
        engine,
        AlwaysUseTorrentEngineAccess,
        fallback,
        cloudOpenTimeout,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `falls back when the engine cannot be created`() = runTest {
        val provider = resolver(FakeTorrentEngine { throw IllegalStateException("bad credentials") })
            .resolve(media, episode)
        assertIs<TestMediaDataProvider>(provider)
    }

    @Test
    fun `falls back when opening fails`() = runTest {
        val provider = resolver(FakeTorrentEngine { FakeDownloader { throw IllegalStateException("quota exceeded") } })
            .resolve(media, episode)
        val data = provider.open(this)
        assertEquals("https://example.com", assertIs<UriMediaData>(data).uri)
    }

    @Test
    fun `does not fall back when no file matches`() = runTest {
        val provider = resolver(FakeTorrentEngine { FakeDownloader { FakeSession } })
            .resolve(media, episode)
        val e = assertFailsWith<MediaSourceOpenException> { provider.open(this) }
        assertEquals(OpenFailures.NO_MATCHING_FILE, e.reason)
    }

    @Test
    fun `falls back when the cloud is not ready for the selected file`() = runTest {
        val entry = FakeCloudEntry("test 01.mp4") { throw IllegalStateException("bad credentials") }
        val provider = resolver(FakeTorrentEngine { FakeDownloader { FakeSessionOf(entry) } })
            .resolve(media, episode)
        val data = provider.open(this)
        assertEquals("https://example.com", assertIs<UriMediaData>(data).uri)
        assertEquals(1, entry.closedHandles, "the handle opened before the check must be released")
    }

    // A broken network makes the SDK retry rather than fail, so the cloud check can hang for minutes.
    // withTimeout would report a CancellationException, which resolve rethrows past the fallback.
    @Test
    fun `falls back when the cloud never settles`() = runTest {
        val entry = FakeCloudEntry("test 01.mp4") { awaitCancellation() }
        val provider = resolver(FakeTorrentEngine { FakeDownloader { FakeSessionOf(entry) } })
            .resolve(media, episode)
        val data = provider.open(this)
        assertEquals("https://example.com", assertIs<UriMediaData>(data).uri)
        assertEquals(1, entry.closedHandles, "the handle opened before the check must be released")
    }

    // 首播没有 resume data 时, startDownload 要登录并解析磁力, 这一步卡住同样要在预算内回退.
    @Test
    fun `falls back when the session never opens`() = runTest {
        val provider = resolver(FakeTorrentEngine { FakeDownloader { awaitCancellation() } })
            .resolve(media, episode)
        val data = provider.open(this)
        assertEquals("https://example.com", assertIs<UriMediaData>(data).uri)
    }

    // 用户主动取消不是超时: 取消要继续传播, 不能替用户去打开 anitorrent.
    @Test
    fun `user cancellation does not fall back`() = runTest {
        var fallbackCalls = 0
        val countingFallback = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                fallbackCalls++
                return TestMediaDataProvider()
            }
        }
        val provider = resolver(
            FakeTorrentEngine { FakeDownloader { awaitCancellation() } },
            fallback = countingFallback,
            cloudOpenTimeout = 1.hours,
        ).resolve(media, episode)

        val job = launch(start = CoroutineStart.UNDISPATCHED) { provider.open(this) }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(true, job.isCancelled)
        assertEquals(0, fallbackCalls, "取消不能触发回退")
    }

    @Test
    fun `falls back when the listing is incomplete and nothing matches`() = runTest {
        val entry = FakeCloudEntry("Season Pack - 01.mp4") {}
        val provider = resolver(FakeTorrentEngine { FakeDownloader { PartialSessionOf(entry) } })
            .resolve(media, EpisodeMetadata("test 04", EpisodeSort(4), EpisodeSort(4)))
        val data = provider.open(this)
        assertEquals("https://example.com", assertIs<UriMediaData>(data).uri)
    }

    @Test
    fun `without a fallback the failure surfaces`() = runTest {
        val resolver = resolver(FakeTorrentEngine { throw IllegalStateException("bad credentials") }, fallback = null)
        val e = assertFailsWith<MediaResolutionException> { resolver.resolve(media, episode) }
        assertEquals(ResolutionFailures.ENGINE_ERROR, e.reason)
    }
}

private class FakeTorrentEngine(
    private val getDownloader: suspend () -> TorrentDownloader,
) : TorrentEngine {
    override val type: TorrentEngineType get() = TorrentEngineType.PikPak
    override val location: MediaSourceLocation get() = MediaSourceLocation.Online
    override val isSupported: Boolean get() = true
    override val saveDir: SystemPath get() = Path("build/fake-torrent-engine").inSystem
    override suspend fun testConnection(): Boolean = true
    override suspend fun getDownloader(): TorrentDownloader = getDownloader.invoke()
    override fun close() {}
}

private class FakeDownloader(
    private val startDownload: suspend () -> TorrentSession,
) : TorrentDownloader {
    override val totalStats: Flow<TorrentDownloader.Stats> get() = emptyFlow()
    override val vendor: TorrentLibInfo get() = TorrentLibInfo("fake", "0", supportsStreaming = true)

    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo =
        EncodedTorrentInfo.createRaw(byteArrayOf(1))

    override suspend fun startDownload(data: EncodedTorrentInfo, parentCoroutineContext: CoroutineContext) =
        startDownload.invoke()

    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): SystemPath =
        Path("build/fake-torrent-engine").inSystem

    override fun listSaves(): List<SystemPath> = emptyList()
    override fun close() {}
}

private class FakeSessionOf(private vararg val entries: TorrentFileEntry) : TorrentSession {
    override val sessionStats: Flow<TorrentSession.Stats?> get() = emptyFlow()
    override suspend fun getName(): String = "fake"
    override suspend fun getFiles(): List<TorrentFileEntry> = entries.toList()
    override fun getPeers(): List<PeerInfo> = emptyList()
    override fun getState(): TorrentHandleState? = null
    override suspend fun close() {}
    override suspend fun closeIfNotInUse() = Unit
}

private class PartialSessionOf(private vararg val entries: TorrentFileEntry) : TorrentSession, PartialListing {
    override val listingComplete: Boolean get() = false
    override val sessionStats: Flow<TorrentSession.Stats?> get() = emptyFlow()
    override suspend fun getName(): String = "fake"
    override suspend fun getFiles(): List<TorrentFileEntry> = entries.toList()
    override fun getPeers(): List<PeerInfo> = emptyList()
    override fun getState(): TorrentHandleState? = null
    override suspend fun close() {}
    override suspend fun closeIfNotInUse() = Unit
}

private class FakeCloudEntry(
    override val pathInTorrent: String,
    private val ready: suspend () -> Unit,
) : TorrentFileEntry, CloudReadiness {
    var closedHandles = 0
    override val fileName: String get() = pathInTorrent
    override val supportsStreaming: Boolean get() = true
    override suspend fun ensureCloudReady() = ready()

    override fun createHandle(): TorrentFileHandle = object : TorrentFileHandle {
        override val entry: TorrentFileEntry get() = this@FakeCloudEntry
        override fun resume(priority: FilePriority) {}
        override fun pause() {}
        override suspend fun close() { closedHandles++ }
        override suspend fun closeAndDelete() = close()
    }

    override val fileStats: Flow<TorrentFileEntry.Stats> get() = emptyFlow()
    override val length: Long get() = 1
    override val pieces: PieceList get() = throw UnsupportedOperationException()
    override suspend fun resolveFile(): SystemPath = throw UnsupportedOperationException()
    override fun resolveFileMaybeEmptyOrNull(): SystemPath? = null
    override suspend fun createInput(awaitCoroutineContext: CoroutineContext): SeekableInput =
        throw UnsupportedOperationException()
}

private object FakeSession : TorrentSession {
    override val sessionStats: Flow<TorrentSession.Stats?> get() = emptyFlow()
    override suspend fun getName(): String = "fake"
    override suspend fun getFiles(): List<TorrentFileEntry> = emptyList()
    override fun getPeers(): List<PeerInfo> = emptyList()
    override fun getState(): TorrentHandleState? = null
    override suspend fun close() {}
    override suspend fun closeIfNotInUse() = Unit
}

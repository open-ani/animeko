/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.torrent.offline.OfflineDownloadAuthException
import me.him188.ani.torrent.offline.OfflineDownloadEngine
import me.him188.ani.torrent.offline.OfflineDownloadRejectedException
import me.him188.ani.torrent.offline.ResolvedMedia
import org.openani.mediamp.source.MediaExtraFiles as MediampMediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [OfflineDownloadMediaResolver] — the chain-head resolver
 * that routes BT media through a cloud offline-download engine and falls
 * back to the local anitorrent resolver when the engine can't deliver.
 *
 * The four upstream-review blockers this resolver is meant to satisfy all
 * concentrate in the fallback matrix (which exceptions surface to the
 * caller vs. which get handed off to [MediaResolver]). These tests pin down
 * that matrix so a future edit to the catch block can't silently widen or
 * narrow the fallback policy without a test breaking.
 */
class OfflineDownloadMediaResolverTest {

    private val magnetMedia: Media = createTestDefaultMedia(
        mediaId = "test.magnet",
        mediaSourceId = "test",
        originalUrl = "https://example.org/1",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567"),
        originalTitle = "test magnet",
        publishedTime = 0,
        properties = createTestMediaProperties(),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        extraFiles = MediaExtraFiles.EMPTY,
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.BitTorrent,
    )

    private val httpStreamingMedia: Media = createTestDefaultMedia(
        mediaId = "test.http",
        mediaSourceId = "test",
        originalUrl = "https://example.org/2",
        download = ResourceLocation.HttpStreamingFile("https://example.org/video.mp4"),
        originalTitle = "test http",
        publishedTime = 0,
        properties = createTestMediaProperties(),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        extraFiles = MediaExtraFiles.EMPTY,
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    private val episode = EpisodeMetadata(title = "EP1", ep = EpisodeSort(1), sort = EpisodeSort(1))

    @Test
    fun `supports - false when engine reports unsupported`() {
        val engine = FakeEngine(isSupported = false)
        val resolver = OfflineDownloadMediaResolver(engine)
        assertFalse(resolver.supports(magnetMedia))
    }

    @Test
    fun `supports - false for non-BT media even when engine is supported`() {
        val engine = FakeEngine(isSupported = true)
        val resolver = OfflineDownloadMediaResolver(engine)
        assertFalse(resolver.supports(httpStreamingMedia))
    }

    @Test
    fun `supports - true for magnet when engine is supported`() {
        val engine = FakeEngine(isSupported = true)
        val resolver = OfflineDownloadMediaResolver(engine)
        assertTrue(resolver.supports(magnetMedia))
    }

    @Test
    fun `resolve - returns HttpStreamingMediaDataProvider on engine success`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveResult = ResolvedMedia(streamUrl = "https://cdn.example/signed.mp4"),
        )
        val resolver = OfflineDownloadMediaResolver(engine)
        val provider = resolver.resolve(magnetMedia, episode)
        val opened = assertIs<HttpStreamingMediaDataProvider>(provider)
            .open(kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext))
        assertEquals("https://cdn.example/signed.mp4", opened.uri)
    }

    @Test
    fun `resolve - auth failure is delegated to fallback when one is configured`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = OfflineDownloadAuthException("wrong password"),
        )
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        val provider = resolver.resolve(magnetMedia, episode)
        assertSame(fallback.sentinel, provider)
        assertEquals(1, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - network IOException is delegated to fallback`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = IOException("proxy unreachable"),
        )
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        resolver.resolve(magnetMedia, episode)
        assertEquals(1, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - timeout is delegated to fallback`() = runTest {
        // TimeoutCancellationException inherits from CancellationException at
        // the platform level, but the resolver explicitly tests for it first
        // so that real coroutine cancel propagates while a withTimeout fires
        // the fallback. Construct one via an actually-timed-out block so we
        // get the real class, not a hand-rolled stand-in.
        val toThrow: Throwable = try {
            kotlinx.coroutines.withTimeout(1) {
                kotlinx.coroutines.delay(1000)
                error("should not reach")
            }
        } catch (e: TimeoutCancellationException) {
            e
        }
        val engine = FakeEngine(isSupported = true, resolveThrows = toThrow)
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        resolver.resolve(magnetMedia, episode)
        assertEquals(1, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - rejected uri is delegated to fallback`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = OfflineDownloadRejectedException("dead torrent"),
        )
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        resolver.resolve(magnetMedia, episode)
        assertEquals(1, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - generic engine exception is delegated to fallback`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = RuntimeException("boom"),
        )
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        resolver.resolve(magnetMedia, episode)
        assertEquals(1, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - CancellationException propagates without hitting fallback`() = runTest {
        // A caller cancel should never be swallowed by the fallback path; if
        // that happened, cancellation would stop being a first-class signal
        // and the UI's "stop playback" affordance would silently re-run the
        // resolver chain.
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = CancellationException("user cancelled"),
        )
        val fallback = RecordingFallback(supports = true)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        assertFailsWith<CancellationException> {
            resolver.resolve(magnetMedia, episode)
        }
        assertEquals(0, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - without fallback, engine failure surfaces as MediaResolutionException`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = IOException("no proxy"),
        )
        val resolver = OfflineDownloadMediaResolver(engine, fallback = null)
        val ex = assertFailsWith<MediaResolutionException> {
            resolver.resolve(magnetMedia, episode)
        }
        assertEquals(ResolutionFailures.NETWORK_ERROR, ex.reason)
        assertIs<IOException>(ex.cause)
    }

    @Test
    fun `resolve - fallback that doesn't support the media falls back to MediaResolutionException`() = runTest {
        val engine = FakeEngine(
            isSupported = true,
            resolveThrows = OfflineDownloadRejectedException("dead torrent"),
        )
        val fallback = RecordingFallback(supports = false)
        val resolver = OfflineDownloadMediaResolver(engine, fallback = fallback)
        val ex = assertFailsWith<MediaResolutionException> {
            resolver.resolve(magnetMedia, episode)
        }
        assertEquals(ResolutionFailures.NO_MATCHING_RESOURCE, ex.reason)
        assertEquals(0, fallback.resolveCallCount)
    }

    @Test
    fun `resolve - exception reason classification is stable per exception type`() = runTest {
        // Pins the mapping from engine exception types to ResolutionFailures
        // reasons when no fallback is available. Reviewers should be able to
        // read this table off the test and cross-check against the resolver.
        suspend fun reasonFor(ex: Throwable): ResolutionFailures {
            val engine = FakeEngine(isSupported = true, resolveThrows = ex)
            val resolver = OfflineDownloadMediaResolver(engine, fallback = null)
            return assertFailsWith<MediaResolutionException> {
                resolver.resolve(magnetMedia, episode)
            }.reason
        }

        assertEquals(
            ResolutionFailures.ENGINE_ERROR,
            reasonFor(OfflineDownloadAuthException("auth")),
        )
        assertEquals(
            ResolutionFailures.NO_MATCHING_RESOURCE,
            reasonFor(OfflineDownloadRejectedException("rejected")),
        )
        assertEquals(
            ResolutionFailures.NETWORK_ERROR,
            reasonFor(IOException("io")),
        )
        assertEquals(
            ResolutionFailures.ENGINE_ERROR,
            reasonFor(RuntimeException("boom")),
        )
    }

    // --- Fakes -------------------------------------------------------------

    private class FakeEngine(
        isSupported: Boolean,
        private val resolveResult: ResolvedMedia? = null,
        private val resolveThrows: Throwable? = null,
    ) : OfflineDownloadEngine {
        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val isSupported: StateFlow<Boolean> = MutableStateFlow(isSupported)

        override suspend fun resolve(
            uri: String,
            pickVideoFile: (candidateFilenames: List<String>) -> String?,
        ): ResolvedMedia {
            resolveThrows?.let { throw it }
            return resolveResult
                ?: error("FakeEngine configured with neither result nor exception")
        }
    }

    /**
     * A fallback MediaResolver that records calls and returns a sentinel
     * provider so assertions can distinguish "fallback ran" from "engine
     * produced the provider".
     */
    private class RecordingFallback(
        private val supports: Boolean,
    ) : MediaResolver {
        val sentinel: MediaDataProvider<UriMediaData> = SentinelProvider
        var resolveCallCount: Int = 0
            private set

        override fun supports(media: Media): Boolean = supports

        override suspend fun resolve(
            media: Media,
            episode: EpisodeMetadata,
        ): MediaDataProvider<*> {
            resolveCallCount++
            return sentinel
        }
    }

    private object SentinelProvider : MediaDataProvider<UriMediaData> {
        override val extraFiles: MediampMediaExtraFiles = MediampMediaExtraFiles.EMPTY
        override suspend fun open(scopeForCleanup: kotlinx.coroutines.CoroutineScope): UriMediaData =
            UriMediaData("sentinel://fallback")
    }
}

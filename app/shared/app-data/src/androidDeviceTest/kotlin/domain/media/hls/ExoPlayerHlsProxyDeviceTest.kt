/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 在真机/模拟器上用真实的 ExoPlayer (media3) 通过 [PlatformHlsPlaybackPreparer] 的本地代理播放真实 HLS 夹具
 * (`assets/hls`, 与桌面端测试共用), 验证:
 *
 * - 各种播放列表形态 (普通点播, 主播放列表, AES-128, fMP4, 过滤广告) 都能正常起播并跳转;
 * - 在慢速源站上, 预缓存跳转目标后, 跳转到该位置的起播时间明显缩短, 且不再访问源站.
 */
@OptIn(UnstableApi::class)
class ExoPlayerHlsProxyDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    // ---------------- 测试 ----------------

    @Test
    fun exoplayerPlaysEveryVariantThroughTheProxyAndSeeks() = withFixture {
        data class Case(val path: String, val options: HlsPlaybackOptions, val durations: Set<Long>)

        val cases = listOf(
            Case("/hls/vod/index.m3u8", HlsPlaybackOptions(proxySegments = true), setOf(96_000)),
            Case("/hls/master.m3u8", HlsPlaybackOptions(proxySegments = true), setOf(96_000, 60_000)),
            Case("/hls/aes/index.m3u8", HlsPlaybackOptions(proxySegments = true), setOf(60_000)),
            Case("/hls/fmp4/index.m3u8", HlsPlaybackOptions(proxySegments = true), setOf(60_000)),
            Case("/hls/withads.m3u8", HlsPlaybackOptions(filterSegments = true, proxySegments = true), setOf(96_000)),
        )
        for (case in cases) {
            val result = preparer.prepare(UriMediaData(origin.url(case.path)), case.options)
            val session = assertNotNull(result.session, "expected proxy session for ${case.path}")
            val harness = PlayerHarness(context)
            try {
                harness.open(result.data.uri, playWhenReady = true)
                harness.await("${case.path}: start playing") { it.state == Player.STATE_READY && it.positionMillis >= 1_000 }
                val duration = harness.snapshot().durationMillis
                assertTrue(
                    case.durations.any { kotlin.math.abs(it - duration) <= 1_500 },
                    "${case.path}: unexpected duration $duration, expected one of ${case.durations}",
                )

                harness.onMain { harness.player.seekTo(40_000) }
                harness.await("${case.path}: ready after seek") { it.state == Player.STATE_READY && it.positionMillis >= 40_000 }
                val afterSeek = harness.snapshot().positionMillis
                harness.await("${case.path}: position advances after seek") { it.positionMillis >= afterSeek + 1_000 }
                assertNull(harness.error, "${case.path}: player error ${harness.error}")
                log("ExoPlayer OK ${case.path} ${case.options}: duration=$duration")
            } finally {
                harness.release()
                session.close()
            }
        }
    }

    @Test
    fun prefetchLetsExoplayerResumeFasterAfterSeekingOnASlowOrigin() = withFixture {
        origin.segmentLatencyMillis = 1_500
        val target = 48_000L // 相当于跳过 OP 后的位置

        // 不预缓存
        val coldMillis = run {
            val result = preparer.prepare(UriMediaData(origin.url("/hls/vod/index.m3u8")), HlsPlaybackOptions(proxySegments = true))
            val session = assertNotNull(result.session)
            val harness = PlayerHarness(context)
            try {
                harness.open(result.data.uri, playWhenReady = false)
                harness.await("cold: initial ready") { it.state == Player.STATE_READY }
                val start = System.nanoTime()
                harness.onMain { harness.player.seekTo(target) }
                harness.await("cold: ready after seek") { it.state == Player.STATE_READY && it.positionMillis >= target }
                assertNull(harness.error)
                (System.nanoTime() - start) / 1_000_000
            } finally {
                harness.release()
                session.close()
            }
        }

        // 预缓存 [48s, 78s) 后再跳转
        val warmMillis = run {
            val result = preparer.prepare(UriMediaData(origin.url("/hls/vod/index.m3u8")), HlsPlaybackOptions(proxySegments = true))
            val session = assertNotNull(result.session)
            val harness = PlayerHarness(context)
            try {
                harness.open(result.data.uri, playWhenReady = false)
                harness.await("warm: initial ready") { it.state == Player.STATE_READY }
                session.setPrefetchRange(MediaTimeRange(target, target + 30_000))
                withTimeout(90_000) {
                    session.prefetchProgress.first { list -> list.size == 10 && list.all { it.state == ChunkState.DONE } }
                }
                val mark = origin.requests.size
                val start = System.nanoTime()
                harness.onMain { harness.player.seekTo(target) }
                harness.await("warm: ready after seek") { it.state == Player.STATE_READY && it.positionMillis >= target }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                assertNull(harness.error)

                val prefetched = (16..25).map { "/hls/vod/seg%03d.ts".format(it) }.toSet()
                val requestedAfterSeek = origin.requests.drop(mark)
                assertTrue(
                    requestedAfterSeek.none { it in prefetched },
                    "prefetched segments must be served from cache, but origin saw $requestedAfterSeek",
                )
                elapsed
            } finally {
                harness.release()
                session.close()
            }
        }

        log("seek to 48s on slow origin: without prefetch=${coldMillis}ms, with prefetch=${warmMillis}ms")
        assertTrue(
            warmMillis + 1_000 < coldMillis,
            "expected prefetch to save at least one segment latency: cold=${coldMillis}ms warm=${warmMillis}ms",
        )
    }

    // ---------------- 基础设施 ----------------

    private class Fixture(val origin: AssetOrigin, val preparer: PlatformHlsPlaybackPreparer)

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val origin = AssetOrigin(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            Fixture(origin, PlatformHlsPlaybackPreparer(provider)).block()
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        println("[$TAG] $message")
    }

    private class Snapshot(val state: Int, val positionMillis: Long, val durationMillis: Long)

    /**
     * 在主线程上持有一个真实的 ExoPlayer. 不设置 Surface: 播放时钟由音频驱动, 足以验证加载与跳转.
     * 缓冲上限设为 6 秒, 避免播放器自己一路缓冲到跳转目标, 影响对预缓存效果的测量.
     */
    private inner class PlayerHarness(context: Context) {
        @Volatile
        var error: PlaybackException? = null
        lateinit var player: ExoPlayer

        init {
            onMain {
                player = ExoPlayer.Builder(context)
                    .setLoadControl(
                        DefaultLoadControl.Builder().setBufferDurationsMs(6_000, 6_000, 1_000, 1_000).build(),
                    )
                    .build()
                player.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(e: PlaybackException) {
                            error = e
                        }
                    },
                )
            }
        }

        fun <T> onMain(block: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return result!!.getOrThrow()
        }

        fun open(uri: String, playWhenReady: Boolean) = onMain {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.playWhenReady = playWhenReady
            player.prepare()
        }

        fun snapshot(): Snapshot = onMain {
            Snapshot(player.playbackState, player.currentPosition, player.duration)
        }

        fun await(what: String, timeoutMillis: Long = 60_000, predicate: (Snapshot) -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                error?.let { throw AssertionError("$what: player error", it) }
                val snapshot = snapshot()
                if (predicate(snapshot)) return
                if (System.currentTimeMillis() > deadline) {
                    throw AssertionError("$what: timed out, state=${snapshot.state} position=${snapshot.positionMillis}")
                }
                Thread.sleep(20)
            }
        }

        fun release() = onMain { player.release() }
    }

    /**
     * 把 assets 里的 HLS 夹具作为源站提供的极简 HTTP 服务 (Android 上没有 JDK 自带的 HttpServer).
     */
    private class AssetOrigin(private val context: Context) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val recorded = ConcurrentLinkedQueue<String>()

        @Volatile
        var segmentLatencyMillis: Long = 0

        val requests: List<String> get() = recorded.toList()
        fun url(path: String): String = "http://127.0.0.1:${serverSocket.localPort}$path"

        private val acceptThread = thread(name = "AssetOrigin", isDaemon = true) {
            while (!closed.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: SocketException) {
                    if (closed.get()) break else continue
                }
                thread(name = "AssetOrigin-conn", isDaemon = true) {
                    runCatching {
                        socket.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))
                            val requestLine = reader.readLine() ?: return@use
                            val headers = HashMap<String, String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                val colon = line.indexOf(':')
                                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                            }
                            val path = requestLine.substringAfter(' ').substringBefore(' ').substringBefore('?')
                            recorded += path
                            if (segmentLatencyMillis > 0 && !path.endsWith(".m3u8")) Thread.sleep(segmentLatencyMillis)
                            val body = runCatching { context.assets.open(path.removePrefix("/")).use { it.readBytes() } }.getOrNull()
                            val output = s.getOutputStream()
                            if (body == null) {
                                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            } else {
                                val range = headers["range"]?.removePrefix("bytes=")?.let { spec ->
                                    val start = spec.substringBefore('-').toIntOrNull() ?: return@let null
                                    val end = spec.substringAfter('-').toIntOrNull() ?: (body.size - 1)
                                    if (start in body.indices) start..end.coerceAtMost(body.size - 1) else null
                                }
                                val slice = if (range != null) body.copyOfRange(range.first, range.last + 1) else body
                                val header = buildString {
                                    if (range != null) {
                                        append("HTTP/1.1 206 Partial Content\r\n")
                                        append("Content-Range: bytes ${range.first}-${range.last}/${body.size}\r\n")
                                    } else {
                                        append("HTTP/1.1 200 OK\r\n")
                                    }
                                    append("Content-Type: ${contentTypeOf(path)}\r\n")
                                    append("Accept-Ranges: bytes\r\n")
                                    append("Content-Length: ${slice.size}\r\n")
                                    append("Connection: close\r\n\r\n")
                                }
                                output.write(header.toByteArray(StandardCharsets.US_ASCII))
                                output.write(slice)
                            }
                            output.flush()
                        }
                    }
                }
            }
        }

        private fun contentTypeOf(path: String): String = when (path.substringAfterLast('.')) {
            "m3u8" -> "application/vnd.apple.mpegurl"
            "ts" -> "video/mp2t"
            "m4s", "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) serverSocket.close()
        }

        @Suppress("unused")
        private fun keepThreadReachable(): Thread = acceptThread
    }

    private companion object {
        const val TAG = "HlsProxyDeviceTest"
    }
}

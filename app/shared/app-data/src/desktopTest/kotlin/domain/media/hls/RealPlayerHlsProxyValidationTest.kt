/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 用真实的播放器/解复用器 (mpv 与 ffmpeg 命令行, 二者都使用 libavformat 的 HLS 实现, 桌面端播放器内核即 mpv)
 * 通过本地代理播放真实 HLS 夹具, 验证代理对真实客户端的兼容性, 并测量预缓存对跳转后起播时间的影响.
 *
 * 需要本机安装 mpv / ffmpeg, 且会启动外部进程, 因此默认跳过. 设置环境变量 `ANI_HLS_REAL_PLAYER=1` 启用:
 * ```
 * ANI_HLS_REAL_PLAYER=1 ./gradlew :app:shared:app-data:desktopTest --tests '*RealPlayerHlsProxyValidationTest'
 * ```
 */
abstract class AbstractRealPlayerHlsProxyValidationTest internal constructor(
    private val serverFactory: HlsProxyServerFactory,
) {
    private val enabled = System.getenv("ANI_HLS_REAL_PLAYER") == "1"
    private val ffmpeg = findExecutable("ffmpeg")
    private val ffprobe = findExecutable("ffprobe")
    private val mpv = findExecutable("mpv")

    private class Fixture(val origin: HlsFixtureOrigin, val preparer: PlatformHlsPlaybackPreparer)

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val origin = HlsFixtureOrigin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            Fixture(
                origin,
                // 与桌面端相同: mpv 需要代理对齐拼接流的时间戳
                PlatformHlsPlaybackPreparer(
                    provider,
                    PlatformHlsPlaybackPreparer.DEFAULT_SEGMENT_CACHE_MAX_BYTES,
                    serverFactory,
                    alignTimestamps = true,
                ),
            ).block()
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private fun skipUnless(vararg tools: String?): Boolean {
        if (!enabled) {
            println("[RealPlayer] skipped: set ANI_HLS_REAL_PLAYER=1 to enable")
            return true
        }
        if (tools.any { it == null }) {
            println("[RealPlayer] skipped: required tool not installed")
            return true
        }
        return false
    }

    private class Run(val exitCode: Int, val output: String, val millis: Long)

    private fun run(vararg command: String, timeoutSeconds: Long = 120): Run {
        val start = System.nanoTime()
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "timed out: ${command.joinToString(" ")}" }
        return Run(process.exitValue(), output, (System.nanoTime() - start) / 1_000_000)
    }

    private suspend fun Fixture.proxied(path: String, options: HlsPlaybackOptions = HlsPlaybackOptions(proxySegments = true)): HlsPlaybackPreparerResult {
        val result = preparer.prepare(UriMediaData(origin.url(path)), options)
        assertNotNull(result.session, "expected proxy session for $path")
        return result
    }

    private fun probeDuration(url: String): Double {
        val r = run(ffprobe!!, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", url)
        assertEquals(0, r.exitCode, r.output)
        return r.output.trim().lines().last().toDouble()
    }

    /** ffmpeg 完整解码一遍 (视频+音频), 任何损坏的分片都会报错. */
    private fun decodeAll(url: String): Run =
        run(ffmpeg!!, "-v", "error", "-xerror", "-i", url, "-f", "null", "-")

    /** libavformat 读出的视频 DTS (秒), 按解复用顺序. mpv 的 `time-pos` 取自同一个解复用器. */
    private fun videoDts(url: String): List<Double> {
        val r = run(ffprobe!!, "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=dts_time", "-of", "csv=p=0", url)
        assertEquals(0, r.exitCode, r.output)
        return r.output.lines().mapNotNull { it.trim().trimEnd(',').toDoubleOrNull() }
    }

    @Test
    fun `libavformat sees a monotonic timeline through the proxy on an unfiltered spliced stream`() = withFixture {
        if (skipUnless(ffprobe)) return@withFixture
        fun rewinds(dts: List<Double>) = dts.zipWithNext().filter { (previous, next) -> next < previous }

        assertTrue(rewinds(videoDts(origin.url("/hls/withads.m3u8"))).isNotEmpty(), "fixture premise: the ads rewind the timeline")
        val result = proxied("/hls/withads.m3u8")
        try {
            val dts = videoDts(result.data.uri)
            assertTrue(dts.size > 100, "expected the whole stream, got ${dts.size} packets")
            assertEquals(emptyList(), rewinds(dts))
            // 102 秒的播放列表, 时间轴不应被拉长或压缩
            assertEquals(102.0, dts.last() - dts.first(), 1.0)
        } finally {
            result.session?.close()
        }
    }

    @Test
    fun `ffmpeg decodes every fixture variant through the proxy without errors`() = withFixture {
        if (skipUnless(ffmpeg, ffprobe)) return@withFixture
        /**
         * @param cleanStream 流本身是否干净. 未过滤的广告拼接流在拼接点上 TS 的 continuity counter 跳变
         *   (夹具的广告是 seg000 的副本), ffmpeg 在 `-xerror` 下直连源站也会把那里的包判为损坏.
         *   代理不改 continuity counter, 这种情况只要求经代理与直连源站的结果一致.
         */
        data class Case(
            val path: String,
            val options: HlsPlaybackOptions,
            val expectedSeconds: Double,
            val cleanStream: Boolean = true,
        )

        val cases = listOf(
            Case("/hls/vod/index.m3u8", HlsPlaybackOptions(proxySegments = true), 96.0),
            Case("/hls/master.m3u8", HlsPlaybackOptions(proxySegments = true), 96.0), // ffmpeg 会打开全部变体, 时长取最长的 vod
            Case("/hls/aes/index.m3u8", HlsPlaybackOptions(proxySegments = true), 60.0),
            Case("/hls/fmp4/index.m3u8", HlsPlaybackOptions(proxySegments = true), 60.0),
            Case("/hls/withads.m3u8", HlsPlaybackOptions(filterSegments = true, proxySegments = true), 96.0),
            Case("/hls/withads.m3u8", HlsPlaybackOptions(proxySegments = true), 102.0, cleanStream = false),
        )
        for (case in cases) {
            val result = proxied(case.path, case.options)
            try {
                val duration = probeDuration(result.data.uri)
                assertTrue(
                    duration in (case.expectedSeconds - 1.5)..(case.expectedSeconds + 1.5),
                    "${case.path} ${case.options}: duration $duration, expected ~${case.expectedSeconds}",
                )
                val decode = decodeAll(result.data.uri)
                if (case.cleanStream) {
                    assertEquals(0, decode.exitCode, "${case.path}: ffmpeg failed:\n${decode.output}")
                    assertEquals("", decode.output.trim(), "${case.path}: ffmpeg reported decode errors")
                } else {
                    val direct = decodeAll(origin.url(case.path))
                    assertEquals(direct.exitCode, decode.exitCode, "${case.path}: proxy must behave like the origin.\ndirect:\n${direct.output}\nproxied:\n${decode.output}")
                }
                println("[RealPlayer] ffmpeg OK ${case.path} ${case.options}: duration=$duration decode=${decode.millis}ms")
            } finally {
                result.session?.close()
            }
        }
    }

    @Test
    fun `mpv plays through the proxy and seeks into the middle`() = withFixture {
        if (skipUnless(mpv)) return@withFixture
        for (path in listOf("/hls/vod/index.m3u8", "/hls/master.m3u8", "/hls/aes/index.m3u8", "/hls/fmp4/index.m3u8")) {
            val result = proxied(path)
            try {
                // 从 40 秒处开始播放 3 秒: 相当于一次跳转
                val r = run(
                    mpv!!, "--no-config", "--vo=null", "--ao=null", "--msg-level=all=warn",
                    "--start=40", "--length=3", result.data.uri,
                )
                assertEquals(0, r.exitCode, "$path: mpv failed:\n${r.output}")
                assertTrue(
                    r.output.lineSequence().none { "error" in it.lowercase() || "failed" in it.lowercase() },
                    "$path: mpv reported problems:\n${r.output}",
                )
                println("[RealPlayer] mpv OK $path in ${r.millis}ms")
            } finally {
                result.session?.close()
            }
        }
    }

    @Test
    fun `prefetch makes mpv start faster after jumping to the prefetched position on a slow origin`() = withFixture {
        if (skipUnless(mpv)) return@withFixture
        // 慢速源站: 每个分片 1.5 秒延迟. 跳转目标 48s (跳过 OP 后的位置), 播放 10 帧 (2.5 秒内容, 跨 1~2 个分片)
        origin.segmentLatencyMillis = 1_500
        fun playFrom48(url: String): Run = run(
            mpv!!, "--no-config", "--vo=null", "--ao=null", "--msg-level=all=warn",
            "--start=48", "--frames=10", "--untimed", url,
        )

        val cold = proxied("/hls/vod/index.m3u8")
        val coldRun = try {
            playFrom48(cold.data.uri)
        } finally {
            cold.session?.close()
        }
        assertEquals(0, coldRun.exitCode, coldRun.output)

        val warm = proxied("/hls/vod/index.m3u8")
        val warmRun = try {
            // 代理需要先提供过播放列表才知道时间轴; 真实场景中播放器早已在播放
            httpGet(warm.data.uri)
            warm.session!!.setPrefetchRange(MediaTimeRange(48_000, 78_000))
            withTimeout(60_000) {
                warm.session!!.prefetchProgress.first { list -> list.size == 10 && list.all { it.state == ChunkState.DONE } }
            }
            val before = origin.requests.size
            val run = playFrom48(warm.data.uri)
            val requested = origin.requests.drop(before).map { it.path }.filter { it.endsWith(".ts") }
            println("[RealPlayer] origin segment requests during prefetched playback: $requested")
            // 命令行 mpv 打开时会先读第一个分片探测流信息, 这在真实场景 (播放器早已打开) 不会发生; 预缓存范围内的分片不应再访问源站
            val prefetched = (16..25).map { "/hls/vod/seg%03d.ts".format(it) }.toSet()
            assertTrue(requested.none { it in prefetched }, "prefetched segments must be served from cache: $requested")
            run
        } finally {
            warm.session?.close()
        }
        assertEquals(0, warmRun.exitCode, warmRun.output)

        println("[RealPlayer] jump to 48s on slow origin: without prefetch=${coldRun.millis}ms, with prefetch=${warmRun.millis}ms")
        assertTrue(
            warmRun.millis + 1_000 < coldRun.millis,
            "expected prefetch to save at least one segment latency: cold=${coldRun.millis}ms warm=${warmRun.millis}ms",
        )
    }

    private fun findExecutable(name: String): String? {
        val candidates = if (System.getProperty("os.name").startsWith("Windows")) listOf("$name.exe", name) else listOf(name)
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        return dirs.flatMap { dir -> candidates.map { File(dir, it) } }.firstOrNull { it.canExecute() }?.absolutePath
    }
}

class RealPlayerHlsProxyValidationTest : AbstractRealPlayerHlsProxyValidationTest(PlatformHlsProxyServerFactory)

/** iOS 使用的 socket 实现, 用真实的 ffmpeg / mpv 验证. */
class KtorNetworkRealPlayerHlsProxyValidationTest : AbstractRealPlayerHlsProxyValidationTest(KtorNetworkHlsProxyServer.Factory)

/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import me.him188.ani.app.domain.media.hls.KtorNetworkHlsProxyServer
import me.him188.ani.app.domain.media.hls.httpGet
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.resolve
import org.openani.mediamp.source.UriMediaData
import java.nio.file.Files
import java.nio.file.Path as JvmPath
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "ANI_STREAM_TS_FIXTURE", matches = ".+")
class AvkitStreamingPlaybackTest {
    @Test
    fun `stream is playable before conversion completes and authenticated ranges stay upstream`() = runBlocking {
        val directory = SystemPaths.createTempDirectory("streaming-test-")
        val finish = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val headerSeen = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("secret", request.headers["X-Emby-Token"])
            assertEquals("bytes=4-7", request.headers[HttpHeaders.Range])
            headerSeen.complete(Unit)
            respond(byteArrayOf(4, 5, 6, 7), HttpStatusCode.PartialContent, headersOf(HttpHeaders.ContentRange, "bytes 4-7/100"))
        })
        val fixture = checkNotNull(System.getenv("ANI_STREAM_TS_FIXTURE"))
        val execute: suspend (List<String>) -> Boolean = { args ->
            try {
                val input = args[args.indexOf("-i") + 1]
                val relayed = withContext(Dispatchers.IO) { httpGet(input, mapOf("Range" to "bytes=4-7")) }
                assertEquals(206, relayed.status)
                assertContentEquals(byteArrayOf(4, 5, 6, 7), relayed.body)
                assertFalse(args.any { "secret" in it || "remote.test" in it })
                val output = args[args.indexOf("mpegts") + 1]
                SystemFileSystem.sink(Path(output)).buffered().use { it.write(Files.readAllBytes(JvmPath.of(fixture))) }
                finish.await()
                true
            } finally { stopped.complete(Unit) }
        }
        val playback = withTimeout(15_000) {
            AvkitStreamingPlayback(client, execute, KtorNetworkHlsProxyServer, { directory }).prepare(
                UriMediaData("https://remote.test/video.mkv", mapOf("X-Emby-Token" to "secret")),
                MediaContainerTrackInfo("V_MPEG4/ISO/AVC", emptyList(), listOf("A_EAC3")), 0,
            )
        }
        try {
            headerSeen.await()
            assertFalse(finish.isCompleted)
            val url = (playback.data as UriMediaData).uri
            val playlist = withContext(Dispatchers.IO) { httpGet(url) }.body.decodeToString()
            assertTrue("#EXTINF:" in playlist)
            assertFalse("#EXT-X-ENDLIST" in playlist)
            val segment = withContext(Dispatchers.IO) { httpGet(url.replace("playlist.m3u8", "segment-0.ts")) }
            assertEquals(200, segment.status)
            assertEquals(0x47, segment.body[0].toInt())
            assertEquals(0, segment.body.size % 188)
            val decoder = ProcessBuilder("ffmpeg", "-v", "error", "-xerror", "-i", "pipe:0", "-frames:v", "1", "-f", "null", "-")
                .redirectErrorStream(true).start()
            try {
                decoder.outputStream.use { it.write(segment.body) }
                assertTrue(decoder.waitFor(15, TimeUnit.SECONDS), "The published segment must decode independently")
                assertEquals(0, decoder.exitValue(), decoder.inputStream.bufferedReader().readText())
            } finally { decoder.destroyForcibly() }
            finish.complete(Unit)
            withTimeout(5000) {
                while (!withContext(Dispatchers.IO) { httpGet(url) }.body.decodeToString().contains("#EXT-X-ENDLIST")) delay(50)
            }
            assertFalse(directory.resolve("stream.ts").exists())
        } finally {
            playback.close()
            playback.close()
            stopped.await()
            withTimeout(5000) { while (directory.exists()) delay(50) }
        }
    }

    @Test
    fun `closing playback cancels an unfinished producer and removes its files`() = runBlocking {
        val directory = SystemPaths.createTempDirectory("streaming-cancel-test-")
        val stopped = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { error("No origin request expected") })
        val fixture = checkNotNull(System.getenv("ANI_STREAM_TS_FIXTURE"))
        val playback = withTimeout(15_000) {
            AvkitStreamingPlayback(client, { args ->
                try {
                    val output = args[args.indexOf("mpegts") + 1]
                    SystemFileSystem.sink(Path(output)).buffered().use { it.write(Files.readAllBytes(JvmPath.of(fixture))) }
                    hold.await()
                    true
                } finally { stopped.complete(Unit) }
            }, KtorNetworkHlsProxyServer, { directory }).prepare(UriMediaData("https://test/video.mkv"),
                MediaContainerTrackInfo("V_MPEG4/ISO/AVC", emptyList()), 0)
        }
        playback.close()
        withTimeout(5000) {
            stopped.await()
            while (directory.exists()) delay(50)
        }
        assertFalse(hold.isCompleted)
    }

    @Test
    fun `native remux emits transport packets and preserves subtitle timestamps`() {
        val directory = SystemPaths.createTempDirectory("streaming-remux-test-")
        try {
            val subtitle = directory.resolve("input.srt")
            val text = "1\n00:00:05,000 --> 00:00:08,500\nHello\n\n2\n00:00:07,000 --> 00:00:09,000\nOverlap\n\n"
            SystemFileSystem.sink(subtitle.path).buffered().use { it.writeString(text) }
            val input = directory.resolve("input.mkv")
            runFfmpeg(listOf("-v", "error", "-y", "-i", checkNotNull(System.getenv("ANI_STREAM_TS_FIXTURE")),
                "-i", subtitle.absolutePath, "-map", "0:v", "-map", "0:a", "-map", "1:s", "-c", "copy", input.absolutePath))
            val output = directory.resolve("stream.ts")
            runFfmpeg(avkitStreamingArguments(input.absolutePath, output.absolutePath, directory,
                MediaContainerTrackInfo("V_MPEG4/ISO/AVC", listOf(MatroskaSubtitleTrack(0, "eng", null)), listOf("A_AAC"))))
            val segments = mutableListOf<AvkitStreamSegment>()
            val segmenter = AvkitTsSegmenter(segments::add)
            SystemFileSystem.source(output.path).buffered().use { stream ->
                while (!stream.exhausted()) segmenter.packet(stream.readByteArray(188))
            }
            segmenter.finish()
            assertTrue(segments.size >= 10)
            val exported = Buffer()
            SystemFileSystem.source(directory.resolve("subtitle-0.mks").path).buffered().use { exportMatroskaSubtitleAsSrt(it, exported) }
            assertEquals(text, exported.readString())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun runFfmpeg(arguments: List<String>) {
        val process = ProcessBuilder(listOf("ffmpeg", "-xerror") + arguments).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Native remux must finish")
            assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
        } finally {
            process.destroyForcibly()
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ANI_STREAM_MEDIA_DIR", matches = ".+")
    fun `HEVC and AV1 samples retain video frames audio and subtitles in independent fragments`() = runBlocking {
        for ((name, codec, audio) in listOf(Triple("hevc8", "V_MPEGH/ISO/HEVC", "aac"),
            Triple("hevc10", "V_MPEGH/ISO/HEVC", "eac3"), Triple("hevc-bframes", "V_MPEGH/ISO/HEVC", "flac"),
            Triple("av1", "V_AV1", "aac"))) {
            val directory = SystemPaths.createTempDirectory("hevc-$name-test-")
            try {
                val sample = JvmPath.of(checkNotNull(System.getenv("ANI_STREAM_MEDIA_DIR")), "animeko-sample-$name.mp4").toString()
                val subtitle = directory.resolve("input.srt")
                val text = "1\n00:00:05,000 --> 00:00:08,500\nHello\n\n"
                SystemFileSystem.sink(subtitle.path).buffered().use { it.writeString(text) }
                val input = directory.resolve("input.mkv")
                runFfmpeg(listOf("-v", "error", "-y", "-i", sample,
                    "-i", checkNotNull(System.getenv("ANI_STREAM_TS_FIXTURE")), "-i", subtitle.absolutePath,
                    "-map", "0:v", "-map", "1:a", "-map", "2:s", "-c:v", "copy", "-c:a", audio, "-c:s", "copy", "-shortest", input.absolutePath))
                val tracks = SystemFileSystem.source(input.path).buffered().use(::readMediaContainerTrackInfo)
                assertEquals(codec, tracks.videoCodecId)
                assertEquals(1, tracks.audioCodecIds.size)
                val output = directory.resolve("stream.mp4")
                runFfmpeg(avkitStreamingArguments(input.absolutePath, output.absolutePath, directory, tracks))
                var initialization = byteArrayOf()
                val segments = mutableListOf<AvkitStreamSegment>()
                val segmenter = AvkitFmp4Segmenter({ initialization = it }, segments::add)
                SystemFileSystem.source(output.path).buffered().use { stream ->
                    while (true) segmenter.box(readGrowingMp4Box(stream) { true } ?: break)
                }
                segmenter.finish()
                assertTrue(segments.size >= 2, "$name requires independently decodable first and last fragments, got ${segments.map { it.durationSeconds }}")
                assertTrue((if (codec == "V_AV1") "av01" else "hvc1") in initialization.decodeToString())
                for (segment in listOf(segments.first(), segments.last())) {
                    val independent = directory.resolve("independent.mp4")
                    SystemFileSystem.sink(independent.path).buffered().use { it.write(initialization); it.write(segment.bytes) }
                    runFfmpeg(listOf("-v", "error", "-i", independent.absolutePath, "-frames:v", "1", "-f", "null", "-"))
                }
                val exported = Buffer()
                SystemFileSystem.source(directory.resolve("subtitle-0.mks").path).buffered().use { exportMatroskaSubtitleAsSrt(it, exported) }
                assertEquals(text, exported.readString())
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ANI_STREAM_MEDIA_DIR", matches = ".+")
    fun `native HEVC producer supports HLS decoding and seeking before it reaches EOF`() = runBlocking {
        val directory = SystemPaths.createTempDirectory("hevc-live-test-")
        val origin = JvmPath.of(checkNotNull(System.getenv("ANI_STREAM_MEDIA_DIR")), "animeko-sample-hevc10.mp4")
        val subtitle = directory.resolve("origin.srt")
        SystemFileSystem.sink(subtitle.path).buffered().use {
            it.writeString("1\n00:00:05,000 --> 00:00:06,500\nHello\n\n2\n00:00:07,000 --> 00:00:09,000\nWorld\n\n")
        }
        val input = directory.resolve("origin.mkv")
        runFfmpeg(listOf("-v", "error", "-y", "-i", origin.toString(),
            "-i", checkNotNull(System.getenv("ANI_STREAM_TS_FIXTURE")), "-i", subtitle.absolutePath,
            "-map", "0:v", "-map", "1:a", "-map", "2:s", "-c", "copy", input.absolutePath))
        val bytes = Files.readAllBytes(JvmPath.of(input.absolutePath))
        val tracks = SystemFileSystem.source(input.path).buffered().use(::readMediaContainerTrackInfo)
        val stopped = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("secret", request.headers["X-Emby-Token"])
            val range = request.headers[HttpHeaders.Range]?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
            val from = range?.groupValues?.get(1)?.toInt() ?: 0
            val to = (range?.groupValues?.get(2)?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
            respond(bytes.copyOfRange(from, to + 1), if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent,
                headersOf(HttpHeaders.ContentLength to listOf((to - from + 1).toString()),
                    HttpHeaders.ContentRange to listOf("bytes $from-$to/${bytes.size}")))
        })
        val playback = withTimeout(25_000) {
            AvkitStreamingPlayback(client, { arguments ->
                val inputIndex = arguments.indexOf("-i")
                val paced = arguments.take(inputIndex) + listOf("-readrate", "2") + arguments.drop(inputIndex)
                val process = ProcessBuilder(listOf("ffmpeg") + paced).redirectErrorStream(true).start()
                try {
                    while (process.isAlive) delay(50)
                    check(process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
                    true
                } finally {
                    process.destroyForcibly()
                    stopped.complete(Unit)
                }
            }, KtorNetworkHlsProxyServer, { directory }).prepare(
                UriMediaData("https://origin.test/video.mkv", mapOf("X-Emby-Token" to "secret")), tracks, 0)
        }
        try {
            assertFalse(stopped.isCompleted, "Playback must become available while native remux is running")
            val url = (playback.data as UriMediaData).uri
            val playlist = withContext(Dispatchers.IO) { httpGet(url) }.body.decodeToString()
            assertTrue("#EXT-X-MAP:" in playlist)
            assertFalse("#EXT-X-ENDLIST" in playlist)
            val selectedSubtitle = playback.data.extraFiles.subtitles.single()
            val cues = withContext(Dispatchers.IO) { httpGet(selectedSubtitle.uri) }
            assertTrue("Hello" in cues.body.decodeToString(), "Subtitles must be available before native remux ends")
            assertEquals("0", cues.headers["x-ani-complete"])
            withContext(Dispatchers.IO) {
                runFfmpeg(listOf("-v", "error", "-ss", "4", "-i", url, "-frames:v", "1", "-f", "null", "-"))
            }
        } finally {
            playback.close()
            withTimeout(5000) {
                stopped.await()
                while (directory.exists()) delay(50)
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ANI_STREAM_MEDIA_DIR", matches = ".+")
    fun `VP9 and MPEG4 use the compatible file path when HLS cannot carry their video`() = runBlocking {
        for ((name, expected) in listOf("vp9" to "V_VP9", "mpeg4" to "V_MPEG4/ISO/ASP")) {
            val fixture = JvmPath.of(checkNotNull(System.getenv("ANI_STREAM_MEDIA_DIR")), "animeko-sample-$name.mkv")
            val bytes = Files.readAllBytes(fixture)
            val client = HttpClient(MockEngine { respond(bytes) })
            try {
                val playback = AvkitPlaybackPreparer(client, { it }, { input, output, tracks ->
                    assertEquals(expected, tracks.videoCodecId)
                    runFfmpeg(avkitConversionArguments(input, output, tracks))
                    runFfmpeg(listOf("-v", "error", "-i", output, "-frames:v", "1", "-f", "null", "-"))
                }, streaming = { _, _ -> error("Unsupported HLS video must use file compatibility") })
                    .prepare(UriMediaData("https://test/video.mkv"))
                playback.close()
            } finally {
                client.close()
            }
        }
    }
}

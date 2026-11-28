/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import me.him188.ani.app.domain.media.hls.HlsProxyRequest
import me.him188.ani.app.domain.media.hls.HlsProxyResponseSink
import me.him188.ani.app.domain.media.hls.HlsProxyServerFactory
import me.him188.ani.app.domain.media.hls.PlatformHlsProxyServerFactory
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.Subtitle
import org.openani.mediamp.source.UriMediaData
import kotlin.math.ceil

/** 转封装与起播并行；认证仅由上游 HTTP 客户端持有，原生任务只访问会话本地地址。 */
internal class AvkitStreamingPlayback(
    private val client: HttpClient,
    private val execute: suspend (List<String>) -> Boolean,
    private val serverFactory: HlsProxyServerFactory = PlatformHlsProxyServerFactory,
    private val createDirectory: () -> SystemPath = { SystemPaths.createTempDirectory("avkit-stream-") },
    private val validateFragment: suspend (SystemPath) -> Boolean = { true },
) {
    suspend fun prepare(
        data: UriMediaData,
        tracks: MediaContainerTrackInfo,
        startPositionMillis: Long,
    ): PlatformMediaPlayback {
        val directory = try {
            createDirectory()
        } catch (failure: Throwable) {
            client.close()
            throw failure
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO_)
        val server = try {
            serverFactory.create()
        } catch (failure: Throwable) {
            client.close()
            scope.cancel()
            directory.deleteRecursively()
            throw failure
        }
        val prefix = "/${directory.name}/"
        val base = "http://127.0.0.1:${server.port}$prefix"
        val fragmentedMp4 = tracks.videoCodecId != "V_MPEG4/ISO/AVC"
        val extension = if (fragmentedMp4) "m4s" else "ts"
        val source = directory.resolve(if (fragmentedMp4) "stream.mp4" else "stream.ts")
        val durations = MutableStateFlow<List<Double>>(emptyList())
        val peakBitrates = MutableStateFlow(0L to 0L)
        val complete = MutableStateFlow(false)
        val closed = atomic(false)
        val conversion = scope.async {
            check(execute(avkitStreamingArguments(base + "source", source.absolutePath, directory, tracks))) { "Streaming remux failed" }
        }
        val indexing = scope.async {
            while (!SystemFileSystem.exists(source.path)) {
                if (conversion.isCompleted) conversion.await()
                delay(50)
            }
            avkitGrowingFileSource(source).buffered().use { input ->
                val publish: (AvkitStreamSegment) -> Unit = { segment ->
                    SystemFileSystem.sink(directory.resolve("segment-${segment.index}.$extension").path).buffered().use { it.write(segment.bytes) }
                    val iframe = segment.iframeBytes ?: avkitTsIframe(segment.bytes)
                    SystemFileSystem.sink(directory.resolve("iframe-${segment.index}.$extension").path).buffered().use { it.write(iframe) }
                    peakBitrates.value = maxOf(peakBitrates.value.first, ceil(segment.bytes.size * 8 / segment.durationSeconds).toLong()) to
                            maxOf(peakBitrates.value.second, ceil(iframe.size * 8 / segment.durationSeconds).toLong())
                    durations.value = durations.value + segment.durationSeconds
                }
                if (fragmentedMp4) {
                    val segmenter = AvkitFmp4Segmenter({ initialization ->
                        SystemFileSystem.sink(directory.resolve("init.mp4").path).buffered().use { it.write(initialization) }
                    }, publish)
                    while (isActive) {
                        val box = readGrowingMp4Box(input) {
                            if (conversion.isCompleted) {
                                conversion.await()
                                true
                            } else false
                        }
                        if (box == null) break
                        segmenter.box(box)
                    }
                    segmenter.finish()
                } else {
                    val segmenter = AvkitTsSegmenter(publish)
                    while (isActive) {
                        if (input.request(188)) segmenter.packet(input.readByteArray(188)) else {
                            if (conversion.isCompleted) {
                                conversion.await()
                                while (input.request(188)) segmenter.packet(input.readByteArray(188))
                                check(input.exhausted()) { "Truncated streaming TS packet" }
                                segmenter.finish()
                                break
                            }
                            delay(50)
                        }
                    }
                }
            }
            SystemFileSystem.delete(source.path)
            complete.value = true
        }
        fun close() {
            if (!closed.compareAndSet(false, true)) return
            server.close()
            client.close()
            scope.cancel()
            CoroutineScope(Dispatchers.IO_ + SupervisorJob()).launch {
                conversion.join()
                indexing.join()
                directory.deleteRecursively()
            }
        }
        server.start { request, sink ->
            if (!request.path.startsWith(prefix)) {
                respond(sink, 404, "text/plain", byteArrayOf())
            } else when (val name = request.path.removePrefix(prefix)) {
                "source" -> relay(data, request, sink)
                "master.m3u8" -> {
                    val (bandwidth, iframeBandwidth) = peakBitrates.value
                    respond(sink, 200, "application/vnd.apple.mpegurl", AvkitTsSegmenter.masterPlaylist(bandwidth, iframeBandwidth).encodeToByteArray())
                }
                "playlist.m3u8", "iframes.m3u8" -> {
                    if (indexing.isCancelled) respond(sink, 503, "text/plain", byteArrayOf()) else
                        respond(sink, 200, "application/vnd.apple.mpegurl", AvkitTsSegmenter.playlist(durations.value, complete.value, fragmentedMp4, iframes = name == "iframes.m3u8").encodeToByteArray())
                }
                else -> {
                    val subtitle = Regex("subtitle-(\\d+)\\.live\\.srt").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
                    val segment = Regex("(?:segment|iframe)-(\\d+)\\.$extension").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
                    when {
                        name == "init.mp4" && fragmentedMp4 && durations.value.isNotEmpty() -> {
                            val bytes = SystemFileSystem.source(directory.resolve(name).path).buffered().use { it.readByteArray() }
                            respondSegment(sink, request, bytes, "video/mp4")
                        }
                        subtitle != null && tracks.subtitles.any { it.index == subtitle } -> {
                            val output = Buffer()
                            val file = directory.resolve("subtitle-$subtitle.mks")
                            if (SystemFileSystem.exists(file.path)) {
                                // 完整 Cluster 已写入 output；正在写入的尾部保留到下次轮询。
                                runCatching { SystemFileSystem.source(file.path).buffered().use { exportMatroskaSubtitleAsSrt(it, output) } }
                            }
                            respond(sink, 200, "application/x-subrip", output.readByteArray(), "X-Ani-Complete: ${if (complete.value) 1 else 0}\r\n")
                        }
                        segment != null && segment in durations.value.indices -> {
                            val bytes = SystemFileSystem.source(directory.resolve(name).path).buffered().use { it.readByteArray() }
                            respondSegment(sink, request, bytes, if (fragmentedMp4) "video/iso.segment" else "video/mp2t")
                        }
                        else -> respond(sink, 404, "text/plain", byteArrayOf())
                    }
                }
            }
        }
        return try {
            withTimeout(45_000) {
                while (true) {
                    if (indexing.isCompleted) indexing.await()
                    val available = durations.value.sum()
                    if (durations.value.isNotEmpty() &&
                        (complete.value || available >= startPositionMillis.coerceAtLeast(0) / 1000.0 + 12)) break
                    delay(50)
                }
            }
            if (fragmentedMp4) withContext(Dispatchers.IO_) {
                val validationFile = directory.resolve("validation.mp4")
                try {
                    SystemFileSystem.sink(validationFile.path).buffered().use { output ->
                        for (name in listOf("init.mp4", "segment-0.m4s")) {
                            SystemFileSystem.source(directory.resolve(name).path).buffered().use { output.transferFrom(it) }
                        }
                    }
                    check(validateFragment(validationFile)) { "The device cannot decode this streaming video" }
                } finally {
                    SystemFileSystem.delete(validationFile.path)
                }
            }
            val subtitles = tracks.subtitles.map { track ->
                Subtitle(base + "subtitle-${track.index}.live.srt", "application/x-subrip", track.language, track.name)
            }
            logger.info { "Streaming AVKit media ready: ${durations.value.sum()} seconds buffered; conversionComplete=${complete.value}" }
            PlatformMediaPlayback(
                UriMediaData(
                    base + "master.m3u8",
                    extraFiles = MediaExtraFiles(data.extraFiles.subtitles + subtitles),
                    options = data.options,
                ),
                ::close,
            )
        } catch (timeout: TimeoutCancellationException) {
            close()
            throw IllegalStateException("Streaming startup timed out", timeout)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private suspend fun relay(data: UriMediaData, request: HlsProxyRequest, sink: HlsProxyResponseSink) {
        client.prepareGet(data.uri) {
            expectSuccess = false
            data.headers.filterKeys { !it.equals(HttpHeaders.Range, true) }.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.AcceptEncoding, "identity")
            request.headers["range"]?.let { header(HttpHeaders.Range, it) }
        }.execute { response ->
            val headers = buildString {
                append("HTTP/1.1 ${response.status.value} ${response.status.description}\r\nConnection: close\r\n")
                for (name in listOf(HttpHeaders.ContentLength, HttpHeaders.ContentRange, HttpHeaders.AcceptRanges, HttpHeaders.ContentType)) {
                    response.headers[name]?.let { append("$name: $it\r\n") }
                }
                append("\r\n")
            }
            sink.write(headers.encodeToByteArray())
            val channel = response.bodyAsChannel()
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = channel.readAvailable(bytes)
                if (count < 0) break
                if (count > 0) sink.write(bytes, 0, count)
            }
        }
    }

    private suspend fun respondSegment(sink: HlsProxyResponseSink, request: HlsProxyRequest, bytes: ByteArray, type: String) {
        val range = request.headers["range"]?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
        val from = range?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val to = (range?.groupValues?.get(2)?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
        if (from !in bytes.indices || to < from) return respond(sink, 416, "text/plain", byteArrayOf())
        respond(sink, if (range == null) 200 else 206, type, bytes.copyOfRange(from, to + 1),
            if (range == null) "" else "Content-Range: bytes $from-$to/${bytes.size}\r\n")
    }

    private suspend fun respond(sink: HlsProxyResponseSink, status: Int, type: String, body: ByteArray, extra: String = "") {
        sink.write("HTTP/1.1 $status Response\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n$extra\r\n".encodeToByteArray())
        sink.write(body)
    }

    companion object {
        private val logger = logger<AvkitStreamingPlayback>()
    }
}

internal expect fun avkitGrowingFileSource(path: SystemPath): RawSource

internal fun avkitStreamingArguments(
    input: String,
    output: String,
    directory: SystemPath,
    tracks: MediaContainerTrackInfo,
): List<String> = buildList {
    require(tracks.videoCodecId in setOf("V_MPEG4/ISO/AVC", "V_MPEGH/ISO/HEVC", "V_AV1")) {
        "The video requires file compatibility processing"
    }
    addAll(
        listOf(
            "-nostdin", "-y", "-v", "error", "-copyts", "-start_at_zero", "-rw_timeout", "15000000", "-i", input,
            "-map", "0:v:0", "-map", "0:a:0?", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ac", "2", "-sn",
        ),
    )
    if (tracks.videoCodecId != "V_MPEG4/ISO/AVC") {
        addAll(
            listOf(
                "-tag:v", if (tracks.videoCodecId == "V_AV1") "av01" else "hvc1",
                "-movflags", "+empty_moov+default_base_moof+frag_keyframe",
                "-min_frag_duration", "2000000", "-flush_packets", "1", "-f", "mp4", output,
            ),
        )
    } else {
        addAll(listOf("-mpegts_start_pid", "256", "-mpegts_flags", "+pat_pmt_at_frames", "-flush_packets", "1", "-f", "mpegts", output))
    }
    for (track in tracks.subtitles) {
        addAll(
            listOf(
                "-map", "0:s:${track.index}", "-c:s", "srt", "-f", "matroska", "-cluster_time_limit", "1000",
                "-flush_packets", "1", "-live", "1", "-avoid_negative_ts", "disabled",
                directory.resolve("subtitle-${track.index}.mks").absolutePath,
            ),
        )
    }
}

/** 等待完整 box，避免将文件写入中尚未完成的媒体分片发布给播放器。 */
internal suspend fun readGrowingMp4Box(input: Source, completed: suspend () -> Boolean): ByteArray? {
    suspend fun awaitBytes(count: Int): Boolean {
        while (!input.request(count.toLong())) {
            if (completed()) return input.request(count.toLong())
            delay(50)
        }
        return true
    }
    if (!awaitBytes(8)) {
        require(input.exhausted()) { "Truncated MP4 box header" }
        return null
    }
    var header = input.readByteArray(8)
    var size = AvkitFmp4Segmenter.readUInt(header, 0)
    if (size == 1L) {
        check(awaitBytes(8)) { "Truncated extended MP4 header" }
        header += input.readByteArray(8)
        require(AvkitFmp4Segmenter.readUInt(header, 8) == 0L) { "Oversized MP4 box" }
        size = AvkitFmp4Segmenter.readUInt(header, 12)
    }
    require(size in header.size.toLong()..32L * 1024 * 1024) { "MP4 box exceeds streaming size limit" }
    val remaining = size.toInt() - header.size
    check(awaitBytes(remaining)) { "Truncated MP4 box" }
    return header + input.readByteArray(remaining)
}

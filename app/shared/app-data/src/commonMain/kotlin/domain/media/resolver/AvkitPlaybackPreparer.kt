/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.Subtitle
import org.openani.mediamp.source.UriMediaData

/**
 * 将 AVKit 需要兼容处理的媒体准备为可 seek 的 MP4，文本字幕通过 extraFiles 传递。
 * 下载按块落盘，起播需要等待下载和封装或转码完成；会话结束、加载失败和取消时删除临时文件。
 */
internal class AvkitPlaybackPreparer(
    private val client: HttpClient,
    private val fileUri: (String) -> String,
    private val remux: suspend (input: String, output: String, tracks: MediaContainerTrackInfo) -> Unit,
    private val createDirectory: () -> SystemPath = { SystemPaths.createTempDirectory("avkit-media-") },
    private val extractSubtitles: suspend (input: String, directory: SystemPath, tracks: MediaContainerTrackInfo) -> List<Subtitle> = { _, _, _ -> emptyList() },
    private val normalizeContainer: suspend (input: String, output: String) -> Unit = { _, _ -> error("Container normalization is unavailable") },
    private val localPath: (String) -> String? = { null },
) {
    suspend fun prepare(data: UriMediaData): PlatformMediaPlayback {
        val extension = Url(data.uri).encodedPath.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        if (extension in DIRECT_EXTENSIONS) return PlatformMediaPlayback(data)
        val prefix = if (extension in COMPATIBILITY_EXTENSIONS || extension in MP4_EXTENSIONS) null else readRange(data, 0, 64)
        val matroska = extension in setOf("mkv", "webm", "mka") || prefix?.let {
            it.size >= 4 && it[0] == 0x1a.toByte() && it[1] == 0x45.toByte() &&
                    it[2] == 0xdf.toByte() && it[3] == 0xa3.toByte()
        } == true
        val mp4 = extension in MP4_EXTENSIONS || prefix?.let { it.size >= 8 && it.decodeToString(4, 8) == "ftyp" } == true
        val mp4Tracks = if (mp4) mp4CompatibilityTracks(readMp4SampleEntries { offset, size -> readRange(data, offset, size) }) else null
        if (mp4 && mp4Tracks == null) return PlatformMediaPlayback(data)
        if (!mp4 && !matroska && extension !in COMPATIBILITY_EXTENSIONS) return PlatformMediaPlayback(data)
        var temporaryDirectory: SystemPath? = null
        return try {
            withContext(Dispatchers.IO_) {
                val directory = createDirectory().also { temporaryDirectory = it }
                var input = directory.resolve("source.media")
                val output = directory.resolve("playback.mp4")
                logger.info { "Preparing media for AVKit: copying media to temporary storage" }
                val local = localPath(data.uri)
                if (local != null) {
                    SystemFileSystem.source(Path(local)).buffered().use { source ->
                        SystemFileSystem.sink(input.path).buffered().use { sink ->
                            val buffer = Buffer()
                            while (!source.exhausted()) {
                                coroutineContext.ensureActive()
                                val count = source.readAtMostTo(buffer, 64 * 1024)
                                if (count < 0) break
                                sink.write(buffer, count)
                            }
                        }
                    }
                } else client.prepareGet(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }.execute { response ->
                    SystemFileSystem.sink(input.path).buffered().use { sink ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = channel.readAvailable(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                if (!matroska && !mp4) {
                    val normalized = directory.resolve("source.mkv")
                    normalizeContainer(input.absolutePath, normalized.absolutePath)
                    SystemFileSystem.delete(input.path)
                    input = normalized
                }
                val tracks = mp4Tracks ?: SystemFileSystem.source(input.path).buffered().use(::readMediaContainerTrackInfo)
                logger.info { "Preparing media for AVKit: converting container and codecs" }
                remux(input.absolutePath, output.absolutePath, tracks)
                val subtitles = try {
                    extractSubtitles(input.absolutePath, directory, tracks)
                } catch (failure: Exception) {
                    coroutineContext.ensureActive()
                    logger.warn(failure) { "Text subtitle extraction failed; playing the prepared video" }
                    emptyList()
                }
                coroutineContext.ensureActive()
                SystemFileSystem.delete(input.path)
                val result = PlatformMediaPlayback(
                    UriMediaData(
                        fileUri(output.absolutePath),
                        extraFiles = if (subtitles.isEmpty()) data.extraFiles else MediaExtraFiles(data.extraFiles.subtitles + subtitles),
                        options = data.options,
                    ),
                    cleanup = { directory.deleteRecursively() },
                )
                logger.info { "Compatible MP4 is ready for AVKit" }
                result
            }
        } catch (failure: Throwable) {
            temporaryDirectory?.deleteRecursively()
            throw failure
        }
    }

    private suspend fun readRange(data: UriMediaData, offset: Long, count: Int): ByteArray {
        localPath(data.uri)?.let { path ->
            return withContext(Dispatchers.IO_) {
                SystemFileSystem.source(Path(path)).buffered().use { source ->
                    source.skip(offset)
                    val bytes = Buffer()
                    while (bytes.size < count && !source.exhausted()) bytes.writeByte(source.readByte())
                    bytes.readByteArray()
                }
            }
        }
        return client.prepareGet(data.uri) {
            data.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=$offset-${offset + count - 1}")
        }.execute { response ->
            if (offset > 0 && response.status != HttpStatusCode.PartialContent) return@execute byteArrayOf()
            val bytes = ByteArray(count)
            var size = 0
            val channel = response.bodyAsChannel()
            while (size < bytes.size) {
                val count = channel.readAvailable(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            bytes.copyOf(size)
        }
    }

    private companion object {
        val DIRECT_EXTENSIONS = setOf("m3u8", "m3u", "m4s", "mp3", "aac")
        val MP4_EXTENSIONS = setOf("mp4", "m4v", "mov", "3gp", "3g2")
        val COMPATIBILITY_EXTENSIONS = setOf("mkv", "webm", "mka", "avi", "wmv", "asf", "rm", "rmvb", "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "ogv", "ogg", "flv")
        val logger = logger<AvkitPlaybackPreparer>()
    }
}

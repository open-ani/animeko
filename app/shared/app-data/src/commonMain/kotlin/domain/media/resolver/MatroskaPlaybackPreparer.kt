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
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.Subtitle
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.coroutineContext

/**
 * 为 AVKit 将完整的 Matroska 下载到临时目录并转封装为可 seek 的 MP4。
 * 下载按块落盘，转封装只复制音视频编码；起播需要等待下载和转封装完成。
 * 会话结束、加载失败和取消时删除临时文件。普通 MP4、HLS 等地址保持直接播放。
 */
internal class MatroskaPlaybackPreparer(
    private val client: HttpClient,
    private val fileUri: (String) -> String,
    private val remux: suspend (input: String, output: String, tracks: MatroskaTrackInfo) -> Unit,
    private val createDirectory: () -> SystemPath = { SystemPaths.createTempDirectory("avkit-matroska-") },
    private val extractSubtitles: suspend (input: String, directory: SystemPath, tracks: MatroskaTrackInfo) -> List<Subtitle> = { _, _, _ -> emptyList() },
) {
    suspend fun prepare(data: UriMediaData): PlatformMediaPlayback {
        val extension = Url(data.uri).encodedPath.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        if (extension in DIRECT_EXTENSIONS) return PlatformMediaPlayback(data)
        if (extension != "mkv" && !hasMatroskaHeader(data)) return PlatformMediaPlayback(data)
        var temporaryDirectory: SystemPath? = null
        return try {
            withContext(Dispatchers.IO_) {
                val directory = createDirectory().also { temporaryDirectory = it }
                val input = directory.resolve("source.mkv")
                val output = directory.resolve("playback.mp4")
                logger.info { "Preparing Matroska for AVKit: downloading media to temporary storage" }
                client.prepareGet(data.uri) {
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
                val tracks = SystemFileSystem.source(input.path).buffered().use(::readMatroskaTrackInfo)
                logger.info { "Preparing Matroska for AVKit: remuxing media" }
                remux(input.absolutePath, output.absolutePath, tracks)
                val subtitles = extractSubtitles(input.absolutePath, directory, tracks)
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
                logger.info { "Matroska MP4 is ready for AVKit" }
                result
            }
        } catch (failure: Throwable) {
            temporaryDirectory?.deleteRecursively()
            throw failure
        }
    }

    private suspend fun hasMatroskaHeader(data: UriMediaData): Boolean =
        client.prepareGet(data.uri) {
            data.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=0-63")
        }.execute { response ->
            val bytes = ByteArray(64)
            var size = 0
            val channel = response.bodyAsChannel()
            while (size < bytes.size) {
                val count = channel.readAvailable(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            size >= 4 && bytes[0] == 0x1a.toByte() && bytes[1] == 0x45.toByte() &&
                    bytes[2] == 0xdf.toByte() && bytes[3] == 0xa3.toByte() &&
                    bytes.decodeToString(0, size).contains("matroska")
        }

    private companion object {
        val DIRECT_EXTENSIONS = setOf("mp4", "m4v", "mov", "m3u8", "m3u", "ts", "m4s", "mp3", "aac")
        val logger = logger<MatroskaPlaybackPreparer>()
    }
}

/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlin.coroutines.coroutineContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.ensureActive
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.ffmpeg.FFmpegKit
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.Subtitle
import org.openani.mediamp.source.UriMediaData
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

/** AVKit 使用兼容的 MP4；视频帧解码验证失败时使用软件转码，文本字幕通过 extraFiles 传递。 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun preparePlatformMediaPlayback(data: MediaData): PlatformMediaPlayback {
    if (data !is UriMediaData || !(data.uri.startsWith("http") || data.uri.startsWith("file:"))) return PlatformMediaPlayback(data)
    return createDefaultHttpClient().use { client ->
        AvkitPlaybackPreparer(
            client = client,
            fileUri = { NSURL.fileURLWithPath(it).absoluteString!! },
            remux = { input, output, tracks ->
                var result = FFmpegKit().execute(avkitConversionArguments(input, output, tracks))
                coroutineContext.ensureActive()
                if (!result.isSuccess || !avkitCanDecodeFrame(output)) {
                    result = FFmpegKit().execute(avkitConversionArguments(input, output, tracks, forceTranscode = true))
                    coroutineContext.ensureActive()
                }
                check(result.isSuccess && avkitCanDecodeFrame(output)) {
                    "The media could not be converted to a playable iOS video (FFmpeg exit ${result.exitCode})"
                }
            },
            normalizeContainer = { input, output ->
                val result = FFmpegKit().execute(normalizeAvkitContainerArguments(input, output))
                check(result.isSuccess) {
                    "The media container is unsupported or damaged (FFmpeg exit ${result.exitCode})"
                }
            },
            localPath = { uri -> if (uri.startsWith("file:")) NSURL.URLWithString(uri)?.path else null },
            extractSubtitles = { input, directory, tracks ->
                if (tracks.subtitles.isEmpty()) emptyList() else {
                    val outputs = tracks.subtitles.map { track ->
                        track.index to directory.resolve("subtitle-${track.index}.mks")
                    }
                    val result = FFmpegKit().execute(
                        matroskaSubtitleExportArguments(input, outputs.map { (index, path) -> index to path.absolutePath }),
                    )
                    check(result.isSuccess) { "Failed to extract Matroska subtitles (FFmpeg exit ${result.exitCode})" }
                    val subtitles = tracks.subtitles.map { track ->
                        val normalized = directory.resolve("subtitle-${track.index}.mks")
                        val output = directory.resolve("subtitle-${track.index}.srt")
                        SystemFileSystem.source(normalized.path).buffered().use { source ->
                            SystemFileSystem.sink(output.path).buffered().use { sink ->
                                exportMatroskaSubtitleAsSrt(source, sink)
                            }
                        }
                        SystemFileSystem.delete(normalized.path)
                        Subtitle(
                            uri = NSURL.fileURLWithPath(output.absolutePath).absoluteString!!,
                            mimeType = "application/x-subrip",
                            language = track.language,
                            label = track.name,
                        )
                    }
                    subtitles
                }
            },
        ).prepare(data)
    }
}

/** 同步解码首帧确认 sample entry、像素格式和编码配置可供 AVKit 使用。 */
@OptIn(ExperimentalForeignApi::class)
private fun avkitCanDecodeFrame(path: String): Boolean {
    val asset = AVURLAsset(NSURL.fileURLWithPath(path), null)
    val generator = AVAssetImageGenerator(asset)
    return try {
        val image = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, 600), null, null) ?: return false
        CGImageRelease(image)
        true
    } catch (failure: Exception) {
        logger<AvkitPlaybackPreparer>().warn(failure) { "AVKit video frame validation failed" }
        false
    } finally {
        generator.cancelAllCGImageGeneration()
    }
}

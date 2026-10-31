/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.createDefaultHttpClient
import org.openani.mediamp.ffmpeg.FFmpegKit
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.Subtitle
import org.openani.mediamp.source.UriMediaData
import platform.Foundation.NSURL

/** AVKit 读取 MP4；Matroska 的视频与音频保留编码，外挂字幕通过 extraFiles 传递。 */
internal actual suspend fun preparePlatformMediaPlayback(data: MediaData): PlatformMediaPlayback {
    if (data !is UriMediaData || !data.uri.startsWith("http")) return PlatformMediaPlayback(data)
    return createDefaultHttpClient().use { client ->
        MatroskaPlaybackPreparer(
            client = client,
            fileUri = { NSURL.fileURLWithPath(it).absoluteString!! },
            remux = { input, output, tracks ->
                val videoTag = mp4VideoTagFor(tracks.videoCodecId)
                val result = FFmpegKit().execute(
                    buildList {
                        addAll(
                            listOf(
                                "-nostdin", "-y", "-v", "error", "-i", input,
                                "-map", "0:v:0", "-map", "0:a?", "-sn",
                                "-c:v", "copy", "-c:a", "copy",
                            ),
                        )
                        if (videoTag != null) addAll(listOf("-tag:v", videoTag))
                        addAll(listOf("-movflags", "+faststart", output))
                    },
                )
                check(result.isSuccess) { "Failed to prepare Matroska for AVKit (FFmpeg exit ${result.exitCode})" }
            },
            extractSubtitles = { input, directory, tracks ->
                if (tracks.subtitles.isEmpty()) emptyList() else {
                    val args = mutableListOf("-nostdin", "-y", "-v", "error", "-i", input)
                    val subtitles = tracks.subtitles.map { track ->
                        val output = directory.resolve("subtitle-${track.index}.srt")
                        args += listOf("-map", "0:s:${track.index}", "-c:s", "srt", "-f", "srt", output.absolutePath)
                        Subtitle(
                            uri = NSURL.fileURLWithPath(output.absolutePath).absoluteString!!,
                            mimeType = "application/x-subrip",
                            language = track.language,
                            label = track.name,
                        )
                    }
                    val result = FFmpegKit().execute(args)
                    check(result.isSuccess) { "Failed to extract Matroska subtitles (FFmpeg exit ${result.exitCode})" }
                    subtitles
                }
            },
        ).prepare(data)
    }
}

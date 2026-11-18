/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

/** 支持的编码保留原码流，其余视频使用 MPEG-4 Part 2，音频使用 AAC。 */
internal fun avkitConversionArguments(
    input: String,
    output: String,
    tracks: MediaContainerTrackInfo,
    forceTranscode: Boolean = false,
): List<String> = buildList {
    val transcodeVideo = forceTranscode || tracks.videoCodecId !in setOf(
        "V_MPEG4/ISO/AVC", "V_MPEGH/ISO/HEVC", "V_MPEG4/ISO/ASP", "V_MPEG4/ISO/SP",
    )
    val transcodeAudio = forceTranscode || tracks.audioCodecIds.any {
        it !in setOf("A_AAC", "A_AAC/MPEG2/LC", "A_AAC/MPEG4/LC", "A_AC3", "A_EAC3", "A_ALAC")
    }
    addAll(listOf("-nostdin", "-y", "-v", "error", "-i", input, "-map", "0:v:0", "-map", "0:a?", "-sn"))
    addAll(listOf("-c:v", if (transcodeVideo) "mpeg4" else "copy"))
    if (transcodeVideo) {
        addAll(listOf("-q:v", "3", "-pix_fmt", "yuv420p", "-vf", "scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"))
    } else {
        mp4VideoTagFor(tracks.videoCodecId)?.let { addAll(listOf("-tag:v", it)) }
    }
    addAll(listOf("-c:a", if (transcodeAudio) "aac" else "copy"))
    if (transcodeAudio) addAll(listOf("-b:a", "192k", "-ac", "2"))
    addAll(listOf("-movflags", "+faststart", output))
}

internal fun normalizeAvkitContainerArguments(input: String, output: String): List<String> = listOf(
    "-nostdin", "-y", "-v", "error", "-i", input,
    "-map", "0:v:0", "-map", "0:a?", "-map", "0:s?", "-c", "copy", "-f", "matroska", output,
)

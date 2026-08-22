/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalSerializationApi::class)

package me.him188.ani.datasources.jellyfin

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JellyfinPlaybackQualityMode {
    AUTO,
    ORIGINAL,
    FIXED,
}

/**
 * A Jellyfin playback bitrate preference.
 *
 * Jellyfin interprets [maxBitrate] as a maximum total streaming bitrate. It is not a
 * server-provided resolution variant.
 */
@Serializable
data class JellyfinPlaybackQuality(
    val mode: JellyfinPlaybackQualityMode = JellyfinPlaybackQualityMode.AUTO,
    val maxBitrate: Int? = null,
) {
    init {
        require(mode == JellyfinPlaybackQualityMode.FIXED || maxBitrate == null) {
            "Only FIXED quality can have maxBitrate"
        }
        require(mode != JellyfinPlaybackQualityMode.FIXED || maxBitrate?.let { it > 0 } == true) {
            "FIXED quality requires a positive maxBitrate"
        }
    }

    companion object {
        val Auto = JellyfinPlaybackQuality(JellyfinPlaybackQualityMode.AUTO)
        val Original = JellyfinPlaybackQuality(JellyfinPlaybackQualityMode.ORIGINAL)

        fun fixed(maxBitrate: Int): JellyfinPlaybackQuality {
            return JellyfinPlaybackQuality(JellyfinPlaybackQualityMode.FIXED, maxBitrate)
        }
    }
}

data class JellyfinPlaybackPlan(
    val uri: String,
    val quality: JellyfinPlaybackQuality,
    val effectiveMaxBitrate: Int?,
    val sourceBitrate: Int?,
    val sourceVideoCodec: String?,
    val mediaSourceId: String,
    val playSessionId: String?,
    val isTranscoding: Boolean,
    val audioStreamIndices: List<Int> = emptyList(),
    val selectedAudioStreamIndex: Int? = null,
)

/**
 * Jellyfin could not provide a stream that satisfies the requested quality.
 *
 * The server may reject the client profile, the user or media source may not support transcoding,
 * or no compatible stream may exist. Callers may explicitly fall back to
 * [JellyfinPlaybackQuality.Original], but must not present that fallback as if the requested
 * bitrate had been applied.
 */
class JellyfinPlaybackUnavailableException(
    val quality: JellyfinPlaybackQuality,
    val supportsTranscoding: Boolean,
) : IllegalStateException(
    "Jellyfin cannot provide ${quality.mode} playback" +
            quality.maxBitrate?.let { " at $it bps" }.orEmpty() +
            if (supportsTranscoding) {
                " because the server did not return a playable stream"
            } else {
                " because Jellyfin did not expose a compatible transcoding stream"
            },
)

/**
 * Jellyfin Web uses client-defined bitrate presets. The server uses the selected value as
 * MaxStreamingBitrate when negotiating PlaybackInfo.
 */
fun jellyfinPlaybackQualities(
    sourceBitrate: Int?,
): List<JellyfinPlaybackQuality> {
    val referenceBitrate = sourceBitrate?.takeIf { it > 0 }

    val presets = listOf(
        120_000_000,
        80_000_000,
        60_000_000,
        40_000_000,
        20_000_000,
        15_000_000,
        10_000_000,
        8_000_000,
        6_000_000,
        4_000_000,
        3_000_000,
        1_500_000,
        720_000,
        420_000,
    )

    val fixed = if (referenceBitrate == null) {
        presets
    } else {
        presets.filter { it < referenceBitrate }
    }

    return buildList {
        add(JellyfinPlaybackQuality.Auto)
        add(JellyfinPlaybackQuality.Original)
        fixed.forEach { add(JellyfinPlaybackQuality.fixed(it)) }
    }
}

@Serializable
internal data class JellyfinPlaybackInfoRequest(
    @SerialName("UserId")
    val userId: String,
    @SerialName("MaxStreamingBitrate")
    val maxStreamingBitrate: Int,
    @SerialName("StartTimeTicks")
    val startTimeTicks: Long,
    @SerialName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerialName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int,
    @SerialName("DeviceProfile")
    val deviceProfile: JellyfinDeviceProfile,
    @SerialName("EnableDirectPlay")
    val enableDirectPlay: Boolean = true,
    @SerialName("EnableDirectStream")
    val enableDirectStream: Boolean = true,
    @SerialName("EnableTranscoding")
    val enableTranscoding: Boolean = true,
    @SerialName("AllowVideoStreamCopy")
    val allowVideoStreamCopy: Boolean = true,
    @SerialName("AllowAudioStreamCopy")
    val allowAudioStreamCopy: Boolean = true,
    @SerialName("AutoOpenLiveStream")
    val autoOpenLiveStream: Boolean = true,
)

@Serializable
internal data class JellyfinDeviceProfile(
    @SerialName("Name")
    val name: String = "Animeko",
    @SerialName("MaxStreamingBitrate")
    val maxStreamingBitrate: Int,
    @SerialName("MaxStaticBitrate")
    val maxStaticBitrate: Int,
    @SerialName("DirectPlayProfiles")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val directPlayProfiles: List<JellyfinDirectPlayProfile> = defaultDirectPlayProfiles,
    @SerialName("TranscodingProfiles")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val transcodingProfiles: List<JellyfinTranscodingProfile> = defaultTranscodingProfiles,
    @SerialName("SubtitleProfiles")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val subtitleProfiles: List<JellyfinSubtitleProfile> = defaultSubtitleProfiles,
)

@Serializable
internal data class JellyfinDirectPlayProfile(
    @SerialName("Container")
    val container: String,
    @SerialName("AudioCodec")
    val audioCodec: String,
    @SerialName("VideoCodec")
    val videoCodec: String,
    @SerialName("Type")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "Video",
)

@Serializable
internal data class JellyfinTranscodingProfile(
    @SerialName("Container")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val container: String = "ts",
    @SerialName("Type")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "Video",
    @SerialName("VideoCodec")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val videoCodec: String = "h264",
    @SerialName("AudioCodec")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val audioCodec: String = "aac,mp3",
    @SerialName("Protocol")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val protocol: String = "hls",
    @SerialName("Context")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val context: String = "Streaming",
    @SerialName("MaxAudioChannels")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val maxAudioChannels: String = "2",
    @SerialName("MinSegments")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val minSegments: Int = 2,
)

@Serializable
internal data class JellyfinSubtitleProfile(
    @SerialName("Format")
    val format: String,
    @SerialName("Method")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val method: String = "External",
)

@Serializable
internal data class JellyfinPlaybackInfoResponse(
    @SerialName("MediaSources")
    val mediaSources: List<JellyfinPlaybackMediaSource> = emptyList(),
    @SerialName("PlaySessionId")
    val playSessionId: String? = null,
    @SerialName("ErrorCode")
    val errorCode: String? = null,
)

@Serializable
internal data class JellyfinPlaybackMediaSource(
    @SerialName("Id")
    val id: String,
    @SerialName("Bitrate")
    val bitrate: Int? = null,
    @SerialName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream")
    val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding")
    val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl")
    val transcodingUrl: String? = null,
    @SerialName("DefaultAudioStreamIndex")
    val defaultAudioStreamIndex: Int? = null,
    @SerialName("MediaStreams")
    val mediaStreams: List<JellyfinPlaybackMediaStream> = emptyList(),
)

@Serializable
internal data class JellyfinPlaybackMediaStream(
    @SerialName("Index")
    val index: Int = -1,
    @SerialName("Type")
    val type: String,
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("BitRate")
    val bitrate: Int? = null,
)

@Serializable
internal data class JellyfinEndpointInfo(
    @SerialName("IsInNetwork")
    val isInNetwork: Boolean = false,
)

private val defaultDirectPlayProfiles = listOf(
    JellyfinDirectPlayProfile(
        container = "mp4,m4v,mov,mkv,webm,ts,mpegts",
        audioCodec = "aac,mp3,opus,flac,vorbis,ac3,eac3",
        videoCodec = "h264,hevc,vp8,vp9,av1,mpeg2video",
    ),
)

private val defaultTranscodingProfiles = listOf(JellyfinTranscodingProfile())

private val defaultSubtitleProfiles = listOf(
    JellyfinSubtitleProfile("vtt"),
    JellyfinSubtitleProfile("ass"),
    JellyfinSubtitleProfile("ssa"),
    JellyfinSubtitleProfile("srt"),
)

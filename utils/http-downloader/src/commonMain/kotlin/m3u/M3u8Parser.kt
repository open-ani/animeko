/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader.m3u

/**
 * Interface for parsing m3u8 playlists
 */
interface M3u8Parser {
    /**
     * Parse m3u8 content from a string
     * @param content The m3u8 content as a string
     * @return An M3u8Playlist object representing the parsed content
     */
    @Throws(M3uFormatException::class)
    fun parse(content: String): M3u8Playlist
}

class M3uFormatException(override val message: String?) : RuntimeException()

/**
 * Sealed class representing an M3U8 playlist
 * Can be either a master playlist (with variant streams) or a media playlist (with segments)
 */
sealed class M3u8Playlist {
    abstract val version: Int
    abstract val tags: Map<String, String>

    /**
     * Master playlist containing variant streams for adaptive bitrate streaming
     */
    data class MasterPlaylist(
        override val version: Int = 3,
        val variants: List<VariantStream> = emptyList(),
        override val tags: Map<String, String> = emptyMap(),
    ) : M3u8Playlist()

    /**
     * Media playlist containing media segments
     */
    data class MediaPlaylist(
        override val version: Int = 3,
        val targetDuration: Int? = null,
        val mediaSequence: Int = 0,
        val segments: List<MediaSegment> = emptyList(),
        val isEndlist: Boolean = false,
        override val tags: Map<String, String> = emptyMap(),
    ) : M3u8Playlist()
}

/**
 * Represents a byte range in an M3U8 segment
 */
data class ByteRange(
    val length: Long,
    val offset: Long? = null,
)

/**
 * Represents a media segment in an M3U8 playlist
 */
data class MediaSegment(
    val duration: Float,
    val uri: String,
    val title: String? = null,
    val isDiscontinuity: Boolean = false,
    val byteRange: ByteRange? = null,
    val keys: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
)

/**
 * Represents a variant stream in a master playlist
 */
data class VariantStream(
    val uri: String,
    val bandwidth: Int,
    val averageBandwidth: Int? = null,
    val codecs: String? = null,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val audio: String? = null,
    val video: String? = null,
    val subtitles: String? = null,
    val closedCaptions: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * Default implementation of M3u8Parser
 */
object DefaultM3u8Parser : M3u8Parser {
    override fun parse(content: String): M3u8Playlist {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty() || !lines[0].trimStart().startsWith("#EXTM3U")) {
            throw M3uFormatException("Invalid M3U8 format, must start with #EXTM3U")
        }

        var version = 3
        var targetDuration: Int? = null
        var mediaSequence = 0
        var isEndlist = false
        val segments = mutableListOf<MediaSegment>()
        val variants = mutableListOf<VariantStream>()
        val tags = mutableMapOf<String, String>()

        var i = 1 // Skip #EXTM3U

        // For current segment being built
        var currentSegmentDuration: Float? = null
        var currentSegmentTitle: String? = null
        var currentSegmentDiscontinuity = false
        var currentSegmentByteRange: ByteRange? = null
        val currentSegmentKeys = mutableMapOf<String, String>()
        val currentSegmentTags = mutableMapOf<String, String>()

        // For current variant being built
        var currentVariantAttributes = mutableMapOf<String, String>()

        while (i < lines.size) {
            val line = lines[i++].trim()

            if (line.startsWith("#")) {
                // This is a tag
                when {
                    line.startsWith("#EXT-X-VERSION:") -> {
                        version = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-TARGETDURATION:") -> {
                        targetDuration = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-ENDLIST") -> {
                        isEndlist = true
                    }

                    line.startsWith("#EXTINF:") -> {
                        // Format: #EXTINF:duration[,title]
                        val valueStr = line.substringAfter(":")
                        currentSegmentDuration = valueStr.substringBefore(",").toFloat()
                        if (valueStr.contains(",")) {
                            currentSegmentTitle = valueStr.substringAfter(",").trim()
                        }
                    }

                    line.startsWith("#EXT-X-DISCONTINUITY") -> {
                        currentSegmentDiscontinuity = true
                    }

                    line.startsWith("#EXT-X-BYTERANGE:") -> {
                        currentSegmentByteRange = parseByteRange(line.substringAfter(":").trim())
                    }

                    line.startsWith("#EXT-X-KEY:") -> {
                        val keyAttributes = parseAttributes(line.substringAfter(":").trim())
                        currentSegmentKeys.putAll(keyAttributes)
                    }

                    line.startsWith("#EXT-X-STREAM-INF:") -> {
                        currentVariantAttributes = parseAttributes(line.substringAfter(":").trim())
                    }

                    else -> {
                        // Store other tags
                        if (line.contains(":")) {
                            val tagName = line.substringBefore(":")
                            val tagValue = line.substringAfter(":")

                            if (currentSegmentDuration != null) {
                                // Tag belongs to the current segment
                                currentSegmentTags[tagName] = tagValue
                            } else {
                                // Tag belongs to the playlist
                                tags[tagName] = tagValue
                            }
                        } else {
                            if (currentSegmentDuration != null) {
                                currentSegmentTags[line] = ""
                            } else {
                                tags[line] = ""
                            }
                        }
                    }
                }
            } else {
                // This is a URI line
                val uri = line

                if (currentVariantAttributes.isNotEmpty()) {
                    // This is a variant stream URI
                    variants.add(
                        VariantStream(
                            uri = uri,
                            bandwidth = currentVariantAttributes["BANDWIDTH"]?.toIntOrNull() ?: 0,
                            averageBandwidth = currentVariantAttributes["AVERAGE-BANDWIDTH"]?.toIntOrNull(),
                            codecs = currentVariantAttributes["CODECS"]?.trim('"'),
                            resolution = currentVariantAttributes["RESOLUTION"],
                            frameRate = currentVariantAttributes["FRAME-RATE"]?.toFloatOrNull(),
                            audio = currentVariantAttributes["AUDIO"],
                            video = currentVariantAttributes["VIDEO"],
                            subtitles = currentVariantAttributes["SUBTITLES"],
                            closedCaptions = currentVariantAttributes["CLOSED-CAPTIONS"],
                            attributes = currentVariantAttributes.toMap(),
                        ),
                    )

                    currentVariantAttributes.clear()
                } else if (currentSegmentDuration != null) {
                    // This is a media segment URI
                    segments.add(
                        MediaSegment(
                            duration = currentSegmentDuration,
                            uri = uri,
                            title = currentSegmentTitle,
                            isDiscontinuity = currentSegmentDiscontinuity,
                            byteRange = currentSegmentByteRange,
                            keys = currentSegmentKeys.toMap(),
                            tags = currentSegmentTags.toMap(),
                        ),
                    )

                    // Reset segment values for next segment
                    currentSegmentDuration = null
                    currentSegmentTitle = null
                    currentSegmentDiscontinuity = false
                    currentSegmentByteRange = null
                    currentSegmentKeys.clear()
                    currentSegmentTags.clear()
                }
            }
        }

        return if (variants.isNotEmpty()) {
            M3u8Playlist.MasterPlaylist(
                version = version,
                variants = variants,
                tags = tags,
            )
        } else {
            M3u8Playlist.MediaPlaylist(
                version = version,
                targetDuration = targetDuration,
                mediaSequence = mediaSequence,
                segments = segments,
                isEndlist = isEndlist,
                tags = tags,
            )
        }
    }

    /**
     * Parse attribute string in the format KEY=VALUE,KEY=VALUE
     * Handles quoted values and commas within quotes
     */
    private fun parseAttributes(attributesString: String): MutableMap<String, String> {
        val attributes = mutableMapOf<String, String>()
        var remaining = attributesString

        while (remaining.isNotEmpty()) {
            // Find the next attribute boundary
            var inQuotes = false
            var commaPos = -1

            for (i in remaining.indices) {
                val c = remaining[i]
                if (c == '"') {
                    inQuotes = !inQuotes
                } else if (c == ',' && !inQuotes) {
                    commaPos = i
                    break
                }
            }

            // Extract the current attribute
            val attribute = if (commaPos >= 0) {
                val attr = remaining.substring(0, commaPos)
                remaining = remaining.substring(commaPos + 1).trim()
                attr
            } else {
                val attr = remaining
                remaining = ""
                attr
            }

            // Parse key-value pair
            val eqPos = attribute.indexOf('=')
            if (eqPos > 0) {
                val key = attribute.substring(0, eqPos).trim()
                val value = attribute.substring(eqPos + 1).trim()

                // Remove surrounding quotes if present
                val cleanValue = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                    value.substring(1, value.length - 1)
                } else {
                    value
                }

                attributes[key] = cleanValue
            }
        }

        return attributes
    }

    /**
     * Parse a #EXT-X-BYTERANGE value (e.g., "500@1000") into a ByteRange object.
     */
    private fun parseByteRange(rangeValue: String): ByteRange {
        val parts = rangeValue.split("@")
        val length = parts[0].toLongOrNull() ?: throw M3uFormatException("Invalid byte range length: ${parts[0]}")
        val offset = if (parts.size > 1) {
            parts[1].toLongOrNull() ?: throw M3uFormatException("Invalid byte range offset: ${parts[1]}")
        } else {
            null
        }
        return ByteRange(length, offset)
    }
}

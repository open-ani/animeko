/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Inflates zlib-compressed ASS/SSA and SRT Block payloads before the BlockGroup is committed.
 * Media3 1.9 otherwise treats compressed bytes as text and truncates them at the first NUL.
 * Compression is declared per track; an omitted ContentCompAlgo means zlib (Matroska default).
 * See https://github.com/androidx/media/issues/2361.
 */
@OptIn(UnstableApi::class)
internal class CompressedSubtitleMatroskaExtractor(
    subtitleParserFactory: SubtitleParser.Factory,
    assHandler: AssHandler,
) : AssMatroskaExtractor(subtitleParserFactory, assHandler) {
    private var compressionAlgorithm: Long? = null
    private var explicitZlib = false
    private val compressedTracks = mutableMapOf<Long, Int>()
    private val blockHeader = ByteArray(11)
    // ass-media exposes the same sample through this field for its overlay TrackOutput.
    private val pendingSubtitle = subtitleSampleField.get(this) as ParsableByteArray

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        when (id) {
            ID_TRACK_ENTRY -> {
                compressionAlgorithm = null
                explicitZlib = false
            }
            ID_CONTENT_COMPRESSION -> compressionAlgorithm = 0
        }
        super.startMasterElement(id, contentPosition, contentSize)
    }

    override fun integerElement(id: Int, value: Long) {
        if (id == ID_CONTENT_COMP_ALGO) {
            compressionAlgorithm = value
            if (value == 0L) {
                explicitZlib = true
                return
            }
        }
        super.integerElement(id, value)
    }

    override fun endMasterElement(id: Int) {
        if (id == ID_TRACK_ENTRY) {
            val track = getCurrentTrack(id)
            if (compressionAlgorithm == 0L && track.codecId in SUBTITLE_PREFIX_SIZES) {
                compressedTracks[track.number.toLong()] = SUBTITLE_PREFIX_SIZES.getValue(track.codecId)
            } else if (explicitZlib) {
                // Preserve Media3's validation for codecs outside this adapter's scope.
                super.integerElement(ID_CONTENT_COMP_ALGO, 0)
            }
        }
        super.endMasterElement(id)
    }

    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        val prefixSize = if (id == ID_BLOCK && compressedTracks.isNotEmpty()) compressedSubtitlePrefixSize(input) else null
        super.binaryElement(id, contentSize, input)
        if (prefixSize != null) {
            // Preserve the Media3 codec-specific text prefix before the compressed payload.
            // BlockGroup end supplies the duration and performs NUL trimming after inflation.
            val sample = pendingSubtitle.data
            val inflated = inflateSubtitle(sample, prefixSize, pendingSubtitle.limit() - prefixSize)
            pendingSubtitle.reset(sample.copyOf(prefixSize) + inflated)
        }
    }

    private fun compressedSubtitlePrefixSize(input: ExtractorInput): Int? {
        try {
            input.peekFully(blockHeader, 0, 1)
            val firstByte = blockHeader[0].toInt() and 0xff
            if (firstByte == 0) return null // Let Media3 report the invalid track number.
            val numberLength = Integer.numberOfLeadingZeros(firstByte) - 23
            input.peekFully(blockHeader, 1, numberLength + 2)
            var number = (firstByte and (0xff ushr numberLength)).toLong()
            for (index in 1 until numberLength) {
                number = (number shl 8) or (blockHeader[index].toLong() and 0xff)
            }
            val prefixSize = compressedTracks[number] ?: return null
            if (blockHeader[numberLength + 2].toInt() and 0x06 != 0) {
                throw ParserException.createForUnsupportedContainerFeature("Laced compressed text subtitles")
            }
            return prefixSize
        } finally {
            input.resetPeekPosition()
        }
    }

    private companion object {
        const val ID_TRACK_ENTRY = 0xAE
        const val ID_CONTENT_COMPRESSION = 0x5034
        const val ID_CONTENT_COMP_ALGO = 0x4254
        const val ID_BLOCK = 0xA1
        const val ASS_SAMPLE_PREFIX = "Dialogue: 0:00:00:00,0:00:00:00,"
        const val SUBRIP_SAMPLE_PREFIX = "1\n00:00:00,000 --> 00:00:00,000\n"
        val SUBTITLE_PREFIX_SIZES = mapOf(
            "S_TEXT/ASS" to ASS_SAMPLE_PREFIX.length,
            "S_TEXT/SSA" to ASS_SAMPLE_PREFIX.length,
            "S_TEXT/UTF8" to SUBRIP_SAMPLE_PREFIX.length,
        )
    }
}

/** A complete zlib stream is required; partial or corrupt bytes must never reach subtitle parsers as text. */
@OptIn(UnstableApi::class)
internal fun inflateSubtitle(data: ByteArray, offset: Int, length: Int): ByteArray {
    val inflater = Inflater()
    try {
        inflater.setInput(data, offset, length)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (output.size() + count > MAX_SUBTITLE_BYTES) {
                throw DataFormatException("Subtitle payload exceeds $MAX_SUBTITLE_BYTES bytes")
            }
            output.write(buffer, 0, count)
            if (count == 0 && !inflater.finished()) {
                throw DataFormatException("Incomplete zlib subtitle payload or unsupported dictionary")
            }
        }
        return output.toByteArray()
    } catch (e: DataFormatException) {
        throw ParserException.createForMalformedContainer("Invalid compressed subtitle payload", e)
    } finally {
        inflater.end()
    }
}

private const val MAX_SUBTITLE_BYTES = 16 * 1024 * 1024

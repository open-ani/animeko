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
 * Inflates zlib-compressed ASS/SSA Block payloads before the BlockGroup is committed.
 * Media3 1.9 otherwise treats compressed bytes as text and truncates them at the first NUL.
 * Compression is declared per track; an omitted ContentCompAlgo means zlib (Matroska default).
 * See https://github.com/androidx/media/issues/2361.
 */
@OptIn(UnstableApi::class)
internal class CompressedAssMatroskaExtractor(
    subtitleParserFactory: SubtitleParser.Factory,
    assHandler: AssHandler,
) : AssMatroskaExtractor(subtitleParserFactory, assHandler) {
    private var compressionAlgorithm: Long? = null
    private var explicitZlib = false
    private val compressedAssTracks = mutableSetOf<Long>()
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
            if (compressionAlgorithm == 0L && track.codecId in ASS_CODECS) {
                compressedAssTracks += track.number.toLong()
            } else if (explicitZlib) {
                // Preserve Media3's validation for codecs outside this adapter's scope.
                super.integerElement(ID_CONTENT_COMP_ALGO, 0)
            }
        }
        super.endMasterElement(id)
    }

    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        val compressed = id == ID_BLOCK && compressedAssTracks.isNotEmpty() && isCompressedAssBlock(input)
        super.binaryElement(id, contentSize, input)
        if (compressed) {
            // Media3 prepends "Dialogue: 0:00:00:00,0:00:00:00," to the raw ASS chunk.
            // BlockGroup end supplies the duration and performs NUL trimming after inflation.
            val prefixSize = ASS_SAMPLE_PREFIX.length
            val sample = pendingSubtitle.data
            val inflated = inflateAssDialogue(sample, prefixSize, pendingSubtitle.limit() - prefixSize)
            pendingSubtitle.reset(sample.copyOf(prefixSize) + inflated)
        }
    }

    private fun isCompressedAssBlock(input: ExtractorInput): Boolean {
        try {
            input.peekFully(blockHeader, 0, 1)
            val firstByte = blockHeader[0].toInt() and 0xff
            if (firstByte == 0) return false // Let Media3 report the invalid track number.
            val numberLength = Integer.numberOfLeadingZeros(firstByte) - 23
            input.peekFully(blockHeader, 1, numberLength + 2)
            var number = (firstByte and (0xff ushr numberLength)).toLong()
            for (index in 1 until numberLength) {
                number = (number shl 8) or (blockHeader[index].toLong() and 0xff)
            }
            if (number !in compressedAssTracks) return false
            if (blockHeader[numberLength + 2].toInt() and 0x06 != 0) {
                throw ParserException.createForUnsupportedContainerFeature("Laced compressed ASS subtitles")
            }
            return true
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
        val ASS_CODECS = setOf("S_TEXT/ASS", "S_TEXT/SSA")
    }
}

/** A complete zlib stream is required; partial or corrupt bytes must never reach libass as text. */
@OptIn(UnstableApi::class)
internal fun inflateAssDialogue(data: ByteArray, offset: Int, length: Int): ByteArray {
    val inflater = Inflater()
    try {
        inflater.setInput(data, offset, length)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (output.size() + count > MAX_ASS_DIALOGUE_BYTES) {
                throw DataFormatException("ASS dialogue exceeds $MAX_ASS_DIALOGUE_BYTES bytes")
            }
            output.write(buffer, 0, count)
            if (count == 0 && !inflater.finished()) {
                throw DataFormatException("Incomplete zlib ASS dialogue or unsupported dictionary")
            }
        }
        return output.toByteArray()
    } catch (e: DataFormatException) {
        throw ParserException.createForMalformedContainer("Invalid compressed ASS dialogue", e)
    } finally {
        inflater.end()
    }
}

private const val MAX_ASS_DIALOGUE_BYTES = 16 * 1024 * 1024

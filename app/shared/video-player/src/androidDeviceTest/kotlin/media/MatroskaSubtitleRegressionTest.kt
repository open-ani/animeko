/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DummyTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import me.him188.ani.app.videoplayer.media.MatroskaSubtitleFixture.block
import me.him188.ani.app.videoplayer.media.MatroskaSubtitleFixture.compression
import me.him188.ani.app.videoplayer.media.MatroskaSubtitleFixture.file
import me.him188.ani.app.videoplayer.media.MatroskaSubtitleFixture.track
import me.him188.ani.app.videoplayer.media.MatroskaSubtitleFixture.zlib
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class MatroskaSubtitleRegressionTest {
    @Test
    fun compressionIsIsolatedAcrossInterleavedTracksAndMultiByteTrackNumbers() {
        val fixture = file(
            listOf(
                track(2, compression()),
                track(130, compression(0)),
                track(4),
                track(5, compression(3, "Header: "), codec = "V_VP8", type = 1),
            ),
            listOf(1000, 5000).flatMap { time ->
                listOf(
                    block(2, time, zlib("简体 $time")),
                    block(4, time, "Plain $time".encodeToByteArray()),
                    block(130, time, zlib("繁體 $time")),
                    block(5, time, "stripped $time".encodeToByteArray()),
                )
            },
        )
        SrtExtraction().use { extraction ->
            extraction.read(fixture)
            extraction.assertCues(2, listOf("简体 1000", "简体 5000"))
            extraction.assertCues(130, listOf("繁體 1000", "繁體 5000"))
            extraction.assertCues(4, listOf("Plain 1000", "Plain 5000"))
            assertEquals(
                listOf("Header: stripped 1000", "Header: stripped 5000"),
                extraction.binarySamples.getValue(5).map { it.decodeToString() },
            )
        }
    }

    @Test
    fun backwardSeekRetainsCompressionAndReplaysCuesWithoutDuplicatePrefixes() {
        val fixture = file(
            listOf(track(130, compression())),
            listOf(block(130, 1000, zlib("第一句")), block(130, 5000, zlib("第二句"))),
        )
        SrtExtraction().use { extraction ->
            extraction.read(fixture)
            extraction.assertCues(130, listOf("第一句", "第二句"))
            repeat(2) {
                extraction.samples.clear()
                extraction.extractor.seek(fixture.clusterOffset.toLong(), 0)
                extraction.read(fixture, fixture.clusterOffset)
                extraction.assertCues(130, listOf("第一句", "第二句"))
            }
        }
    }

    @Test
    fun multilineUtf8SurvivesInflationAcrossBufferBoundaries() {
        val text = "简体与繁體字幕，标点！".repeat(300) + "\n第二行"
        SrtExtraction().use { extraction ->
            extraction.read(file(listOf(track(2, compression())), listOf(block(2, 1000, zlib(text)))))
            extraction.assertCues(2, listOf(text))
        }
    }

    @Test
    fun truncatedCompressedBlockFailsBeforeEmittingText() {
        assertMalformedPayload(zlib("完整字幕").dropLast(1).toByteArray())
    }

    @Test
    fun corruptCompressedBlockFailsBeforeEmittingText() {
        val payload = zlib("完整字幕")
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        assertMalformedPayload(payload)
    }

    @Test
    fun unsupportedCompressionAlgorithmIsRejected() {
        SrtExtraction().use { extraction ->
            val error = assertFailsWith<ParserException> {
                extraction.read(file(listOf(track(2, compression(1))), emptyList()))
            }
            assertTrue(error.message.orEmpty().startsWith("ContentCompAlgo 1 not supported"))
            assertTrue(extraction.samples.isEmpty())
        }
    }

    @Test
    fun explicitZlibOnVideoRetainsMedia3Rejection() {
        SrtExtraction().use { extraction ->
            val error = assertFailsWith<ParserException> {
                extraction.read(file(listOf(track(1, compression(0), codec = "V_VP8", type = 1)), emptyList()))
            }
            assertTrue(error.message.orEmpty().startsWith("ContentCompAlgo 0 not supported"))
            assertTrue(extraction.samples.isEmpty())
        }
    }

    @Test
    fun lacedCompressedTextIsRejectedBeforeEmittingCues() {
        SrtExtraction().use { extraction ->
            // Xiph lacing with two independently compressed samples.
            val payload = zlib("字幕")
            val laced = byteArrayOf(1, payload.size.toByte()) + payload + payload
            val error = assertFailsWith<ParserException> {
                extraction.read(file(listOf(track(130, compression())), listOf(block(130, 1000, laced, flags = 2))))
            }
            assertFalse(error.contentIsMalformed)
            assertTrue(error.message.orEmpty().startsWith("Laced compressed text subtitles"))
            assertTrue(extraction.samples.isEmpty())
        }
    }

    private fun assertMalformedPayload(payload: ByteArray) {
        SrtExtraction().use { extraction ->
            val error = assertFailsWith<ParserException> {
                extraction.read(file(listOf(track(2, compression())), listOf(block(2, 1000, payload))))
            }
            assertTrue(error.contentIsMalformed)
            assertTrue(extraction.samples.isEmpty(), "Corrupt data must never be emitted as subtitle text")
        }
    }

    private class SrtExtraction : AutoCloseable {
        private val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        val extractor = CompressedSubtitleMatroskaExtractor(AssSubtitleParserFactory(handler), handler)
        val samples = mutableMapOf<Int, MutableList<CuesWithTiming>>()
        val binarySamples = mutableMapOf<Int, MutableList<ByteArray>>()

        init {
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput {
                    return object : TrackOutput by DummyTrackOutput() {
                        private val data = ByteArrayOutputStream()

                        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
                            val bytes = ByteArray(length)
                            val count = input.read(bytes, 0, length)
                            if (count > 0) data.write(bytes, 0, count)
                            return count
                        }

                        override fun sampleData(buffer: ParsableByteArray, length: Int, sampleDataPart: Int) {
                            val bytes = ByteArray(length)
                            buffer.readBytes(bytes, 0, length)
                            data.write(bytes)
                        }

                        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
                            val bytes = data.toByteArray()
                            assertEquals(0, offset)
                            assertEquals(size, bytes.size)
                            if (type == C.TRACK_TYPE_TEXT) {
                                samples.getOrPut(id) { mutableListOf() } += CueDecoder().decode(timeUs, bytes, 0, size)
                            } else {
                                binarySamples.getOrPut(id) { mutableListOf() } += bytes
                            }
                            data.reset()
                        }
                    }
                }

                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            })
        }

        fun read(fixture: MatroskaSubtitleFixture.File, offset: Int = 0) {
            val data = fixture.bytes
            val stream = data.inputStream(offset, data.size - offset)
            val input = DefaultExtractorInput(
                { buffer, start, length -> stream.read(buffer, start, length) },
                offset.toLong(),
                data.size.toLong(),
            )
            val seek = PositionHolder()
            while (true) {
                when (val result = extractor.read(input, seek)) {
                    Extractor.RESULT_END_OF_INPUT -> return
                    else -> assertEquals(Extractor.RESULT_CONTINUE, result)
                }
            }
        }

        fun assertCues(track: Int, text: List<String>) {
            val cues = samples.getValue(track)
            assertEquals(text, cues.map { it.cues.single().text.toString() }, "Track $track")
            assertEquals(text.indices.map { 1_000_000L + it * 4_000_000L }, cues.map { it.startTimeUs })
            assertEquals(List(text.size) { 3_000_000L }, cues.map { it.durationUs })
        }

        override fun close() {
            extractor.release()
            handler.release()
        }
    }
}

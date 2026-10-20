/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
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
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(UnstableApi::class)
class CompressedSubtitleMatroskaExtractorTest {
    @Test
    fun compressedAndPlainTracksReachLibassWithCorrectTextAndTiming() = assertLibassTracks(ssa = false)

    @Test
    fun compressedAndPlainSsaTracksReachLibassWithCorrectTextAndTiming() = assertLibassTracks(ssa = true)

    private fun assertLibassTracks(ssa: Boolean) {
        val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        val extractor = CompressedSubtitleMatroskaExtractor(AssSubtitleParserFactory(handler), handler)
        val formats = mutableMapOf<Int, Format>()
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput by DummyTrackOutput() {
                override fun format(format: Format) { formats[id] = format }
            }
            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {}
        })
        try {
            extractFixture(extractor, "compressed-ass.mkv", ssa)
            for (number in 2..4) {
                val format = assertNotNull(formats[number])
                handler.onTracksChanged(
                    Tracks(listOf(Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)))),
                )
                val events = assertNotNull(handler.track?.getEvents())
                val expected = if (number == 4) {
                    listOf("Plain ASS subtitles, punctuation intact!", "Plain ASS subtitles, punctuation intact!")
                } else {
                    listOf("你好，世界！简体中文字幕", "繁體字幕：魔法騎士！")
                }
                assertEquals(expected, events.map { it.text }, "Track $number")
                assertEquals(listOf(1000L, 5000L), events.map { it.start })
                assertEquals(listOf(3000L, 3000L), events.map { it.duration })
            }
        } finally {
            extractor.release()
            handler.release()
        }
    }

    @Test
    fun compressedAndPlainSrtTracksProduceCorrectCuesAndTiming() {
        val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        val extractor = CompressedSubtitleMatroskaExtractor(AssSubtitleParserFactory(handler), handler)
        val samples = mutableMapOf<Int, MutableList<CuesWithTiming>>()
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                if (type != C.TRACK_TYPE_TEXT) return DummyTrackOutput()
                return object : TrackOutput by DummyTrackOutput() {
                    private val data = ByteArrayOutputStream()

                    override fun sampleData(buffer: ParsableByteArray, length: Int, sampleDataPart: Int) {
                        val bytes = ByteArray(length)
                        buffer.readBytes(bytes, 0, length)
                        data.write(bytes)
                    }

                    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
                        val bytes = data.toByteArray()
                        assertEquals(0, offset)
                        assertEquals(size, bytes.size)
                        samples.getOrPut(id) { mutableListOf() } += CueDecoder().decode(timeUs, bytes, 0, size)
                        data.reset()
                    }
                }
            }

            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {}
        })
        try {
            extractFixture(extractor, "compressed-srt.mkv")
            for (number in 2..4) {
                val cues = assertNotNull(samples[number])
                val expected = if (number == 4) {
                    listOf("Plain SRT subtitles, punctuation intact!", "Plain SRT subtitles, punctuation intact!")
                } else {
                    listOf("你好，世界！简体中文字幕", "繁體字幕：魔法騎士！")
                }
                assertEquals(expected, cues.map { it.cues.single().text.toString() }, "Track $number")
                assertEquals(listOf(1_000_000L, 5_000_000L), cues.map { it.startTimeUs })
                assertEquals(listOf(3_000_000L, 3_000_000L), cues.map { it.durationUs })
            }
        } finally {
            extractor.release()
            handler.release()
        }
    }

    private fun extractFixture(extractor: Extractor, asset: String, ssa: Boolean = false) {
        val data = InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { it.readBytes() }
        if (ssa) {
            val assCodec = "S_TEXT/ASS".encodeToByteArray()
            val ssaCodec = "S_TEXT/SSA".encodeToByteArray()
            var replaced = 0
            for (offset in 0..data.size - assCodec.size) {
                if (assCodec.indices.all { data[offset + it] == assCodec[it] }) {
                    ssaCodec.copyInto(data, offset)
                    replaced++
                }
            }
            assertEquals(3, replaced, "All subtitle track codec IDs must be SSA")
        }
        val stream = data.inputStream()
        val input = DefaultExtractorInput({ buffer, offset, length -> stream.read(buffer, offset, length) }, 0, data.size.toLong())
        val seek = PositionHolder()
        while (true) {
            val result = extractor.read(input, seek)
            if (result == Extractor.RESULT_END_OF_INPUT) break
            assertEquals(Extractor.RESULT_CONTINUE, result, "The fixture requires no seek during extraction")
        }
    }
}

/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(InternalMediampApi::class, InternalForInheritanceMediampApi::class)

package me.him188.ani.app.videoplayer.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.features.buildPlayerFeatures
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer

class ExternalSubtitleControllerTest {
    @Test
    fun `each subtitle remembers its delay and media changes clear calibration`() = runTest {
        val player = TestMediampPlayer(backgroundScope.coroutineContext)
        val controller = ExternalSubtitleController(player, backgroundScope)
        val media = UriMediaData("https://test/video.mp4")
        player.setMediaData(media)
        runCurrent()
        controller.select("first.srt", caption.encodeToByteArray(), media)
        runCurrent()
        controller.offsetMillis.value = 1500
        controller.select("second.srt", caption.encodeToByteArray(), media)
        runCurrent()
        assertEquals(0L, controller.offsetMillis.value)
        controller.offsetMillis.value = -500
        val tracks = controller.tracks.candidates.first()
        controller.tracks.select(tracks.first())
        runCurrent()
        assertEquals(1500L, controller.offsetMillis.value)
        controller.tracks.select(null)
        controller.tracks.select(tracks.last())
        runCurrent()
        assertEquals(-500L, controller.offsetMillis.value)
        player.injectPosition(500)
        runCurrent()
        assertEquals("Hello", controller.text.value)
        player.setMediaData(UriMediaData("https://test/next.mp4"))
        runCurrent()
        assertEquals(0L, controller.offsetMillis.value)
        player.close()
    }

    @Test
    fun `embedded and imported tracks share exclusive selection without discarding imported files`() = runTest {
        val native = SubtitleTrack("embedded", "embedded", "en", emptyList())
        val nativeGroup = object : TrackGroup<SubtitleTrack> {
            override val selected = MutableStateFlow<SubtitleTrack?>(native)
            override val candidates = MutableStateFlow(listOf(native))
            override fun select(track: SubtitleTrack?): Boolean {
                if (track != null && track != native) return false
                selected.value = track
                return true
            }
        }
        val delegate = TestMediampPlayer(backgroundScope.coroutineContext)
        val player = object : MediampPlayer by delegate {
            override val features = buildPlayerFeatures {
                add(MediaMetadata, object : MediaMetadata {
                    override val subtitleTracks = nativeGroup
                    override val audioTracks = null
                    override val chapters = null
                })
            }
        }
        val controller = ExternalSubtitleController(player, backgroundScope)
        val media = UriMediaData("https://test/video.mp4")
        player.setMediaData(media)
        runCurrent()
        controller.offsetMillis.value = 700
        controller.select("local.srt", caption.encodeToByteArray(), media)
        runCurrent()
        assertEquals(0L, controller.offsetMillis.value)
        controller.offsetMillis.value = -200
        assertNull(nativeGroup.selected.value)
        val external = controller.tracks.candidates.first().last()
        assertEquals(listOf(native, external), controller.tracks.candidates.first())
        assertTrue(controller.tracks.select(native))
        runCurrent()
        assertNull(controller.selected.value)
        assertEquals(700L, controller.offsetMillis.value)
        assertEquals(native, controller.tracks.selected.value)
        assertTrue(controller.tracks.select(external))
        runCurrent()
        assertNull(nativeGroup.selected.value)
        assertEquals(-200L, controller.offsetMillis.value)
        assertEquals(external, controller.tracks.selected.value)
        player.close()
    }

    @Test
    fun `external captions follow seeks and delay and are cleared on media changes`() = runTest {
        val player = TestMediampPlayer(backgroundScope.coroutineContext)
        val controller = ExternalSubtitleController(player, backgroundScope)
        val first = UriMediaData("https://test/video.mp4")
        player.setMediaData(first)
        runCurrent()
        assertTrue(controller.select("test.srt", caption.encodeToByteArray(), first))
        player.injectPosition(1500)
        runCurrent()
        assertEquals("Hello", controller.text.value)
        val importedTrack = controller.tracks.candidates.first().single()
        assertTrue(controller.tracks.select(null))
        runCurrent()
        assertNull(controller.selected.value)
        assertEquals(listOf(importedTrack), controller.tracks.candidates.first())
        assertTrue(controller.tracks.select(importedTrack))
        runCurrent()
        assertEquals("Hello", controller.text.value)
        controller.offsetMillis.value = 1000
        runCurrent()
        assertEquals("", controller.text.value)
        player.injectPosition(2500)
        runCurrent()
        assertEquals("Hello", controller.text.value)
        player.injectPosition(500)
        runCurrent()
        assertEquals("", controller.text.value)
        player.setMediaData(UriMediaData("https://test/next.mp4"))
        runCurrent()
        assertNull(controller.selected.value)
        assertEquals(0L, controller.offsetMillis.value)
        assertEquals(emptyList(), controller.tracks.candidates.first())
        assertFalse(controller.tracks.select(importedTrack))
        assertFalse(controller.select("stale.srt", caption.encodeToByteArray(), first))
        player.close()
    }

    @Test
    fun `ASS dialogue accepts custom field order commas and line breaks`() {
        val cues = parseExternalSubtitle(
            """[Script Info]
                |Title: Test
                |[Events]
                |Format: Layer, End, Start, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                |Dialogue: 0,0:00:03.00,0:00:01.25,Default,,0,0,0,,{\i1}Hello, world\NSecond line
                |Comment: 0,0:00:03.00,0:00:01.25,Default,,0,0,0,,Ignored
                |Dialogue: 0,0:00:03.00,0:00:01.25,Default,,0,0,0,,{\p1}m 0 0 l 20 20
            """.trimMargin().encodeToByteArray(),
        )
        assertEquals(SubtitleCue(1250, 3000, "Hello, world\nSecond line"), cues.single())
    }

    @Test
    fun `UTF16 BOM subtitles are decoded`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + caption.flatMap {
            listOf((it.code and 255).toByte(), (it.code shr 8).toByte())
        }.toByteArray()
        assertEquals("Hello", parseExternalSubtitle(bytes).single().text)
    }

    private val caption = "1\n00:00:01,000 --> 00:00:03,000\nHello"
}

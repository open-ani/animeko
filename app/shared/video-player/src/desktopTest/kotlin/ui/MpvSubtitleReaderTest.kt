/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.domain.player.SubtitleTextConverter
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.openani.mediamp.mpv.MPVHandle

/** 使用应用的 mpv runtime 解码字幕，路径由测试环境提供。 */
@EnabledIfEnvironmentVariable(named = "ANI_TEST_MPV_DIR", matches = ".+")
class MpvSubtitleReaderTest {
    @Test
    fun `selected native text remains decoded during conversion and visibility is restored`() = runTest {
        MPVHandle.setRuntimeLibraryDirectory(checkNotNull(System.getenv("ANI_TEST_MPV_DIR")), false)
        val directory = Files.createTempDirectory("ani-subtitle-native-")
        try {
            val media = checkNotNull(System.getenv("ANI_TEST_MPV_MEDIA"))
            val caption = directory.resolve("caption.srt")
            Files.writeString(caption, "1\n00:00:00,000 --> 00:00:04,000\n软件與網絡\nHello\n")
            MPVHandle(Any()).use { handle ->
                handle.option("vo", "null")
                handle.option("ao", "null")
                handle.option("pause", "yes")
                handle.option("keep-open", "yes")
                assertTrue(handle.initialize())
                assertTrue(handle.command("loadfile", media))
                waitUntil { handle.getPropertyString("duration")?.toDoubleOrNull()?.let { it > 0 } == true }
                assertTrue(handle.command("sub-add", caption.toString(), "select"))
                assertTrue(handle.command("seek", "1", "absolute+exact"))
                waitUntil { handle.getPropertyString("sub-text")?.contains("软件與網絡") == true }
                MpvSubtitleReader(handle).use { reader ->
                    val original = reader.read(true)
                    assertEquals("软件與網絡\nHello", original)
                    assertFalse(handle.getPropertyBoolean("sub-visibility"))
                    assertEquals("軟件與網絡\nHello", SubtitleTextConverter().convert(original, SubtitleTextConversion.TRADITIONAL))
                    assertEquals("", reader.read(false))
                    assertTrue(handle.getPropertyBoolean("sub-visibility"))
                    assertTrue(handle.setPropertyDouble("sub-delay", 2.0))
                    assertTrue(handle.command("seek", "1", "absolute+exact"))
                    waitUntil { reader.read(true).isEmpty() }
                    assertTrue(handle.command("seek", "3", "absolute+exact"))
                    waitUntil { reader.read(true).contains("软件與網絡") }
                    assertTrue(handle.setPropertyDouble("sub-delay", -2.0))
                    assertTrue(handle.command("seek", "3", "absolute+exact"))
                    waitUntil { reader.read(true).isEmpty() }
                    assertTrue(handle.command("seek", "1", "absolute+exact"))
                    waitUntil { reader.read(true).contains("软件與網絡") }
                    assertTrue(handle.setPropertyString("sid", "no"))
                    waitUntil { reader.read(true).isEmpty() }
                }
                assertTrue(handle.getPropertyBoolean("sub-visibility"))
            }
        } finally {
            Files.list(directory).use { files -> files.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(directory)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            assertTrue(System.nanoTime() < deadline, "mpv subtitle state did not settle")
            Thread.sleep(20)
        }
    }
}

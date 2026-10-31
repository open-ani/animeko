/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.videoplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import org.openani.mediamp.source.Subtitle
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.currentItem
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

class IosSubtitleTracksTest {
    @Test
    fun `selection accepts the native current item across Kotlin wrappers and rejects stale items`() = runTest {
        val item = AVPlayerItem(uRL = NSURL.fileURLWithPath("/missing-subtitle-test-video.mp4"))
        val player = AVPlayer(playerItem = item)
        val directory = SystemPaths.createTempDirectory("ios-subtitle-test-")
        try {
            val file = directory.resolve("test.srt")
            file.writeBytes("1\n00:00:00,000 --> 00:00:02,000\nHello 你好\n\n".encodeToByteArray())
            val subtitles = IosSubtitleTracks(backgroundScope, player) { 1000 }
            subtitles.attach(
                item,
                listOf(Subtitle(uri = NSURL.fileURLWithPath(file.absolutePath).absoluteString!!, mimeType = "application/x-subrip", language = "eng")),
            )
            assertEquals(item, player.currentItem)
            val candidate = subtitles.candidates.value.single()
            assertTrue(subtitles.select(candidate))
            assertEquals(candidate, subtitles.selected.value)
            withContext(Dispatchers.Default) {
                withTimeout(5000) { subtitles.text.first { it == "Hello 你好" } }
            }
            assertTrue(subtitles.select(null))
            assertNull(subtitles.selected.value)
            assertEquals("", subtitles.text.value)

            player.replaceCurrentItemWithPlayerItem(null)
            assertFalse(subtitles.select(candidate))
            assertNull(subtitles.selected.value)
        } finally {
            player.replaceCurrentItemWithPlayerItem(null)
            directory.deleteRecursively()
        }
    }
}

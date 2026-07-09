/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 真实播放冒烟测试 (本地文件). 需要:
 * - macOS + mediamp-mpv dev-native 构建 (或 -Dani.mpv.native.dir)
 * - 测试视频 -Dani.seekverify.video (默认 /tmp/seek-verify.mp4)
 *
 * 条件不满足时跳过.
 */
class MpvVideoAnalyzerSmokeTest {
    private fun testVideo(): File? =
        File(System.getProperty("ani.seekverify.video") ?: "/tmp/seek-verify.mp4")
            .takeIf { it.isFile }

    @Test
    fun `probe local file with window`() {
        if (!System.getProperty("os.name").contains("Mac")) return
        val video = testVideo() ?: return
        val framesDir = File(System.getProperty("java.io.tmpdir"), "mpv-probe-frames-window")

        val output = runBlocking {
            MpvVideoAnalyzer().analyze(
                ProbeVideoInput(
                    videoUrl = video.absolutePath,
                    playSeconds = 5,
                    showWindow = true,
                    captureFramesDir = framesDir.absolutePath,
                    captureAtSeconds = listOf(2),
                ),
            )
        }

        println("analysis = ${output.analysis}")
        println("frames = ${output.frames.map { it.path }}")

        assertTrue(output.analysis.available, "player must be available")
        val playback = assertNotNull(output.analysis.playback)
        assertTrue(playback.ok, "playback must reach target: $playback ${output.analysis.errors}")
        assertTrue(
            (playback.playedPositionMillis ?: 0) >= 5_000,
            "position must advance to >=5s, was ${playback.playedPositionMillis}",
        )
        val video0 = assertNotNull(output.analysis.video, "video stream info")
        assertEquals(1920, video0.width)
        assertTrue(output.frames.isNotEmpty(), "should capture frames")
    }

    @Test
    fun `probe local file headless - drain keeps playback advancing`() {
        if (!System.getProperty("os.name").contains("Mac")) return
        val video = testVideo() ?: return

        val output = runBlocking {
            MpvVideoAnalyzer().analyze(
                ProbeVideoInput(
                    videoUrl = video.absolutePath,
                    playSeconds = 5,
                    showWindow = false,
                ),
            )
        }

        println("analysis = ${output.analysis}")
        val playback = assertNotNull(output.analysis.playback)
        assertTrue(playback.ok, "headless playback must reach target: $playback ${output.analysis.errors}")
        assertTrue(
            (playback.playedPositionMillis ?: 0) >= 5_000,
            "position must advance to >=5s without any surface, was ${playback.playedPositionMillis}",
        )
    }
}

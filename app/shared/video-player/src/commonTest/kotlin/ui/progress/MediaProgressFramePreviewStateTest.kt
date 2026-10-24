/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaProgressFramePreviewStateTest {
    /** 帧按请求位置缓存, [gatedPosition] 处的取帧会挂在 [gate] 上, 用于模拟解码延迟. */
    private class Fixture {
        val frames = mutableMapOf<Long, ImageBitmap>()
        val gate = CompletableDeferred<Unit>()
        var gatedPosition: Long? = null
        val state = MediaProgressFramePreviewState(
            fetchFrame = { position ->
                if (position == gatedPosition) gate.await()
                frames.getOrPut(position) { ImageBitmap(1, 1) }
            },
            debounceMillis = 0,
        )
    }

    @Test
    fun `decoding shows the nearest cached frame instead of a far one`() = runTest {
        val f = Fixture()
        f.state.requestFrame(0)
        f.state.requestFrame(10_000)
        assertSame(f.frames.getValue(10_000), f.state.frame)

        // 目标 2s 未缓存: 解码期间应立即显示离它最近的缓存帧 (0s), 而不是继续显示 10s.
        f.gatedPosition = 2_000
        val request = launch { f.state.requestFrame(2_000) }
        runCurrent()
        assertSame(f.frames.getValue(0), f.state.frame)
        assertTrue(f.state.isLoading)

        f.gate.complete(Unit)
        request.join()
        assertSame(f.frames.getValue(2_000), f.state.frame)
    }

    @Test
    fun `keeps the displayed frame when it is already the nearest`() = runTest {
        val f = Fixture().apply { gatedPosition = 2_000 }
        f.state.requestFrame(0)

        // 当前帧 (0s) 与目标 (2s) 只差一格, 缓存里没有更近的帧, 保持不动避免闪动.
        val request = launch { f.state.requestFrame(2_000) }
        runCurrent()
        assertSame(f.frames.getValue(0), f.state.frame)
        assertTrue(f.state.isLoading)

        f.gate.complete(Unit)
        request.join()
        assertSame(f.frames.getValue(2_000), f.state.frame)
    }
}

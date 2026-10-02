/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.mutableStateOf
import me.him188.ani.app.domain.media.player.prefetch.MediaPrefetchRequest
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

@OptIn(InternalMediampApi::class)
class PlayerSkipOpEdStatePrefetchTest {
    private val op = Chapter("OP", durationMillis = 90_000, offsetMillis = 120_000)

    private fun createState(chapters: List<Chapter> = listOf(op)): PlayerSkipOpEdState {
        return PlayerSkipOpEdState(
            chapters = mutableStateOf(chapters),
            onSkip = {},
            videoLength = mutableStateOf(24.minutes),
        )
    }

    @Test
    fun `no prefetch far before the chapter`() {
        val state = createState()
        state.update(120_000 - PlayerSkipOpEdState.PREFETCH_LEAD_MILLIS - 1_000)
        assertNull(state.prefetchRequest)
    }

    @Test
    fun `pending chapter is available only until cancelled or skipped`() {
        val state = createState()
        state.update(100_000)
        assertNull(state.pendingChapter)
        state.update(116_000)
        assertEquals(op, state.pendingChapter)
        state.cancelSkipOpEd()
        assertNull(state.pendingChapter)
        state.update(120_000)
        assertNull(state.pendingChapter)

        val automatic = createState()
        automatic.update(116_000)
        assertEquals(op, automatic.pendingChapter)
        automatic.update(120_000)
        assertNull(automatic.pendingChapter)
    }

    @Test
    fun `prefetch covers 30s after chapter end from lead time until chapter end`() {
        val state = createState()
        // 缓存 OP 结束后的 30 秒, 前提是 OP 开头 (120s) 之前的内容已缓冲好
        val expected = MediaPrefetchRequest(MediaTimeRange(210_000, 240_000), requireBufferedUntilMillis = 120_000)
        state.update(120_000 - PlayerSkipOpEdState.PREFETCH_LEAD_MILLIS)
        assertEquals(expected, state.prefetchRequest)
        state.update(150_000) // 章节内
        assertEquals(expected, state.prefetchRequest)
        state.update(209_999)
        assertEquals(expected, state.prefetchRequest)
        state.update(210_000) // 章节结束
        assertNull(state.prefetchRequest)
    }

    @Test
    fun `no prefetch once the chapter has been skipped and the user seeks back`() {
        var skippedTo = -1L
        val state = PlayerSkipOpEdState(
            chapters = mutableStateOf(listOf(op)),
            onSkip = { skippedTo = it },
            videoLength = mutableStateOf(24.minutes),
        )
        state.update(119_500) // 进入提示窗口
        state.update(120_000) // 到达章节开头, 自动跳过
        assertEquals(210_000, skippedTo)
        state.update(210_000)
        assertNull(state.prefetchRequest)

        // 用户拖回 OP 之前: 同一章节不会再自动跳过, 也就不需要预缓存
        state.update(100_000)
        assertNull(state.prefetchRequest)
    }

    @Test
    fun `cancelling the skip also cancels prefetch`() {
        val state = createState()
        state.update(116_000) // 提示窗口内 (章节开头前 5 秒)
        assertEquals(MediaPrefetchRequest(MediaTimeRange(210_000, 240_000), 120_000), state.prefetchRequest)
        state.cancelSkipOpEd()
        state.update(117_000)
        assertNull(state.prefetchRequest)
    }

    @Test
    fun `non op ed chapters do not trigger prefetch`() {
        val state = createState(listOf(Chapter("Ch 1", durationMillis = 600_000, offsetMillis = 0)))
        state.update(10_000)
        assertNull(state.prefetchRequest)
    }
}

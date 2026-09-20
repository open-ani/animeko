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
        assertNull(state.prefetchRange)
    }

    @Test
    fun `prefetch covers 30s after chapter end from lead time until chapter end`() {
        val state = createState()
        val expected = MediaTimeRange(210_000, 240_000)
        state.update(120_000 - PlayerSkipOpEdState.PREFETCH_LEAD_MILLIS)
        assertEquals(expected, state.prefetchRange)
        state.update(150_000) // 章节内
        assertEquals(expected, state.prefetchRange)
        state.update(209_999)
        assertEquals(expected, state.prefetchRange)
        state.update(210_000) // 章节结束
        assertNull(state.prefetchRange)
    }

    @Test
    fun `non op ed chapters do not trigger prefetch`() {
        val state = createState(listOf(Chapter("Ch 1", durationMillis = 600_000, offsetMillis = 0)))
        state.update(10_000)
        assertNull(state.prefetchRange)
    }
}

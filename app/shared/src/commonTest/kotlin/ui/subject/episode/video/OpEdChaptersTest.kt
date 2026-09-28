/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalMediampApi::class, ExperimentalCoroutinesApi::class)
class OpEdChaptersTest {
    @Test
    fun `late AutoSkip response preserves cancellation and adds missing ending`() = runTest {
        val onlineTimes = MutableSharedFlow<List<Long>>()
        val flows = createOpEdChapterFlows(
            playerChapters = flowOf(emptyList()),
            videoLength = flowOf(24.minutes),
            autoSkipTimes = onlineTimes,
            opEdSkipDuration = flowOf(85.seconds),
            mediaChapters = flowOf(listOf(MediaChapter("Intro", 70_000L, 60_000L, MediaChapterKind.OPENING))),
        )
        val chapters = mutableStateOf(emptyList<Chapter>())
        var progressChapters = emptyList<Chapter>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flows.skipChapters.collect { chapters.value = it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flows.progressChapters.collect { progressChapters = it }
        }
        runCurrent()
        assertEquals(listOf(60_000L), chapters.value.map { it.offsetMillis })

        val targets = mutableListOf<Long>()
        val state = PlayerSkipOpEdState(chapters) { targets.add(it) }
        state.update(57_000L)
        assertTrue(state.showSkipTips)
        state.cancelSkipOpEd()
        assertFalse(state.showSkipTips)

        onlineTimes.emit(listOf(60_000L, 1_200_000L))
        runCurrent()
        assertEquals(listOf(60_000L, 1_200_000L), chapters.value.map { it.offsetMillis })
        assertEquals(listOf(60_000L, 1_200_000L), progressChapters.map { it.offsetMillis })
        state.update(58_000L)
        assertFalse(state.showSkipTips)
        state.update(60_000L)
        assertEquals(emptyList(), targets)

        state.update(1_200_000L)
        assertEquals(listOf(1_285_000L), targets)
    }
}

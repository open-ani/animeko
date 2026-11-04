/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PlaybackAutoSelectionStateTest {
    @Test
    fun `exhaustion remains visible until another attempt starts`() {
        val selection = PlaybackAutoSelectionState()
        selection.finish(selection.begin(), MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES))
        assertEquals(
            PlaybackAutoSelectionState.State.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES),
            selection.state.value,
        )
        assertSame(selection.begin(), selection.state.value)
    }

    @Test
    fun `preempted and cancelled attempts do not report exhausted candidates`() {
        val selection = PlaybackAutoSelectionState()
        listOf(
            MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.PRECONDITION_SELECTED),
            MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.PREEMPTED),
            MediaAutoSelectOutcome.Cancelled,
        ).forEach { outcome ->
            selection.finish(selection.begin(), outcome)
            assertSame(PlaybackAutoSelectionState.State.Idle, selection.state.value)
        }
    }

    @Test
    fun `late outcomes and cancellation cannot replace a newer selection attempt`() {
        val selection = PlaybackAutoSelectionState()
        val old = selection.begin()
        val current = selection.begin()
        selection.finish(old, MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.FUZZY_DEADLINE))
        selection.cancel(old)
        assertSame(current, selection.state.value)
        selection.cancel(current)
        assertSame(PlaybackAutoSelectionState.State.Idle, selection.state.value)
    }

    @Test
    fun `loading a manually chosen resource invalidates pending exhaustion results`() {
        val selection = PlaybackAutoSelectionState()
        val pending = selection.begin()
        assertIs<PlaybackAutoSelectionState.State.Selecting>(selection.state.value)
        selection.reset()
        selection.finish(pending, MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES))
        assertSame(PlaybackAutoSelectionState.State.Idle, selection.state.value)
    }
}

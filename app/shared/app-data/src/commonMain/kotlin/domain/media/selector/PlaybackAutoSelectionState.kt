/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前集、当前选择器的自动选源状态。每次尝试持有独立令牌，旧结果不能覆盖后续选择。 */
class PlaybackAutoSelectionState {
    sealed interface State {
        data object Idle : State
        class Selecting internal constructor() : State
        data class Exhausted(val cause: MediaAutoSelectExhaustCause) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state = mutableState.asStateFlow()

    fun begin(): State.Selecting = State.Selecting().also { mutableState.value = it }

    fun finish(attempt: State.Selecting, outcome: MediaAutoSelectOutcome) {
        val result = if (outcome is MediaAutoSelectOutcome.Exhausted &&
            outcome.cause != MediaAutoSelectExhaustCause.PRECONDITION_SELECTED &&
            outcome.cause != MediaAutoSelectExhaustCause.PREEMPTED
        ) State.Exhausted(outcome.cause) else State.Idle
        mutableState.compareAndSet(attempt, result)
    }

    fun cancel(attempt: State.Selecting) {
        mutableState.compareAndSet(attempt, State.Idle)
    }

    /** 新资源开始加载时，当前选择取代此前的耗尽或等待状态。 */
    fun reset() {
        mutableState.value = State.Idle
    }
}

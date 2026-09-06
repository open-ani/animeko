/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import me.him188.ani.app.navigation.SubjectDetailPlaceholder

sealed interface TvNavigationEvent {
    data class Subject(val subjectId: Int, val placeholder: SubjectDetailPlaceholder? = null) : TvNavigationEvent
    data class Episode(val subjectId: Int, val episodeId: Int) : TvNavigationEvent
    data object LoggedIn : TvNavigationEvent
}

/** A ViewModel-owned queue; navigation is delivered once to the active route. */
class TvNavigationEvents {
    private val channel = Channel<TvNavigationEvent>(Channel.BUFFERED)
    val events: Flow<TvNavigationEvent> = channel.receiveAsFlow()

    fun emit(event: TvNavigationEvent) {
        channel.trySend(event)
    }
}

@Composable
fun TvNavigationEffect(events: Flow<TvNavigationEvent>, onNavigate: (TvNavigationEvent) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(events, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { currentOnNavigate(it) }
        }
    }
}

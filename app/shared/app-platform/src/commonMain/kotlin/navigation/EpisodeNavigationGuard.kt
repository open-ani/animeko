/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

fun interface EpisodeNavigationGuard {
    fun checkNavigateEpisode(subjectId: Int, episodeId: Int): String?
}

fun interface EpisodeNavigationGuardHandle {
    fun dispose()
}

object EpisodeNavigationGuardRegistry {
    private val guards = MutableStateFlow<List<EpisodeNavigationGuard>>(emptyList())
    private val _denialEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val denialEvents: SharedFlow<String> = _denialEvents.asSharedFlow()

    fun register(guard: EpisodeNavigationGuard): EpisodeNavigationGuardHandle {
        guards.update { it + guard }
        return EpisodeNavigationGuardHandle { guards.update { current -> current - guard } }
    }

    internal fun check(subjectId: Int, episodeId: Int): String? =
        guards.value.firstNotNullOfOrNull { it.checkNavigateEpisode(subjectId, episodeId) }

    internal fun emitDenial(reason: String) {
        _denialEvents.tryEmit(reason)
    }

    /**
     * Returns `true` when entering the episode is allowed; otherwise emits the denial reason to
     * [denialEvents] and returns `false`. For episode-switching action points that do not go
     * through [AniNavigator.navigateEpisodeDetails], e.g. in-player episode selectors.
     */
    fun checkOrNotifyDenied(subjectId: Int, episodeId: Int): Boolean {
        val reason = check(subjectId, episodeId) ?: return true
        emitDenial(reason)
        return false
    }
}

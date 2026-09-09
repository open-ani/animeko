/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey

internal data class TvDetailsKey(val value: String) : TvFocusKey

internal enum class TvDetailsPanelKind {
    Summary, Image, Collection, RemoveCollection, MarkAllWatched, Rating, Episodes,
    Characters, Staff, Person, Tags, TagResults, Comments, Comment, Report,
}

internal data class TvDetailsPanel(
    val kind: TvDetailsPanelKind,
    val argument: String = "",
    val origin: String,
    val focusedItem: String? = null,
) {
    val key: String get() = "${kind.name}:$argument"
}

@Stable
internal class TvSubjectPresentationState {
    var panels by mutableStateOf<List<TvDetailsPanel>>(emptyList())
        private set
    var generation by mutableIntStateOf(0)
        private set
    var lastFocused by mutableStateOf("play")
    var lastEpisode by mutableStateOf<String?>(null)
    var backLevel by mutableIntStateOf(0)
    var restoreTarget by mutableStateOf<String?>(null)
    val panel get() = panels.lastOrNull()

    fun open(kind: TvDetailsPanelKind, argument: String = "", origin: String = lastFocused) {
        generation++
        panels = panels + TvDetailsPanel(kind, argument, origin)
    }

    fun close() {
        val previous = panel ?: return
        generation++
        panels = panels.dropLast(1)
        restoreTarget = previous.origin
    }

    /** Android may deliver both the system Back callback and key-up for the same press. */
    fun closeIfCurrent(expectedGeneration: Int) {
        if (generation == expectedGeneration) close()
    }

    fun rememberPanelFocus(key: String) {
        val current = panel ?: return
        if (current.focusedItem != key) panels = panels.dropLast(1) + current.copy(focusedItem = key)
    }

    /** An old completion cannot dismiss or advance a newer panel. */
    fun complete(requestId: Int, offerMarkAll: Boolean): Boolean {
        if (requestId != generation || panel == null) return false
        when (panel?.kind) {
            TvDetailsPanelKind.Collection, TvDetailsPanelKind.RemoveCollection -> {
                if (panel?.kind == TvDetailsPanelKind.RemoveCollection) close()
                if (offerMarkAll) open(TvDetailsPanelKind.MarkAllWatched, origin = "collection") else close()
            }
            TvDetailsPanelKind.Rating, TvDetailsPanelKind.MarkAllWatched, TvDetailsPanelKind.Report -> close()
            else -> return false
        }
        return true
    }

    companion object {
        val Saver = listSaver<TvSubjectPresentationState, Any>(
            save = { state ->
                listOf(state.lastFocused, state.lastEpisode.orEmpty(), state.backLevel, state.generation) +
                    state.panels.flatMap { listOf(it.kind.name, it.argument, it.origin, it.focusedItem.orEmpty()) }
            },
            restore = { saved ->
                TvSubjectPresentationState().apply {
                    lastFocused = saved[0] as String
                    lastEpisode = (saved[1] as String).ifEmpty { null }
                    backLevel = saved[2] as Int
                    generation = saved[3] as Int
                    panels = saved.drop(4).chunked(4).map {
                        TvDetailsPanel(TvDetailsPanelKind.valueOf(it[0] as String), it[1] as String, it[2] as String,
                            (it[3] as String).ifEmpty { null })
                    }
                }
            },
        )
    }
}

internal fun detailsFocusFallback(target: String?, previous: List<String>, available: List<String>, fallback: String): String {
    if (target in available) return checkNotNull(target)
    if (available.isEmpty()) return fallback
    return available[previous.indexOf(target).coerceAtLeast(0).coerceAtMost(available.lastIndex)]
}

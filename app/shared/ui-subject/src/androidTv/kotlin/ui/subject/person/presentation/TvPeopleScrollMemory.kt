/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** Snapshot before navigation: a returning Paging list may first lay out only its loading item. */
@Stable
internal class TvPeopleScrollMemory {
    var page by mutableStateOf<Int?>(null)
        private set
    var rows by mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap())
        private set

    fun capture(scroll: ScrollState, lists: Map<String, LazyListState>) {
        page = scroll.value
        rows = lists.mapValues { (_, list) -> list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
    }

    companion object {
        val Saver = listSaver<TvPeopleScrollMemory, Any>(
            save = { memory -> listOf(memory.page ?: -1) + memory.rows.flatMap { (id, position) ->
                listOf(id, position.first, position.second)
            } },
            restore = { saved -> TvPeopleScrollMemory().apply {
                page = (saved.first() as Int).takeIf { it >= 0 }
                rows = saved.drop(1).chunked(3).associate { (it[0] as String) to ((it[1] as Int) to (it[2] as Int)) }
            } },
        )
    }
}

/** Restoring a visible item must not reapply TV's pivot or the horizontal leading-edge policy. */
@OptIn(ExperimentalFoundationApi::class)
internal class TvPeopleFocusScrollSpec(
    private val delegate: BringIntoViewSpec,
    private val enabled: () -> Boolean,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (enabled()) delegate.calculateScrollDistance(offset, size, containerSize) else 0f
}

/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.subject.details

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.subject.presentation.TvDetailsKey
import me.him188.ani.tv.ui.subject.presentation.TvSubjectPresentationState
import me.him188.ani.tv.ui.subject.presentation.detailsFocusFallback

internal data class TvDetailsFocusRow(
    val state: LazyListState,
    val keys: List<String>,
    val loading: Boolean,
    val entry: String,
    /** 行中有条目时仍保留的入口，例如“查看全部剧集”。 */
    val persistentEntry: Boolean = false,
)

/** 准备目标行的数据和滚动位置；窗口与节点就绪由 [TvFocusScope] 负责。 */
internal class TvDetailsFocusState(
    private val scope: TvFocusScope,
    private val presentation: TvSubjectPresentationState,
    private val rows: State<Map<String, TvDetailsFocusRow>>,
) {
    suspend fun restore(
        target: String,
        previous: List<String> = emptyList(),
    ) {
        scope.requestPrepared(isRelevant = { presentation.panel == null }) { prepare(target, previous) }
    }

    suspend fun prepare(target: String, previous: List<String> = emptyList()): TvFocusKey {
        fun targetRow(): TvDetailsFocusRow? = rows.value.let { current ->
            current[target.substringBefore(':')] ?: current.values.firstOrNull { it.entry == target }
        }
        snapshotFlow {
            val row = targetRow()
            row == null || target == row.entry || target in row.keys || !row.loading
        }.first { it }

        val row = targetRow()
        return if (row == null || target == row.entry && row.persistentEntry) {
            TvDetailsKey(target)
        } else if (row.keys.isEmpty()) {
            TvDetailsKey(row.entry)
        } else {
            val selected = detailsFocusFallback(target, previous, row.keys, row.keys.first())
            row.state.scrollToItem(row.keys.indexOf(selected))
            TvDetailsKey(selected)
        }
    }
}

@Composable
internal fun rememberTvDetailsFocusState(
    focus: TvFocusScope,
    presentation: TvSubjectPresentationState,
    rows: Map<String, TvDetailsFocusRow>,
): TvDetailsFocusState {
    val latestRows = rememberUpdatedState(rows)
    val state = remember(focus, presentation) {
        TvDetailsFocusState(focus, presentation, latestRows)
    }
    val scope = rememberCoroutineScope()

    focus.InitialFocus(state, isRelevant = { presentation.panel == null && presentation.restoreTarget == null }) {
        state.prepare(presentation.lastFocused)
    }
    LaunchedEffect(state, presentation.restoreTarget, presentation.panel) {
        val target = presentation.restoreTarget ?: return@LaunchedEffect
        if (presentation.panel == null) {
            state.restore(target)
            if (presentation.restoreTarget == target) presentation.restoreTarget = null
        }
    }

    val rowKeys = rows.mapValues { it.value.keys }
    var previousKeys by remember(state) { mutableStateOf(rowKeys) }
    // Capture the identity before the lazy layout removes its focused node.
    val focusedBeforeUpdate = presentation.lastFocused
    LaunchedEffect(state, rowKeys) {
        val current = focusedBeforeUpdate
        val section = current.substringBefore(':')
        val before = previousKeys[section].orEmpty()
        val after = rowKeys[section].orEmpty()
        if (presentation.panel == null && current in before && current !in after) {
            // A replacement Paging flow can emit an empty loading page before its data.
            // Keep this preparation alive across those emissions to choose a neighbour.
            scope.launch { state.restore(current, before) }
        }
        val loadingSection = rows.entries.firstOrNull { it.value.entry == current }?.key
        if (presentation.panel == null && loadingSection != null &&
            previousKeys[loadingSection].isNullOrEmpty()
        ) {
            rowKeys[loadingSection]?.firstOrNull()?.let { key ->
                scope.launch { state.restore(key) }
            }
        }
        previousKeys = rowKeys
    }
    return state
}

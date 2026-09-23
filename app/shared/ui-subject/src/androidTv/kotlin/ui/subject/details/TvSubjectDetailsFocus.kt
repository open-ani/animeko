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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.requestPrepared
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

/** 焦点恢复只等待窗口、布局和目标数据就绪，不控制页面内容的挂载。 */
internal class TvDetailsFocusState(
    val scope: TvFocusScope,
    private val presentation: TvSubjectPresentationState,
    private val lifecycle: Lifecycle,
    private val window: WindowInfo,
    private val rows: State<Map<String, TvDetailsFocusRow>>,
) {
    var laidOut by mutableStateOf(false)

    suspend fun restore(
        target: String,
        previous: List<String> = emptyList(),
    ) {
        scope.requestPrepared(isRelevant = { presentation.panel == null }) {
            fun targetRow(): TvDetailsFocusRow? = rows.value.let { current ->
                current[target.substringBefore(':')] ?: current.values.firstOrNull { it.entry == target }
            }
            lifecycle.currentStateFlow.combine(snapshotFlow {
                val row = targetRow()
                laidOut && window.isWindowFocused &&
                    (row == null || target == row.entry || target in row.keys || !row.loading)
            }) { state, ready -> state.isAtLeast(Lifecycle.State.RESUMED) && ready }.first { it }

            val row = targetRow()
            if (row == null || target == row.entry && row.persistentEntry) {
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
}

@Composable
internal fun rememberTvDetailsFocusState(
    presentation: TvSubjectPresentationState,
    rows: Map<String, TvDetailsFocusRow>,
): TvDetailsFocusState {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val latestRows = rememberUpdatedState(rows)
    val state = remember(focus, presentation, lifecycle, window) {
        TvDetailsFocusState(focus, presentation, lifecycle, window, latestRows)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        if (presentation.panel == null && presentation.restoreTarget == null) {
            state.restore(presentation.lastFocused)
        }
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

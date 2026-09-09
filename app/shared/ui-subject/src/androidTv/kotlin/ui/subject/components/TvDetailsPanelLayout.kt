/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.TvAnchoredOptionLayout
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionPanel
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import me.him188.ani.leanback.ui.subject.presentation.detailsFocusFallback

internal class TvDetailsPanelEntry(
    val key: String,
    val focusable: Boolean = true,
    val content: @Composable (Modifier) -> Unit
)

/** The caller supplies content and actions; this container owns only overlay layout and focus. */
@Composable
internal fun TvDetailsPanelLayout(
    title: String,
    entries: List<TvDetailsPanelEntry>,
    backdrop: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialKey: String? = null,
    focusedKey: String? = null,
    onFocused: (String) -> Unit = {},
    compact: Boolean = false,
    showHeader: Boolean = !compact,
    modal: Boolean = false,
    actions: List<TvDetailsPanelEntry> = emptyList(),
    maxWidth: Dp = TvSubjectDetailsDefaults.PanelMaxWidth,
    anchorBounds: Rect? = null,
    anchorButton: @Composable (Modifier) -> Unit = {},
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val listState = rememberLazyListState()
    val actionsState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var laidOut by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val entryKey = remember { focusedKey ?: initialKey }
    var previousKeys by remember { mutableStateOf(emptyList<String>()) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    val readingKeys = entries.filter { it.focusable }.map { it.key }
    val actionKeys = actions.map { it.key }
    val keys = readingKeys + actionKeys
    LaunchedEffect(keys) {
        if (keys.isEmpty()) return@LaunchedEffect
        val rememberedKey = if (entered) focusedKey ?: initialKey else entryKey
        val shouldRestore = !entered || rememberedKey !in keys
        val target = detailsFocusFallback(rememberedKey, previousKeys, keys, keys.first())
        previousKeys = keys
        if (!shouldRestore) return@LaunchedEffect
        focus.requestPrepared {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            val actionIndex = actionKeys.indexOf(target)
            if (actionIndex >= 0) actionsState.scrollToItem(actionIndex)
            else listState.scrollToItem(entries.indexOfFirst { it.key == target }.coerceAtLeast(0))
            TvDetailsKey(target)
        }
        entered = true
    }
    fun anchor(key: String) = Modifier.tvFocusAnchor(focus, TvDetailsKey(key))
        .onFocusChanged {
            if (it.hasFocus && (entered || focus.userNavGeneration > 0)) {
                if (key in actionKeys) lastAction = key
                onFocused(key)
            }
        }.testTag("tv-details-panel-$key")
    val entriesContent: @Composable () -> Unit = {
        BoxWithConstraints {
            val readingHeight = (maxHeight - 8.dp).coerceAtLeast(40.dp)
            val downTarget = lastAction?.takeIf { it in actionKeys } ?: actionKeys.firstOrNull()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(4.dp),
                modifier = if (anchorBounds != null) Modifier.tvFocusExit(
                    focus, FocusDirection.Down to TvDetailsKey("overlay-trigger"),
                ) else if (downTarget != null) Modifier.tvFocusExit(focus, FocusDirection.Down to TvDetailsKey(downTarget))
                else Modifier,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp),
            ) {
                items(entries, key = { it.key }) { item ->
                    val itemModifier = anchor(item.key)
                        .then(if (modal) Modifier.heightIn(max = readingHeight) else Modifier)
                        .then(if (item.key == readingKeys.lastOrNull() && downTarget != null) {
                            Modifier.tvFocusLink(focus, down = TvDetailsKey(downTarget))
                        } else Modifier)
                    item.content(itemModifier)
                }
            }
        }
    }
    if (modal) {
        Box(Modifier.fillMaxSize().tvFocusNavSignal(focus).testTag("tv-details-panel")) {
            TvOptionModal(title,
                modifier = modifier.onGloballyPositioned { laidOut = true }.testTag("tv-review-reader-popup"),
                footer = if (actions.isEmpty()) null else ({
                    LazyRow(
                        state = actionsState,
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("tv-review-actions")
                            .tvFocusHotkey(focus, Key.DirectionUp to TvDetailsKey(readingKeys.first()))
                            .tvFocusHotkey(focus, Key.DirectionDown) {},
                    ) {
                        items(actions, key = { it.key }) { item -> item.content(anchor(item.key)) }
                    }
                }),
            ) { entriesContent() }
        }
        return
    }
    val panelContent: @Composable () -> Unit = {
        val panelModifier = modifier.testTag("tv-details-operation-panel").onGloballyPositioned { laidOut = true }
        TvOptionPanel(panelModifier, title = title.takeIf { showHeader }) { entriesContent() }
    }
    TvModalOverlay(
        onClose,
        modifier = Modifier.tvFocusNavSignal(focus).testTag("tv-details-panel"),
        background = { TvDetailsBackdrop(backdrop, { 1f }, crossfade = false) },
    ) {
        if (anchorBounds != null) {
            TvAnchoredOptionLayout(
                anchorBounds,
                anchor = {
                    anchorButton(
                        Modifier.tvFocusAnchor(focus, TvDetailsKey("overlay-trigger"))
                            .tvFocusLink(
                                focus,
                                up = (focusedKey?.takeIf { it in keys } ?: initialKey ?: keys.firstOrNull())
                                    ?.let(::TvDetailsKey),
                            )
                            .testTag("tv-details-overlay-trigger"),
                    )
                },
                panel = panelContent,
            )
        } else {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = maxWidth).fillMaxWidth(.9f).fillMaxHeight(.9f)) { panelContent() }
            }
        }
    }
}

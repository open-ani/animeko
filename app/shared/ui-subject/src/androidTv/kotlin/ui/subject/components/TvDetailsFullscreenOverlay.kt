/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey

/** Full-screen in-window overlay, with entry focus prepared after layout and lifecycle readiness. */
@Composable
internal fun TvDetailsFullscreenOverlay(
    backdrop: String,
    onClose: () -> Unit,
    initialKey: String,
    content: @Composable BoxScope.(TvFocusScope) -> Unit,
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var laidOut by remember { mutableStateOf(false) }
    LaunchedEffect(focus) {
        focus.requestPrepared {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            TvDetailsKey(initialKey)
        }
    }
    TvModalOverlay(
        onClose = onClose,
        modifier = Modifier.tvFocusNavSignal(focus).onGloballyPositioned { laidOut = true }
            .testTag("tv-details-panel"),
        background = { TvDetailsBackdrop(backdrop, { 1f }, crossfade = false) },
    ) {
        content(focus)
    }
}

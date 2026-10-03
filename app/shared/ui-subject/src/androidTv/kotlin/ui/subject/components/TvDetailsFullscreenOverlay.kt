/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.subject.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.subject.presentation.TvDetailsKey

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
    focus.InitialFocus(TvDetailsKey(initialKey))
    TvModalOverlay(
        onClose = onClose,
        modifier = Modifier.tvFocusNavSignal(focus)
            .testTag("tv-details-panel"),
        background = { TvDetailsBackdrop(backdrop, { 1f }, crossfade = false) },
    ) {
        content(focus)
    }
}

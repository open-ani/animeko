/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import me.him188.ani.leanback.ui.foundation.focus.consumeHeldConfirmKey
import me.him188.ani.leanback.ui.foundation.focus.tvBackKey

/** A modal layer in the current Compose window. Focus and pointer input stay above the underlay. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun TvModalOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val enterAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enterAlpha.animateTo(1f, tween(180)) }
    BackHandler(enabled = active, onBack = onClose)
    Box(
        modifier.fillMaxSize().graphicsLayer { alpha = enterAlpha.value }
            .consumeHeldConfirmKey().tvBackKey(enabled = active, onBack = onClose)
            .focusProperties { onExit = { if (active) cancelFocus() } }.focusGroup(),
    ) {
        background()
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        content()
    }
}

/** Keep the page composed for state/geometry, but prevent it accepting input behind a modal layer. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvModalUnderlay(blocked: Boolean): Modifier = this
    .focusProperties { onEnter = { if (blocked) cancelFocus() } }
    .focusGroup()
    .onPreviewKeyEvent { blocked }
    .then(if (blocked) Modifier.clearAndSetSemantics { } else Modifier)

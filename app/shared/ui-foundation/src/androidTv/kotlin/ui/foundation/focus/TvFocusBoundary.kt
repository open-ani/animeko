/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics

internal class TvFocusBoundaryState(
    private val active: State<Boolean>,
    private val parent: TvFocusBoundaryState?,
) {
    val isActive: Boolean get() = active.value && parent?.isActive != false
}

internal val LocalTvFocusBoundary = staticCompositionLocalOf<TvFocusBoundaryState?> { null }

/** 活动目标接收焦点和按键；退场内容保留布局，子边界继承父边界的有效性。 */
@Composable
fun TvFocusBoundary(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val parent = LocalTvFocusBoundary.current
    val latestActive = rememberUpdatedState(active)
    val boundary = remember(parent) { TvFocusBoundaryState(latestActive, parent) }
    CompositionLocalProvider(LocalTvFocusBoundary provides boundary) {
        Box(
            modifier
                .focusProperties { onEnter = { if (!boundary.isActive) cancelFocus() } }
                .focusGroup()
                .onPreviewKeyEvent { !boundary.isActive }
                .then(if (boundary.isActive) Modifier else Modifier.clearAndSetSemantics { }),
            content = content,
        )
    }
}

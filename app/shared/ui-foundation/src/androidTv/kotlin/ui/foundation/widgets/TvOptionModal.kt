/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Player-style modal in the existing window. The caller owns Back and restoring the entry focus. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun TvOptionModal(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 480.dp,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TvOptionContent {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f))
                .pointerInput(Unit) { detectTapGestures { } }.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier.width(width).heightIn(max = 460.dp).tvOptionPanelSurface().padding(24.dp)
                    .focusProperties { onExit = { cancelFocus() } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall,
                        color = TvOptionDefaults.Content)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TvOptionDefaults.Muted)
                    }
                }
                TvOptionDivider()
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                if (footer != null) {
                    TvOptionDivider()
                    footer()
                }
            }
        }
    }
}

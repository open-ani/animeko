/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact

@Stable
object EditMediaSourceTestDataCardDefaults {
    val cardShape
        @Composable
        get() = MaterialTheme.shapes.large

    val cardColors
        @Composable
        get() = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerLow),
        )

    @Composable
    fun FlowRow(
        modifier: Modifier = Modifier,
        content: @Composable FlowRowScope.() -> Unit
    ) {
        BoxWithConstraints {
            val isCompact = currentWindowAdaptiveInfo1().windowSizeClass.isWidthCompact
            androidx.compose.foundation.layout.FlowRow(
                modifier.padding(all = 16.dp).padding(bottom = 4.dp).focusGroup(),
                verticalArrangement = if (isCompact) {
                    Arrangement.spacedBy(16.dp)
                } else {
                    Arrangement.spacedBy(20.dp)
                },
                horizontalArrangement = if (isCompact) {
                    Arrangement.spacedBy(16.dp)
                } else {
                    Arrangement.spacedBy(24.dp)
                },
                maxItemsInEachRow = (constraints.maxWidth / 300f).toInt().coerceAtLeast(1),
                content = content,
            )
        }
    }

    @Composable
    fun KeywordTextField(
        state: AbstractMediaSourceTestState,
        modifier: Modifier = Modifier,
    ) {
        TextField(
            value = state.searchKeyword,
            onValueChange = { state.searchKeyword = it.trim() },
            modifier.moveFocusOnEnter(),
            label = { Text("关键词") },
            placeholder = {
                Text(
                    state.searchKeywordPlaceholder,
                    color = MaterialTheme.colorScheme.outline,
                )
            },
            trailingIcon = {
                IconButton(onClick = { state.randomKeyword() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "随机")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
        )
    }

    @Composable
    fun EpisodeSortTextField(
        state: AbstractMediaSourceTestState,
        modifier: Modifier = Modifier
    ) {
        TextField(
            value = state.sort,
            onValueChange = { state.sort = it.trim() },
            modifier.moveFocusOnEnter(),
            label = { Text("剧集序号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Next,
            ),
        )
    }
}
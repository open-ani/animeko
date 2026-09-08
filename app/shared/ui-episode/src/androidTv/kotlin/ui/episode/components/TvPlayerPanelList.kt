/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_empty_content
import me.him188.ani.leanback.ui.foundation.layout.tvPanelScrollEdges
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvPlayerPanelList(
    listModifier: Modifier,
    hostModifier: Modifier,
    empty: Boolean,
    emptyText: String = stringResource(Lang.foundation_empty_content),
    reverseLayout: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalTvOptionColors.current
    if (empty) {
        Text(
            emptyText,
            hostModifier
                .padding(horizontal = 16.dp, vertical = 20.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.muted,
        )
    } else {
        LazyColumn(
            modifier = hostModifier.then(listModifier).tvPanelScrollEdges(state, colors.container),
            state = state,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
            reverseLayout = reverseLayout,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

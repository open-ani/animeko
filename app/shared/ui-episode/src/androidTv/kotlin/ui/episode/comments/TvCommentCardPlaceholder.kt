/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults

/** Decorative skeletons; the host owns loading semantics and any temporary focus anchor. */
@Composable
internal fun TvCommentCardPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(LocalTvOptionColors.current.raised, TvOptionDefaults.ItemShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvPlaceholderBlock(Modifier.size(36.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TvPlaceholderBlock(Modifier.fillMaxWidth(.55f).height(16.dp))
                TvPlaceholderBlock(Modifier.fillMaxWidth(.75f).height(12.dp))
            }
        }
        Column(Modifier.height(78.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvPlaceholderBlock(Modifier.fillMaxWidth().height(18.dp))
            TvPlaceholderBlock(Modifier.fillMaxWidth().height(18.dp))
            TvPlaceholderBlock(Modifier.fillMaxWidth(.7f).height(18.dp))
        }
        TvPlaceholderBlock(Modifier.width(88.dp).height(20.dp))
    }
}

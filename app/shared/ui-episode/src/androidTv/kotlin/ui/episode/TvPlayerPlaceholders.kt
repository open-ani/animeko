/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.external.placeholder.PlaceholderHighlight
import me.him188.ani.app.ui.external.placeholder.fade
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
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
            PlaceholderBlock(Modifier.size(36.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaceholderBlock(Modifier.fillMaxWidth(.55f).height(16.dp))
                PlaceholderBlock(Modifier.fillMaxWidth(.75f).height(12.dp))
            }
        }
        Column(Modifier.height(78.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceholderBlock(Modifier.fillMaxWidth().height(18.dp))
            PlaceholderBlock(Modifier.fillMaxWidth().height(18.dp))
            PlaceholderBlock(Modifier.fillMaxWidth(.7f).height(18.dp))
        }
        PlaceholderBlock(Modifier.width(88.dp).height(20.dp))
    }
}

@Composable
internal fun TvRecommendationCardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.width(TvPlayerEpisodeStripDefaults.CardWidth).aspectRatio(16f / 9f)
            .padding(TvFocusDefaults.RingInset)
            .clip(TvPlayerEpisodeStripDefaults.CardShape)
            .background(LocalTvOptionColors.current.raised),
    ) {
        PlaceholderBlock(Modifier.fillMaxSize(), TvPlayerEpisodeStripDefaults.CardShape)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaceholderBlock(Modifier.fillMaxWidth(.8f).height(16.dp))
            PlaceholderBlock(Modifier.fillMaxWidth(.55f).height(12.dp))
        }
    }
}

@Composable
internal fun TvDanmakuItemPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(LocalTvOptionColors.current.raised, TvOptionDefaults.ItemShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaceholderBlock(Modifier.width(40.dp).height(12.dp))
        PlaceholderBlock(Modifier.weight(1f).height(20.dp))
    }
}

@Composable
private fun PlaceholderBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    val contentColor = LocalTvOptionColors.current.content
    Spacer(
        modifier.placeholder(
            visible = true,
            color = contentColor.copy(alpha = .12f),
            shape = shape,
            highlight = { PlaceholderHighlight.fade(contentColor.copy(alpha = .08f)) },
        ),
    )
}

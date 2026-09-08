/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.him188.ani.leanback.ui.episode.components.TvPlayerPlaceholderBlock
import me.him188.ani.leanback.ui.episode.controls.TvPlayerEpisodeStripDefaults
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors

@Composable
internal fun TvRecommendationCardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.width(TvPlayerEpisodeStripDefaults.CardWidth).aspectRatio(16f / 9f)
            .padding(TvFocusDefaults.RingInset)
            .clip(TvPlayerEpisodeStripDefaults.CardShape)
            .background(LocalTvOptionColors.current.raised),
    ) {
        TvPlayerPlaceholderBlock(Modifier.fillMaxSize(), TvPlayerEpisodeStripDefaults.CardShape)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TvPlayerPlaceholderBlock(Modifier.fillMaxWidth(.8f).height(16.dp))
            TvPlayerPlaceholderBlock(Modifier.fillMaxWidth(.55f).height(12.dp))
        }
    }
}

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodePresentation
import me.him188.ani.app.ui.foundation.layout.cardHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.settings.mediasource.rss.test.OutlinedMatchTag

@Composable
fun SelectorTestEpisodeListGrid(
    episodes: List<SelectorTestEpisodePresentation>,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    eachItem: @Composable (SelectorTestEpisodePresentation) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        modifier,
        state,
        contentPadding,
        horizontalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardHorizontalPadding),
        verticalItemSpacing = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding,
    ) {
        for (episode in episodes) {
            item(key = episode.playUrl) {
                eachItem(episode)
            }
        }
    }
}

@Stable
object SelectorTestEpisodeListGridDefaults {
    @Composable
    fun EpisodeCard(
        episode: SelectorTestEpisodePresentation,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        EpisodeCard(
            title = { Text(episode.name.ifEmpty { "<名称为空>" }) },
            { onClick() },
            modifier,
        ) {
            episode.tags.forEach {
                OutlinedMatchTag(it)
            }
        }
    }

}

@Composable
private fun EpisodeCard(
    title: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tags: @Composable FlowRowScope.() -> Unit,
) {
    val color = CardDefaults.cardColors()
    Card(
        onClick,
        modifier,
        colors = color,
    ) {
        ListItem(
            headlineContent = title,
            supportingContent = {
                FlowRow(
                    Modifier.padding(top = 8.dp, bottom = 8.dp).width(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = tags,
                )
            },
            colors = ListItemDefaults.colors(containerColor = color.containerColor),
        )
    }
}

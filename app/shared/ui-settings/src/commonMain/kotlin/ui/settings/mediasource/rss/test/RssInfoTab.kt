/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.test.rss.RssItemInfo
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.OutlinedTag
import me.him188.ani.app.ui.media.MediaDetailsRenderer
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
val RssItemInfo.subtitleLanguageRendered: String
    get() = MediaDetailsRenderer.renderSubtitleLanguages(
        parsed.subtitleKind,
        parsed.subtitleLanguages.map { it.displayName },
    )

@Composable
@Suppress("UnusedReceiverParameter")
fun RssTestPaneDefaults.RssInfoTab(
    items: List<RssItemInfo>,
    onViewDetails: (item: RssItemInfo) -> Unit,
    selectedItemProvider: () -> RssItemInfo?,
    modifier: Modifier = Modifier,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val selectedItem by remember(selectedItemProvider) {
        derivedStateOf(selectedItemProvider)
    }
    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Adaptive(minSize = 300.dp),
        modifier,
        state = lazyStaggeredGridState,
        verticalItemSpacing = 20.dp,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(items) { item ->
            RssTestResultRssItem(
                item,
                isSelected = selectedItem == item,
                onClick = {
                    onViewDetails(item)
                },
                Modifier.animateItem().fillMaxWidth(),
            )
        }
    }
}

@Composable
fun RssTestResultRssItem(
    item: RssItemInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = CardDefaults.cardColors(
        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else CardDefaults.cardColors().containerColor,
    )
    Card(
        onClick,
        modifier.width(IntrinsicSize.Min),
        colors = color,
    ) {
        ListItem(
            headlineContent = { Text(item.rss.title) },
            supportingContent = {
                FlowRow(
                    Modifier.padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (tag in item.tags) {
                        OutlinedMatchTag(tag)
                    }
                    item.rss.pubDate?.let {
                        OutlinedTag { Text(formatDateTime(it)) }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = color.containerColor),
        )
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewRssInfoTab() {
    RssTestPaneDefaults.RssInfoTab(
        items = TestRssItemInfos,
        onViewDetails = { },
        selectedItemProvider = { TestRssItemInfos[1] },
    )
}

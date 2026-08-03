/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 追番页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvCollectionFocus : TvFocusKey {
    /** 第一个分类 tab (进页初始焦点). */
    FirstTab,

    /** 网格首卡 (tab 行按下键的显式落点). */
    FirstCard,
}

/**
 * TV 追番页 (atv-architecture.md §7.4, M2):
 * 顶部 TabRow (聚焦即选中 + 数量角标) + Adaptive 网格.
 */
@Composable
fun TvCollectionScreen(
    viewModel: TvCollectionViewModel,
    onClickSubject: (SubjectCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts by viewModel.counts.collectAsState()
    val items = viewModel.pager.collectAsLazyPagingItems()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // 统一焦点框架: 进页初始焦点落第一个 tab; tab 行按下键直达网格首卡
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvCollectionFocus.FirstTab)

    Column(modifier.fillMaxSize().tvFocusNavSignal(focus).padding(top = 24.dp)) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.padding(start = 48.dp),
        ) {
            viewModel.tabs.forEachIndexed { index, type ->
                Tab(
                    selected = selectedTabIndex == index,
                    onFocus = {
                        // 聚焦即选中 (PR 语义, §5.2)
                        selectedTabIndex = index
                        viewModel.selectTab(type)
                    },
                    modifier = Modifier
                        .then(
                            if (index == 0) {
                                Modifier.tvFocusAnchor(focus, TvCollectionFocus.FirstTab)
                            } else Modifier,
                        )
                        // 跨过网格上缘空隙直达首卡 (空间搜索跨大间距不可靠)
                        .tvFocusLink(focus, down = TvCollectionFocus.FirstCard),
                ) {
                    val count = counts?.getCount(type)
                    Text(
                        text = type.displayText() + (count?.let { " $it" } ?: ""),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        if (items.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "这里空空如也，去探索页找些番剧吧",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(124.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items.itemCount, key = { items.peek(it)?.subjectId ?: it }) { index ->
                    val info = items[index] ?: return@items
                    TvPosterCard(
                        imageUrl = info.subjectInfo.imageLarge,
                        title = info.subjectInfo.displayName,
                        onClick = { onClickSubject(info) },
                        modifier = if (index == 0) {
                            Modifier.tvFocusAnchor(focus, TvCollectionFocus.FirstCard)
                        } else Modifier,
                    )
                }
            }
        }
    }
}

private fun UnifiedCollectionType.displayText(): String = when (this) {
    UnifiedCollectionType.WISH -> "想看"
    UnifiedCollectionType.DOING -> "在看"
    UnifiedCollectionType.ON_HOLD -> "搁置"
    UnifiedCollectionType.DONE -> "看过"
    UnifiedCollectionType.DROPPED -> "抛弃"
    UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
}

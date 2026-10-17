/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.exploration.ExplorationDefaults
import me.him188.ani.app.ui.exploration.search.SubjectItemDefaults
import me.him188.ani.app.ui.exploration.search.SubjectItemLayoutParameters
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItem
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.exploration.search.TestSubjectPreviewItemInfos
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_trending_ranking
import me.him188.ani.app.ui.lang.exploration_trending_ranking_empty
import me.him188.ani.app.ui.lang.exploration_trending_ranking_heat
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchResultLazyVerticalGrid
import me.him188.ani.app.ui.search.isFinishedAndEmpty
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * @param rank 在热度排行中的名次, 从 1 开始
 * @param heat 最近 30 天在 Bangumi 标记为在看的人数
 */
@Immutable
class TrendingRankingItemPresentation(
    val rank: Int,
    val heat: Int,
    val subject: SubjectPreviewItemInfo,
)

/**
 * 完整的 Bangumi 热度排行.
 */
@Composable
fun TrendingRankingScreen(
    items: LazyPagingItems<TrendingRankingItemPresentation>,
    onClickItem: (TrendingRankingItemPresentation) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(stringResource(Lang.exploration_trending_ranking)) },
                Modifier.fillMaxWidth(),
                navigationIcon = navigationIcon,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        val horizontalPadding = windowSizeClass.paneHorizontalPadding
        val itemVerticalPadding = windowSizeClass.paneVerticalPadding / 2
        val layoutDirection = LocalLayoutDirection.current
        val itemShape = SubjectItemLayoutParameters.calculate(windowSizeClass).shape

        SearchResultLazyVerticalGrid(
            items,
            error = {
                LoadErrorCard(
                    error = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxWidth(),
                )
            },
            // 顶部留给 top bar, 底部的系统栏让列表内容可以滚动到其下方
            Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = ExplorationDefaults.ContentMaxWidth)
                .fillMaxSize(),
            cells = GridCells.Adaptive(360.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(
                start = paddingValues.calculateStartPadding(layoutDirection) + horizontalPadding,
                end = paddingValues.calculateEndPadding(layoutDirection) + horizontalPadding,
                bottom = paddingValues.calculateBottomPadding() + itemVerticalPadding,
            ),
        ) {
            if (items.isFinishedAndEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(Lang.exploration_trending_ranking_empty),
                        Modifier.padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                count = items.itemCount,
                key = { index ->
                    val item = items.peek(index)
                    // 翻页期间排行可能更新, 同一个条目可能出现两次, 所以 key 带上位置
                    if (item == null) "trending-placeholder-$index" else "trending-$index-${item.subject.subjectId}"
                },
                contentType = items.itemContentType { 1 },
            ) { index ->
                val item = items[index]
                if (item == null) {
                    Box(Modifier.size(Dp.Hairline))
                    return@items
                }
                var nsfwMode by rememberSaveable(item.subject.subjectId) {
                    mutableStateOf(item.subject.nsfwMode)
                }
                NsfwMask(
                    mode = nsfwMode,
                    onTemporarilyDisplay = { nsfwMode = NsfwMode.DISPLAY },
                    shape = itemShape,
                    Modifier.padding(vertical = itemVerticalPadding),
                ) {
                    TrendingRankingItem(
                        item,
                        onClick = { onClickItem(item) },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingRankingItem(
    item: TrendingRankingItemPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubjectPreviewItem(
        selected = false,
        onClick = onClick,
        onPlay = {},
        info = item.subject,
        modifier = modifier,
        image = {
            Box(Modifier.fillMaxSize()) {
                SubjectItemDefaults.Image(item.subject.imageUrl)
                TrendingRankBadge(item.rank, Modifier.align(Alignment.TopStart))
            }
        },
        extraInfo = {
            TrendingHeatText(item.heat)
        },
    )
}

/**
 * 封面左上角的名次. 前三名用主题色突出.
 */
@Composable
private fun TrendingRankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val highlighted = rank <= 3
    Surface(
        modifier,
        // 左上角由封面的圆角裁剪, 只需要右下角是圆的
        shape = RoundedCornerShape(bottomEnd = 12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            rank.toString(),
            Modifier
                .defaultMinSize(minWidth = 32.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrendingHeatText(
    heat: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(Lang.exploration_trending_ranking_heat, heat.toString()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@TestOnly
internal val TestTrendingRankingItems
    get() = TestSubjectPreviewItemInfos.let { infos ->
        List(12) { index ->
            TrendingRankingItemPresentation(
                rank = index + 1,
                heat = 7559 / (index + 1),
                subject = infos[index % infos.size],
            )
        }
    }

@OptIn(TestOnly::class)
@Composable
@PreviewSizeClasses
@PreviewLightDark
private fun PreviewTrendingRankingScreen() = ProvideCompositionLocalsForPreview {
    TrendingRankingScreen(
        rememberTestLazyPagingItems(TestTrendingRankingItems),
        onClickItem = {},
    )
}

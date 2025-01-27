/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingNextPage
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO

/**
 * Lazy column of [item]s, designed for My Collections.
 *
 * 自带一圈 padding
 *
 * @param item composes each item. See [SubjectCollectionInfo]
 */
@Composable
fun SubjectCollectionsColumn(
    items: LazyPagingItems<SubjectCollectionInfo>,
    item: @Composable (item: SubjectCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    enableAnimation: Boolean = true,
) {
    val isCompact = currentWindowAdaptiveInfo1().windowSizeClass.isWidthCompact
    val spacedBy = if (isCompact) 16.dp else 24.dp

    LazyVerticalGrid(
        GridCells.Adaptive(360.dp),
        modifier,
        gridState,
        contentPadding = PaddingValues(all = spacedBy / 2),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) } // 添加新 item 时保持到顶部

        if (items.loadState.hasError) {
            item {
                LoadErrorCard(
                    LoadError.fromCombinedLoadStates(items.loadState),
                    onRetry = { items.refresh() },
                    Modifier.padding(all = spacedBy / 2), // should not happen
                )
            }
        }

        items(
            items.itemCount,
            items.itemKey { it.subjectId },
            contentType = items.itemContentType { it.progressInfo.nextEpisodeIdToPlay != null },
        ) { index ->
            items[index]?.let {
                Box(
                    Modifier
                        .padding(all = spacedBy / 2)
                        .ifThen(enableAnimation) {
                            animateItem(
                                fadeInSpec = AniThemeDefaults.feedItemFadeInSpec,
                                placementSpec = AniThemeDefaults.feedItemPlacementSpec,
                                fadeOutSpec = AniThemeDefaults.feedItemFadeOutSpec,
                            )
                        },
                ) {
                    item(it)
                }
            }
        }

        if (items.isLoadingNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }

//        item(span = { GridItemSpan(maxLineSpan) }) {
//            items[items.itemCount] // trigger loading next page
//            Spacer(Modifier.height(1.dp))
//        }
    }
}

/**
 * 追番列表的一个条目卡片
 *
 * @param onClick on clicking this card (background)
 */
@Composable
fun SubjectCollectionItem(
    item: SubjectCollectionInfo,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onClick: () -> Unit,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SubjectCollectionItemDefaults.height,
    shape: Shape = SubjectCollectionItemDefaults.shape, 
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(
        onClick,
        modifier.clip(shape).fillMaxWidth().height(height),
        shape = shape,
        colors = colors,
    ) {
        Row(Modifier.weight(1f, fill = false)) {
            AsyncImage(
                item.subjectInfo.imageLarge,
                contentDescription = null,
                modifier = Modifier
                    .height(height).width(height * COVER_WIDTH_TO_HEIGHT_RATIO),
                contentScale = ContentScale.Crop,
            )

            Box(Modifier.weight(1f)) {
                SubjectCollectionItemContent(
                    item = item,
                    editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
                    onShowEpisodeList = onShowEpisodeList,
                    playButton = playButton,
                    Modifier.padding(start = 12.dp).fillMaxSize(),
                )
            }
        }
    }
}

@Stable
object SubjectCollectionItemDefaults {
    val height: Dp get() = 148.dp
    val shape: Shape
        @Composable
        get() = MaterialTheme.shapes.small
}

/**
 * 追番列表的一个条目卡片的内容
 */
@Composable
private fun SubjectCollectionItemContent(
    item: SubjectCollectionInfo,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // 标题和右上角菜单
        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.subjectInfo.displayName,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    { editableSubjectCollectionTypeState.showDropdown = true },
                    Modifier.fillMaxHeight().padding(),
                ) {
                    Icon(Icons.Outlined.MoreVert, null, Modifier.size(24.dp))
                }

                EditCollectionTypeDropDown(editableSubjectCollectionTypeState)
            }
        }

        Row(
            Modifier.padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 连载至第 28 话 · 全 34 话
            AiringLabel(
                remember(item) {
                    AiringLabelState(stateOf(item.airingInfo), stateOf(item.progressInfo))
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.weight(1f))


        Row(
            Modifier
                .padding(vertical = 12.dp)
                .padding(horizontal = 12.dp)
                .align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onShowEpisodeList) {
                Text("选集")
            }

            Box(Modifier.width(IntrinsicSize.Min)) { playButton() }
        }
    }
}


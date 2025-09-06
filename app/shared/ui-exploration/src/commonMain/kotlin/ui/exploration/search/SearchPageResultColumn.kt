/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.animation.AniMotionScheme
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.animation.SharedTransitionKeys
import me.him188.ani.app.ui.foundation.icons.BackgroundDotLarge
import me.him188.ani.app.ui.foundation.icons.GalleryThumbnail
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.interaction.keyboardDirectionToSelectItem
import me.him188.ani.app.ui.foundation.interaction.keyboardPageToScroll
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchDefaults.IconTextButton
import me.him188.ani.app.ui.search.SearchResultLazyVerticalGrid
import me.him188.ani.app.ui.search.hasFirstPage
import me.him188.ani.app.ui.search.isFinishedAndEmpty
import me.him188.ani.app.ui.subject.SubjectCoverCard
import me.him188.ani.app.ui.subject.SubjectGridDefaults
import me.him188.ani.app.ui.subject.SubjectGridLayoutParams


@Composable
internal fun SearchResultColumn(
    items: LazyPagingItems<SubjectPreviewItemInfo>,
    layoutKind: SearchResultLayoutKind,
    summary: @Composable SearchResultColumnScope.() -> Unit, // 可在还没发起任何搜索时不展示
    selectedItemIndex: () -> Int,
    onSelect: (index: Int) -> Unit,
    onPlay: (info: SubjectPreviewItemInfo) -> Unit,
    highlightSelected: Boolean = true,
    modifier: Modifier = Modifier,
    headers: LazyGridScope.() -> Unit = {},
    state: LazyGridState = rememberLazyGridState(),
    layoutParams: SearchResultColumnLayoutParams = SearchResultColumnLayoutParams.layoutParameters(kind = layoutKind),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var height by rememberSaveable { mutableIntStateOf(0) }
    val bringIntoViewRequesters = remember { mutableStateMapOf<Int, BringIntoViewRequester>() }
    val aniMotionScheme = LocalAniMotionScheme.current

    val itemsState = rememberUpdatedState(items)

    SearchResultLazyVerticalGrid(
        items,
        error = {
            LoadErrorCard(
                error = it,
                onRetry = { items.retry() },
                modifier = Modifier.fillMaxWidth(), // noop
            )
        },
        modifier
            .focusGroup()
            .onSizeChanged { height = it.height }
            .keyboardDirectionToSelectItem(
                selectedItemIndex,
            ) {
                state.animateScrollToItem(it)
                onSelect(it)
            }
            .keyboardPageToScroll({ height.toFloat() }) {
                state.animateScrollBy(it)
            },
        cells = layoutParams.grid.gridCells,
        state = state,
        horizontalArrangement = layoutParams.grid.horizontalArrangement,
        verticalArrangement = layoutParams.grid.verticalArrangement,
        contentPadding = contentPadding,
    ) {
        headers()

        item(span = { GridItemSpan(maxLineSpan) }) {
            val scope = remember(this, itemsState, aniMotionScheme) {
                SearchResultColumnScopeImpl(itemsState, aniMotionScheme)
            }

            scope.summary()
        }

        items(
            count = items.itemCount,
            key = items.itemKey { it.subjectId },
            contentType = items.itemContentType { 1 },
        ) { index ->
            val info = items[index]

            SharedTransitionLayout {
                AnimatedContent(
                    layoutParams.kind,
                    transitionSpec = aniMotionScheme.animatedContent.topLevel,
                ) { targetKind ->
                    var nsfwMaskState: NsfwMode by rememberSaveable(info?.title) {
                        mutableStateOf(info?.nsfwMode ?: NsfwMode.DISPLAY)
                    }
                    NsfwMask(
                        mode = nsfwMaskState,
                        onTemporarilyDisplay = { nsfwMaskState = NsfwMode.DISPLAY },
                        shape = layoutParams.grid.cardShape,
                    ) {
                        val animatedVisibilityScope = this
                        when (targetKind) {
                            SearchResultLayoutKind.COVER -> {
                                SubjectCoverCard(
                                    info?.title,
                                    info?.imageUrl,
                                    isPlaceholder = info == null,
                                    onClick = { onSelect(index) },
                                    Modifier
                                        .ifNotNullThen(info) {
                                            sharedElement(
                                                rememberSharedContentState(
                                                    SharedTransitionKeys.subjectCoverImage(
                                                        subjectId = it.subjectId,
                                                    ),
                                                ),
                                                animatedVisibilityScope,
                                                clipInOverlayDuringTransition = OverlayClip(layoutParams.grid.cardShape),
                                            )
                                        }
                                        .animateItem(
                                            aniMotionScheme.feedItemFadeInSpec,
                                            aniMotionScheme.feedItemPlacementSpec,
                                            aniMotionScheme.feedItemFadeOutSpec,
                                        ),
                                    shape = layoutParams.grid.cardShape,
                                )
                            }

                            SearchResultLayoutKind.PREVIEW -> {
                                if (info != null && !info.hide) {
                                    val requester = remember { BringIntoViewRequester() }
                                    // 记录 item 对应的 requester
                                    DisposableEffect(requester) {
                                        bringIntoViewRequesters[info.subjectId] = requester
                                        onDispose {
                                            bringIntoViewRequesters.remove(info.subjectId)
                                        }
                                    }

                                    SearchResultItem(
                                        info = info,
                                        selected = highlightSelected && index == selectedItemIndex(),
                                        shape = layoutParams.previewItem.shape,
                                        onClick = { onSelect(index) },
                                        onPlay = onPlay,
                                        Modifier
                                            .animateItem(
                                                aniMotionScheme.feedItemFadeInSpec,
                                                aniMotionScheme.feedItemPlacementSpec,
                                                aniMotionScheme.feedItemFadeOutSpec,
                                            )
                                            .bringIntoViewRequester(requester),
                                        imageModifier = Modifier.sharedElement(
                                            rememberSharedContentState(SharedTransitionKeys.subjectCoverImage(subjectId = info.subjectId)),
                                            animatedVisibilityScope,
                                            clipInOverlayDuringTransition = OverlayClip(layoutParams.grid.cardShape),
                                        ),
                                    )
                                } else {
                                    Box(Modifier.size(Dp.Hairline))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow(selectedItemIndex)
            .collectLatest {
                bringIntoViewRequesters[items.itemSnapshotList.getOrNull(it)?.subjectId]?.bringIntoView()
            }
    }
}

internal data class SearchResultColumnLayoutParams(
    val kind: SearchResultLayoutKind,
    val grid: SubjectGridLayoutParams,
    val previewItem: SubjectItemLayoutParameters,
) {
    companion object {
        @Composable
        fun layoutParameters(
            kind: SearchResultLayoutKind,
            windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()
        ): SearchResultColumnLayoutParams {
            val subjectItem = SubjectItemLayoutParameters.calculate(windowAdaptiveInfo.windowSizeClass)

            return SearchResultColumnLayoutParams(
                kind = kind,
                grid = when (kind) {
                    SearchResultLayoutKind.COVER -> SubjectGridDefaults.coverLayoutParameters(windowAdaptiveInfo)
                    SearchResultLayoutKind.PREVIEW -> {
                        SubjectGridLayoutParams(
                            gridCells = GridCells.Adaptive(360.dp),
                            horizontalArrangement = Arrangement.spacedBy(windowAdaptiveInfo.windowSizeClass.paneHorizontalPadding),
                            verticalArrangement = Arrangement.Top,
                            cardShape = subjectItem.shape,
                        )
                    }
                },
                subjectItem,
            )
        }
    }
}

enum class SearchResultLayoutKind {
    COVER,
    PREVIEW, ;

    companion object {
        fun next(kind: SearchResultLayoutKind): SearchResultLayoutKind {
            return entries[(kind.ordinal + 1) % entries.size]
        }
    }
}

@Composable
private fun SearchResultItem(
    info: SubjectPreviewItemInfo,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onPlay: (SubjectPreviewItemInfo) -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
) {
    SubjectPreviewItem(
        selected = selected,
        onClick = onClick,
        onPlay = { onPlay(info) },
        info = info,
        modifier
            .fillMaxWidth()
            .padding(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding / 2),
        image = {
            Box(imageModifier) {
                SubjectItemDefaults.Image(
                    info.imageUrl,
                    Modifier.clip(shape),
                )
            }
        },
        title = { maxLines ->
            Text(
                info.title,
                maxLines = maxLines,
            )
        },
    )
}

@Suppress("FunctionName")
private fun LazyGridItemScope.SearchResultColumnScopeImpl(
    itemsState: State<LazyPagingItems<SubjectPreviewItemInfo>>,
    aniMotionScheme: AniMotionScheme,
): SearchResultColumnScope = object : SearchResultColumnScope {
    @Composable
    override fun SearchSummary(
        layoutKind: SearchResultLayoutKind,
        currentSort: SearchSort,
        onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
        onSortChange: (SearchSort) -> Unit,
        modifier: Modifier
    ) {
        val modifier1 = modifier // 不要加动画, #1901
        when {
            itemsState.value.isFinishedAndEmpty -> {
                ListItem(
                    headlineContent = { Text("无搜索结果") },
                    modifier = modifier1,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                )
            }

            itemsState.value.hasFirstPage -> {
                Surface(modifier1, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.aligned(Alignment.CenterVertically),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已显示 ${itemsState.value.itemCount} 个结果", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            Modifier.weight(1f).align(Alignment.Bottom),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.aligned(Alignment.End),
                        ) {
                            LayoutKindButton(
                                layoutKind,
                                onLayoutKindChange,
                            )
                            SortButton(
                                currentSort,
                                onSortChange,
                            )
                        }
                    }
                }
            }

            else -> {
                Spacer(modifier1.height(Dp.Hairline)) // 如果空白内容, 它可能会有 bug
            }
        }
    }
}

interface SearchResultColumnScope {
    @Composable
    fun SearchSummary(
        layoutKind: SearchResultLayoutKind,
        currentSort: SearchSort,
        onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
        onSortChange: (SearchSort) -> Unit,
        modifier: Modifier = Modifier,
    )
}

@Composable
private fun LayoutKindButton(
    layoutKind: SearchResultLayoutKind,
    onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            onLayoutKindChange(SearchResultLayoutKind.next(layoutKind))
        },
        modifier,
    ) {
        Icon(
            when (layoutKind) {
                SearchResultLayoutKind.COVER -> Icons.Outlined.BackgroundDotLarge
                SearchResultLayoutKind.PREVIEW -> Icons.Outlined.GalleryThumbnail
            },
            layoutKind.name, // not good
        )
    }
}

/**
 * 切换排序方式的按钮
 *
 * @see [SearchSort]
 */
@Composable
private fun SortButton(
    currentSort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier, contentAlignment = Alignment.BottomEnd,
    ) {
        var showDropdown by rememberSaveable {
            mutableStateOf(false)
        }
        IconTextButton(
            onClick = { showDropdown = true },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.Sort, null)
            },
        ) {
            Text(getSortText(currentSort), softWrap = false)
        }
        DropdownMenu(showDropdown, { showDropdown = false }) {
            for (sort in SearchSort.entries) {
                SelectableDropdownMenuItem(
                    selected = sort == currentSort,
                    text = {
                        Text(
                            getSortText(sort),
                            softWrap = false,
                        )
                    },
                    onClick = {
                        showDropdown = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

private fun getSortText(currentSort: SearchSort): String = when (currentSort) {
    SearchSort.MATCH -> "最佳匹配"
    SearchSort.COLLECTION -> "最多收藏"
    SearchSort.RANK -> "最高排名"
}

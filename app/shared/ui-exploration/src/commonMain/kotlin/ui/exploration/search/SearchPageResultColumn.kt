/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.interaction.keyboardDirectionToSelectItem
import me.him188.ani.app.ui.foundation.interaction.keyboardPageToScroll
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchDefaults
import me.him188.ani.app.ui.search.SearchResultLazyVerticalStaggeredGrid


@Composable
internal fun SearchPageResultColumn(
    items: LazyPagingItems<SubjectPreviewItemInfo>,
    showSummary: () -> Boolean, // 可在还没发起任何搜索时不展示
    selectedItemIndex: () -> Int,
    onSelect: (index: Int) -> Unit,
    onPlay: (info: SubjectPreviewItemInfo) -> Unit,
    modifier: Modifier = Modifier,
    headers: LazyStaggeredGridScope.() -> Unit = {},
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState()
) {
    var height by rememberSaveable { mutableIntStateOf(0) }
    val bringIntoViewRequesters = remember { mutableStateMapOf<Int, BringIntoViewRequester>() }
    val nsfwBlurShape = SubjectItemLayoutParameters.calculate(currentWindowAdaptiveInfo1().windowSizeClass).shape
    val aniMotionScheme = LocalAniMotionScheme.current

    SearchResultLazyVerticalStaggeredGrid(
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
        lazyStaggeredGridState = state,
        horizontalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
    ) {
        headers()

        if (showSummary()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SearchDefaults.SearchSummaryItem(
                    items,
                    Modifier.animateItem(
                        fadeInSpec = aniMotionScheme.feedItemFadeInSpec,
                        placementSpec = aniMotionScheme.feedItemPlacementSpec,
                        fadeOutSpec = aniMotionScheme.feedItemFadeOutSpec,
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                )
            }
        }

        items(
            items.itemCount,
            key = items.itemKey { it.subjectId },
            contentType = items.itemContentType { 1 },
        ) { index ->
            val info = items[index]
            val requester = remember { BringIntoViewRequester() }
            // 记录 item 对应的 requester
            if (info != null) {
                DisposableEffect(requester) {
                    bringIntoViewRequesters[info.subjectId] = requester
                    onDispose {
                        bringIntoViewRequesters.remove(info.subjectId)
                    }
                }

                var nsfwMaskState: NsfwMode by rememberSaveable(info) {
                    mutableStateOf(info.nsfwMode)
                }
                NsfwMask(
                    mode = nsfwMaskState,
                    onTemporarilyDisplay = { nsfwMaskState = NsfwMode.DISPLAY },
                    shape = nsfwBlurShape,
                ) {
                    SubjectPreviewItem(
                        selected = index == selectedItemIndex(),
                        onClick = { onSelect(index) },
                        onPlay = { onPlay(info) },
                        info = info,
                        Modifier
//                        .sharedElement(
//                            rememberSharedContentState(SharedTransitionKeys.subjectBounds(info.subjectId)),
//                            animatedVisibilityScope,
//                        )
                            .animateItem(
                                fadeInSpec = aniMotionScheme.feedItemFadeInSpec,
                                placementSpec = aniMotionScheme.feedItemPlacementSpec,
                                fadeOutSpec = aniMotionScheme.feedItemFadeOutSpec,
                            )
                            .fillMaxWidth()
                            .bringIntoViewRequester(requester)
                            .padding(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding / 2),
                        image = {
                            SubjectItemDefaults.Image(
                                info.imageUrl,
//                            Modifier.sharedElement(
//                                rememberSharedContentState(SharedTransitionKeys.subjectCoverImage(subjectId = info.subjectId)),
//                                animatedVisibilityScope,
//                            ),
                            )
                        },
                        title = { maxLines ->
                            Text(
                                info.title,
//                            Modifier.sharedElement(
//                                rememberSharedContentState(SharedTransitionKeys.subjectTitle(subjectId = info.subjectId)),
//                                animatedVisibilityScope,
//                            ),
                                maxLines = maxLines,
                            )
                        },
                    )
                }
            } else {
                Box(Modifier.size(Dp.Hairline))
                // placeholder
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

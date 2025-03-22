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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.keyboardDirectionToSelectItem
import me.him188.ani.app.ui.foundation.interaction.keyboardPageToScroll
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchDefaults
import me.him188.ani.app.ui.search.SearchResultLazyVerticalStaggeredGrid
import me.him188.ani.app.ui.search.collectHasQueryAsState

@Composable
fun SearchPage(
    state: SearchPageState,
    detailContent: @Composable PaneScope.(subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (index: Int, item: SubjectPreviewItemInfo) -> Unit = { _, _ -> },
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.navigateBack()
        }
    }

    val items = state.items
    val searchBar = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SuggestionSearchBar(
                state.suggestionSearchBarState,
                Modifier
                    .ifThen(
                        currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
                                || !state.suggestionSearchBarState.expanded,
                    ) { Modifier.padding(horizontal = 8.dp) },
                placeholder = { Text("搜索") },
            )

            val filterState by state.searchFilterStateFlow.collectAsStateWithLifecycle()
            SearchFilterChipsRow(
                filterState,
                onClickChip = { _, value ->
                    state.toggleTagSelection(value)
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
    SearchPageLayout(
        navigator,
        searchResultList = {
            val aniNavigator = LocalNavigator.current
            val scope = rememberCoroutineScope()

            val hasQuery by state.searchState.collectHasQueryAsState()
            SearchPageResultColumn(
                items = items,
                showSummary = { hasQuery },
                selectedItemIndex = { state.selectedItemIndex },
                onSelect = { index ->
                    items[index]?.let {
                        onSelect(index, it)
                    }
                },
                onPlay = { info ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val playInfo = state.requestPlay(info)
                        playInfo?.let {
                            aniNavigator.navigateEpisodeDetails(it.subjectId, playInfo.episodeId)
                        }
                    }
                }, // collect only once
                searchBar = searchBar,
                state = state.gridState,
            )
        },
        detailContent = {
//            AnimatedContent(
//                state.selectedItemIndex,
//                transitionSpec = AniThemeDefaults.emphasizedAnimatedContentTransition,
//            ) { index ->
//               
//            }
            items.itemSnapshotList.getOrNull(state.selectedItemIndex)?.let {
                detailContent(it.subjectId)
            }
        },
        modifier,
        navigationIcon = {
            if (navigator.canNavigateBack()) {
                BackNavigationIconButton(
                    {
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            navigator.navigateBack()
                        }
                    },
                )
            } else {
                navigationIcon()
            }
        },
        contentWindowInsets = contentWindowInsets,
    )
}

@Composable
internal fun SearchPageResultColumn(
    items: LazyPagingItems<SubjectPreviewItemInfo>,
    showSummary: () -> Boolean, // 可在还没发起任何搜索时不展示
    selectedItemIndex: () -> Int,
    onSelect: (index: Int) -> Unit,
    onPlay: (info: SubjectPreviewItemInfo) -> Unit,
    searchBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
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
                onSelect(it)
                state.animateScrollToItem(it)
            }
            .keyboardPageToScroll({ height.toFloat() }) {
                state.animateScrollBy(it)
            },
        lazyStaggeredGridState = state,
        horizontalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            searchBar()
        }

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

/**
 * @param searchBar contentPadding: 页面的左右 24.dp 边距
 */
@Composable
internal fun SearchPageLayout(
    navigator: ThreePaneScaffoldNavigator<*>,
    searchResultList: @Composable (PaneScope.() -> Unit),
    detailContent: @Composable (PaneScope.() -> Unit),
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val coroutineScope = rememberCoroutineScope()
    AniListDetailPaneScaffold(
        navigator,
        listPaneTopAppBar = {
            AniTopAppBar(
                title = { Text("搜索") },
                Modifier.fillMaxWidth(),
                navigationIcon = {
                    if (navigator.canNavigateBack()) {
                        BackNavigationIconButton(
                            onNavigateBack = {
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    navigator.navigateBack()
                                }
                            },
                        )
                    } else {
                        navigationIcon()
                    }
                },
                windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        listPaneContent = {
            Column(
                Modifier
                    .paneContentPadding()
                    .paneWindowInsetsPadding(),
//                        .padding(top = searchBarHeight),
            ) {
                searchResultList()
            }
        },
        detailPane = {
            detailContent()
        },
        modifier,
        useSharedTransition = false,
        listPanePreferredWidth = 480.dp,
        contentWindowInsets = contentWindowInsets,
    )
}

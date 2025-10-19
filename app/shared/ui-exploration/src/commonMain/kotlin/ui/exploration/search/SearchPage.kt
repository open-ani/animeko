/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.search.collectHasQueryAsState
import me.him188.ani.utils.platform.isDesktop

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
    val scope = rememberCoroutineScope()

    val items = state.items
    SearchPageListDetailScaffold(
        navigator,
        hasSelectedItem = remember(state) {
            derivedStateOf {
                state.selectedItemIndex != -1
            }
        }.value,
        searchBar = {
            SuggestionSearchBar(
                state.suggestionSearchBarState,
                Modifier.padding(bottom = 16.dp),
                placeholder = { Text("关键词") },
                windowInsets = contentWindowInsets.only(WindowInsetsSides.Horizontal),
            )
        },
        searchResultColumn = { nestedScrollConnection ->
            val aniNavigator = LocalNavigator.current

            val hasQuery by state.searchState.collectHasQueryAsState()
            val query by state.queryFlow.collectAsStateWithLifecycle()
            BoxWithConstraints {
                SearchResultColumn(
                    items = items,
                    layoutKind = state.layoutKind,
                    summary = {
                        if (hasQuery) {
                            SearchSummary(
                                state.layoutKind,
                                query.sort,
                                onLayoutKindChange = { state.layoutKind = it },
                                onSortChange = { state.updateSort(it) },
                            )
                        }
                    },
                    selectedItemIndex = { state.selectedItemIndex },
                    onSelect = { index ->
                        items[index]?.let {
                            onSelect(index, it)
                        }
                        val didSwitchUI = if (
                            !isSinglePane
                            && navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.List
                            && state.layoutKind != SearchResultLayoutKind.PREVIEW
                        ) {
                            // 在多页模式下, 在 list 模式点击 item, 自动切换列表 layout 为详细模式, 避免左侧太拥挤
                            state.layoutKind = SearchResultLayoutKind.PREVIEW
                            true
                        } else {
                            false
                        }
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            // 切换 UI 后, 被选择的位置可能会变, 所以需要 scroll 到那个元素
                            val shouldAnimateScroll = didSwitchUI
                                    && navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail // 如果已经打开了右边, 就不要动 scroll. 我们只在初次切换 UI 时 scroll.
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) // 打开右边
                            if (shouldAnimateScroll) {
                                state.gridState.animateScrollToItem(index)
                            }
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
                    headers = {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val filterState by state.searchFilterStateFlow.collectAsStateWithLifecycle()
                            SearchFilterChipsRow(
                                filterState,
                                onClickItemText = { chip, value ->
                                    state.toggleTagSelection(chip, value, unselectOthersOfSameKind = true)
                                },
                                onCheckedChange = { chip, value ->
                                    state.toggleTagSelection(chip, value, unselectOthersOfSameKind = false)
                                },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    highlightSelected = !isSinglePane,
                    state = state.gridState,
                    layoutParams = SearchResultColumnLayoutParams.layoutParameters(
                        kind = state.layoutKind,
                        windowAdaptiveInfo = WindowAdaptiveInfo(
                            WindowSizeClass(maxWidth.value, maxHeight.value),
                            currentWindowAdaptiveInfo1().windowPosture,
                        ),
                    ),
                    contentPadding = PaddingValues(bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding)
                        .plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()),
                )
            }
        },
        detailContent = {
            items.itemSnapshotList.getOrNull(state.selectedItemIndex)?.let {
                detailContent(it.subjectId)
            }
        },
        navigateToTopButton = {
            AnimatedVisibility(
                state.gridState.canScrollBackward,
            ) {
                SmallFloatingActionButton(
                    {
                        scope.launch {
                            state.gridState.animateScrollToItem(0)
                        }
                    },
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "回到顶部")
                }
            }
        },
        modifier,
        navigationIcon = {
            if (
                navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
                && state.selectedItemIndex != -1
            ) { // navigator.canNavigate will always return false on Two Pane mode
                BackNavigationIconButton(
                    {
                        // Unselect, then hasSelectedItem will change
                        state.selectedItemIndex = -1
                    },
                )
            } else {
                navigationIcon()
            }
        },
        contentWindowInsets = contentWindowInsets,
    )
}

/**
 * @param searchBar contentPadding: 页面的左右 24.dp 边距
 */
@Composable
internal fun SearchPageListDetailScaffold(
    navigator: ThreePaneScaffoldNavigator<*>,
    hasSelectedItem: Boolean,
    searchBar: @Composable (PaneScope.() -> Unit),
    searchResultColumn: @Composable (PaneScope.(NestedScrollConnection?) -> Unit),
    detailContent: @Composable (PaneScope.() -> Unit),
    navigateToTopButton: @Composable PaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val coroutineScope = rememberCoroutineScope()

    val topAppBarScrollBehavior: TopAppBarScrollBehavior? = if (LocalPlatform.current.isDesktop()) {
        // Workaround for Compose bug: scrolling to the top does not work correctly.
        null
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

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
                scrollBehavior = topAppBarScrollBehavior,
                // 此处 TopAppBar 滚动后会折叠，滚动前后使用相同的配色避免界面与状态栏颜色不同
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                )
            )
        },
        listPaneContent = {
            Scaffold(
                floatingActionButton = { navigateToTopButton() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) { _ -> // We only need window insets for the FAB
                Column(
                    Modifier
                        .paneContentPadding()
                        .paneWindowInsetsPadding()
                        .run {
                            if (topAppBarScrollBehavior == null) this
                            else nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        },
                ) {
                    searchBar()
                    searchResultColumn(topAppBarScrollBehavior?.nestedScrollConnection)
                }
            }
        },
        detailPane = {
            detailContent()
        },
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        useSharedTransition = false,
        scaffoldValue = if (!hasSelectedItem) {
            // 如果没有选中项目, 全屏显示 List 页面.
            ThreePaneScaffoldValue(
                primary = PaneAdaptedValue.Hidden,
                secondary = PaneAdaptedValue.Expanded,
                tertiary = PaneAdaptedValue.Hidden,
            )
        } else {
            navigator.scaffoldValue
        },
        contentWindowInsets = contentWindowInsets,
    )
}

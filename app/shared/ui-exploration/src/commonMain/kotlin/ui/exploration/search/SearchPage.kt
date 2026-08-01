/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.ui.adaptive.AdaptiveSearchBar
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.interaction.onEnterKeyEvent
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search
import me.him188.ani.app.ui.lang.exploration_search_back_to_top
import me.him188.ani.app.ui.lang.settings_mediasource_test_keyword
import me.him188.ani.app.ui.search.TestSearchState
import me.him188.ani.app.ui.search.collectItemsWithLifecycle
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchPage(
    state: SearchPageState,
    onIntent: (SearchPageIntent) -> Unit,
    suggestionsPager: (String) -> Flow<PagingData<String>>,
    detailContent: @Composable PaneScope.(subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    // 沉浸式变体 (与沉浸式探索页共用同一开关, 关闭则回退下方默认布局)
    val pageVariant = LocalSearchPageVariant.current
    if (pageVariant != null && LocalThemeSettings.current.tvImmersiveExploration) {
        pageVariant.Page(
            state = state,
            onIntent = onIntent,
            suggestionsPager = suggestionsPager,
            modifier = modifier,
        )
        return
    }
    val searchText = stringResource(Lang.exploration_search)
    val keywordText = stringResource(Lang.settings_mediasource_test_keyword)
    val backToTopText = stringResource(Lang.exploration_search_back_to_top)
    val coroutineScope = rememberCoroutineScope()
    val items = state.searchState.collectItemsWithLifecycle()
    val focusManager = LocalFocusManager.current
    var isSearchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var layoutKind by rememberSaveable { mutableStateOf(SearchResultLayoutKind.COVER) }
    var editingQuery by rememberSaveable(state.query.keywords) { mutableStateOf(state.query.keywords) }
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val gridFocusRequester = remember { FocusRequester() }

    // 焦点导航: 点击结果直接导航到全屏详情页 (不用左右两栏 —— 右侧详情栏难以聚焦交互);
    // 从详情页返回时把焦点还给刚才点击的格子.
    // tick 只在页面 ON_RESUME 且有待恢复项时增加, 避免点击后离开页面前的重组把恢复状态消耗掉.
    var focusRestoreIndex by rememberSaveable { mutableIntStateOf(-1) }
    var focusRestoreTick by remember { mutableIntStateOf(0) }
    if (focusDriven) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && focusRestoreIndex >= 0) {
                    focusRestoreTick++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        // 进入页面时的初始焦点 (原先由左上角返回按钮提供, 该按钮在 TV 上已移除):
        // 有结果时聚焦结果格子. 无结果或请求失败时不动作, 由 AniAppContent 的导航级
        // 焦点兜底把焦点交给页面第一个可聚焦元素 (即搜索框).
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (focusRestoreIndex < 0) {
                runCatching { gridFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(state.hasSelectedItem) {
        if (state.hasSelectedItem) {
            isSearchBarExpanded = false
            if (!focusDriven) focusManager.clearFocus(force = true)
        }
    }

    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.navigateBack()
        }
    }

    SearchPageListDetailScaffold(
        navigator = navigator,
        // 焦点导航不使用左右两栏: 结果格子占满全宽, 点击直接导航到全屏详情页.
        hasSelectedItem = !focusDriven && state.hasSelectedItem,
        searchBar = {
            SearchPageSearchBar(
                state = state,
                onIntent = onIntent,
                suggestionsPager = suggestionsPager,
                editingQuery = editingQuery,
                onEditingQueryChange = { editingQuery = it },
                expanded = isSearchBarExpanded,
                onExpandedChange = { expanded ->
                    isSearchBarExpanded = expanded
                    if (!expanded && focusDriven) {
                        coroutineScope.launch {
                            runCatching { gridFocusRequester.requestFocus() }
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp),
                placeholder = { Text(keywordText) },
                windowInsets = contentWindowInsets.only(WindowInsetsSides.Horizontal),
                focusDriven = focusDriven,
            )
        },
        searchResultColumn = {
            BoxWithConstraints {
                SearchResultColumn(
                    items = items,
                    layoutKind = layoutKind,
                    summary = {
                        if (state.hasActiveSearch) {
                            SearchSummary(
                                layoutKind = layoutKind,
                                currentSort = state.query.sort,
                                onLayoutKindChange = {
                                    layoutKind = it
                                },
                                onSortChange = {
                                    onIntent(SearchPageIntent.ChangeSort(it))
                                },
                            )
                        }
                    },
                    selectedItemIndex = { state.selectedItemIndex },
                    onSelect = { index ->
                        val item = items[index] ?: return@SearchResultColumn
                        if (focusDriven || listDetailLayoutParameters.preferSinglePane) {
                            if (focusDriven) focusRestoreIndex = index
                            isSearchBarExpanded = false
                            onIntent(
                                SearchPageIntent.OpenSubjectDetails(
                                    index = index,
                                    item = item,
                                ),
                            )
                            return@SearchResultColumn
                        }

                        val didSwitchLayout =
                            navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.List &&
                                    layoutKind != SearchResultLayoutKind.PREVIEW

                        if (didSwitchLayout) {
                            layoutKind = SearchResultLayoutKind.PREVIEW
                        }
                        isSearchBarExpanded = false

                        onIntent(
                            SearchPageIntent.SelectResult(
                                index = index,
                                item = item,
                            ),
                        )

                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            val shouldAnimateScroll = didSwitchLayout &&
                                    navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                            if (shouldAnimateScroll) {
                                gridState.animateScrollToItem(index)
                            }
                        }
                    },
                    onFocusItem = { index ->
                        val item = items[index] ?: return@SearchResultColumn
                        // 焦点导航没有右侧详情栏, 焦点移动不需要更新选中项 (也避免每次焦点移动都加载详情).
                        if (!focusDriven && !listDetailLayoutParameters.preferSinglePane) {
                            // Only update the selection (detail pane content); do NOT call
                            // navigator.navigateTo here, which would trigger a layout change.
                            onIntent(
                                SearchPageIntent.SelectResult(
                                    index = index,
                                    item = item,
                                ),
                            )
                        }
                    },
                    onPlay = {
                        onIntent(SearchPageIntent.Play(it))
                    },
                    headers = {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchFilterChipsRow(
                                state = state.searchFilterState,
                                onClickItemText = { chip, value ->
                                    val updatedQuery = state.toggleTagSelection(
                                        tag = chip,
                                        value = value,
                                        unselectOthersOfSameKind = true,
                                    ).query
                                    onIntent(
                                        SearchPageIntent.UpdateQuery(
                                            updatedQuery,
                                        ),
                                    )
                                },
                                onCheckedChange = { chip, value ->
                                    val updatedQuery = state.toggleTagSelection(
                                        tag = chip,
                                        value = value,
                                        unselectOthersOfSameKind = false,
                                    ).query
                                    onIntent(
                                        SearchPageIntent.UpdateQuery(
                                            updatedQuery,
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    modifier = if (focusDriven) Modifier.focusRequester(gridFocusRequester) else Modifier,
                    focusRestoreIndex = focusRestoreIndex,
                    focusRestoreTick = focusRestoreTick,
                    onFocusRestored = { focusRestoreIndex = -1 },
                    highlightSelected = !isSinglePane,
                    state = gridState,
                    layoutParams = SearchResultColumnLayoutParams.layoutParameters(
                        kind = layoutKind,
                        windowAdaptiveInfo = WindowAdaptiveInfo(
                            WindowSizeClass(maxWidth.value, maxHeight.value),
                            currentWindowAdaptiveInfo1().windowPosture,
                        ),
                    ),
                    contentPadding = PaddingValues(
                        bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding,
                    ).plus(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    ),
                )
            }
        },
        detailContent = {
            items.itemSnapshotList.getOrNull(state.selectedItemIndex)?.let {
                detailContent(it.subjectId)
            }
        },
        navigateToTopButton = {
            AnimatedVisibility(gridState.canScrollBackward) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, backToTopText)
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (
                navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail &&
                state.hasSelectedItem
            ) {
                BackNavigationIconButton(
                    onNavigateBack = {
                        onIntent(SearchPageIntent.ClearSelection)
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
private fun SearchPageSearchBar(
    state: SearchPageState,
    onIntent: (SearchPageIntent) -> Unit,
    suggestionsPager: (String) -> Flow<PagingData<String>>,
    editingQuery: String,
    onEditingQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    inputFieldModifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forSearchBar(),
    placeholder: @Composable (() -> Unit)? = null,
    focusDriven: Boolean = false,
) {
    var debouncedEditingQuery by remember { mutableStateOf(editingQuery) }

    LaunchedEffect(editingQuery) {
        delay(800)
        debouncedEditingQuery = editingQuery
    }

    BackHandler(expanded) {
        onExpandedChange(false)
    }

    AdaptiveSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = editingQuery,
                onQueryChange = {
                    onEditingQueryChange(it.trim('\n'))
                },
                onSearch = {
                    onExpandedChange(false)
                    onIntent(
                        SearchPageIntent.UpdateQuery(
                            state.query.copy(keywords = editingQuery),
                            submit = true,
                        ),
                    )
                },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                modifier = inputFieldModifier
                    .fillMaxWidth()
                    .run {
                        // On TV focus moves from the input to the suggestion list via D-pad;
                        // the input loses isFocused but the search bar should stay expanded.
                        // On non-TV, losing focus (e.g. clicking elsewhere) should collapse it.
                        if (focusDriven) this
                        else onFocusChanged { focusState ->
                            if (!focusState.isFocused && expanded) {
                                onExpandedChange(false)
                            }
                        }
                    }
                    .onEnterKeyEvent {
                        onExpandedChange(false)
                        onIntent(
                            SearchPageIntent.UpdateQuery(
                                state.query.copy(keywords = editingQuery),
                                submit = true,
                            ),
                        )
                        true
                    },
                placeholder = placeholder,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (editingQuery.isNotEmpty() || expanded) {
                    {
                        IconButton(
                            onClick = {
                                onExpandedChange(false)
                                onEditingQueryChange("")
                                onIntent(
                                    SearchPageIntent.UpdateQuery(
                                        state.query.copy(keywords = ""),
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                } else {
                    null
                },
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        windowInsets = windowInsets,
    ) {
        val previewType = if (editingQuery.isEmpty()) {
            SuggestionSearchPreviewType.HISTORY
        } else {
            SuggestionSearchPreviewType.SUGGESTIONS
        }
        val values = when (previewType) {
            SuggestionSearchPreviewType.HISTORY -> state.searchHistoryPager
            SuggestionSearchPreviewType.SUGGESTIONS -> suggestionsPager(debouncedEditingQuery)
        }.collectAsLazyPagingItemsWithLifecycle()

        LazyColumn {
            items(
                count = values.itemCount,
                key = values.itemKey { "search-suggestion-$it" },
                contentType = values.itemContentType { 1 },
            ) { index ->
                val text = values[index] ?: return@items
                ListItem(
                    leadingContent = if (previewType == SuggestionSearchPreviewType.HISTORY) {
                        { Icon(Icons.Default.History, contentDescription = null) }
                    } else {
                        null
                    },
                    headlineContent = { Text(text) },
                    modifier = Modifier.clickable {
                        onEditingQueryChange(text)
                        onExpandedChange(false)
                        onIntent(
                            SearchPageIntent.UpdateQuery(
                                state.query.copy(keywords = text),
                                submit = true,
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    trailingContent = if (previewType == SuggestionSearchPreviewType.HISTORY) {
                        {
                            IconButton(
                                onClick = {
                                    onIntent(SearchPageIntent.RemoveHistory(text))
                                },
                                enabled = state.removingHistory != text,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除 $text")
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
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
    val searchText = stringResource(Lang.exploration_search)

    val topAppBarScrollBehavior: TopAppBarScrollBehavior? = if (LocalPlatform.current.isDesktop()) {
        null
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

    AniListDetailPaneScaffold(
        navigator = navigator,
        listPaneTopAppBar = {
            AniTopAppBar(
                title = { Text(searchText) },
                modifier = Modifier.fillMaxWidth(),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
        listPaneContent = {
            Scaffold(
                floatingActionButton = { navigateToTopButton() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) { _ ->
                Column(
                    modifier = Modifier
                        .paneContentPadding()
                        .paneWindowInsetsPadding()
                        .run {
                            if (topAppBarScrollBehavior == null) {
                                this
                            } else {
                                nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                            }
                        },
                ) {
                    searchBar()
                    searchResultColumn(topAppBarScrollBehavior?.nestedScrollConnection)
                }
            }
        },
        detailPane = detailContent,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        useSharedTransition = false,
        scaffoldValue = if (!hasSelectedItem) {
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

@Composable
@PreviewSizeClasses
@Preview
fun PreviewSearchPage() = ProvideCompositionLocalsForPreview {
    PreviewSearchPageImpl()
}

@OptIn(TestOnly::class)
@Composable
@PreviewSizeClasses
@PreviewLightDark
fun PreviewSearchPageEmptyResult() = ProvideCompositionLocalsForPreview {
    PreviewSearchPageImpl(
        createTestSearchPageState(
            searchState = TestSearchState(
                MutableStateFlow(MutableStateFlow(PagingData.from(emptyList()))),
            ),
        ),
    )
}

/**
 * @sample me.him188.ani.app.ui.search.PreviewLoadErrorCard
 */
@OptIn(TestOnly::class)
@Composable
@PreviewSizeClasses
@PreviewLightDark
fun PreviewSearchPageError() = ProvideCompositionLocalsForPreview {
    PreviewSearchPageImpl(
        createTestSearchPageState(
            searchState = TestSearchState(
                MutableStateFlow(
                    MutableStateFlow(
                        PagingData.from(
                            emptyList(),
                            sourceLoadStates = LoadStates(
                                LoadState.NotLoading(true),
                                LoadState.NotLoading(true),
                                LoadState.Error(RepositoryNetworkException()),
                            ),
                            mediatorLoadStates = LoadStates(
                                LoadState.NotLoading(true),
                                LoadState.NotLoading(true),
                                LoadState.Error(RepositoryNetworkException()),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

@Composable
@PreviewLightDark
fun PreviewSearchPageResultColumn() = ProvideCompositionLocalsForPreview {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        val state = createTestFinishedSubjectSearchState()
        SearchResultColumn(
            items = state.collectItemsWithLifecycle(),
            layoutKind = SearchResultLayoutKind.PREVIEW,
            summary = {
                SearchSummary(
                    layoutKind = SearchResultLayoutKind.PREVIEW,
                    currentSort = SearchSort.MATCH,
                    onLayoutKindChange = {},
                    onSortChange = {},
                )
            },
            selectedItemIndex = { 1 },
            onSelect = {},
            onPlay = {},
            headers = {},
        )
    }
}

@Composable
@OptIn(TestOnly::class)
private fun PreviewSearchPageImpl(state: SearchPageState = createTestSearchPageState()) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        SearchPage(
            state = state,
            onIntent = {},
            suggestionsPager = { MutableStateFlow(PagingData.from(listOf("suggestion1"))) },
            detailContent = { Text("Hello, World!") },
        )
    }
}

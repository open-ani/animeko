/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.keyboardDirectionToSelectItem
import me.him188.ani.app.ui.foundation.interaction.keyboardPageToScroll
import me.him188.ani.app.ui.foundation.layout.AniListDetailPaneScaffold
import me.him188.ani.app.ui.foundation.layout.compareTo
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.search.SearchDefaults
import me.him188.ani.app.ui.search.SearchState

@Composable
fun SearchPage(
    state: SearchPageState,
    windowInsets: WindowInsets,
    detailContent: @Composable (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
    focusSearchBarByDefault: Boolean = true,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
) {
    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
    }

    val focusRequester = remember { FocusRequester() }
    SearchPageLayout(
        navigator,
        windowInsets,
        searchBar = { contentPadding ->
            SuggestionSearchBar(
                state.suggestionSearchBarState,
                Modifier
                    .ifThen(
                        currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass >= WindowWidthSizeClass.MEDIUM
                                || !state.suggestionSearchBarState.expanded,
                    ) { contentPadding },
                inputFieldModifier = Modifier.focusRequester(focusRequester),
                windowInsets = windowInsets,
                placeholder = { Text("搜索") },
            )
        },
        searchResultList = {
            val aniNavigator = LocalNavigator.current
            val scope = rememberCoroutineScope()
            SearchPageResultColumn(
                state.searchState,
                selectedItemIndex = { state.selectedItemIndex },
                onSelect = { index ->
                    state.selectedItemIndex = index
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                },
                onPlay = { info ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val playInfo = state.requestPlay(info)
                        playInfo?.let {
                            aniNavigator.navigateEpisodeDetails(it.subjectId, playInfo.episodeId)
                        }
                    }
                },
            )
        },
        detailContent = {
            AnimatedContent(
                state.selectedItemIndex,
                transitionSpec = AniThemeDefaults.emphasizedAnimatedContentTransition,
            ) { index ->
                state.searchState.items.getOrNull(index)?.let {
                    detailContent(it.id)
                }
            }
        },
        modifier,
    )
    if (focusSearchBarByDefault) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun SearchPageResultColumn(
    state: SearchState<SubjectPreviewItemInfo>,
    selectedItemIndex: () -> Int,
    onSelect: (index: Int) -> Unit,
    onPlay: (info: SubjectPreviewItemInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    var height by remember { mutableIntStateOf(0) }

    val bringIntoViewRequesters = remember { mutableStateMapOf<Int, BringIntoViewRequester>() }

    SearchDefaults.ResultColumn(
        state,
        modifier
            .focusGroup()
            .onSizeChanged { height = it.height }
            .keyboardDirectionToSelectItem(selectedItemIndex, onSelect, lazyListState)
            .keyboardPageToScroll({ height.toFloat() }, lazyListState),
        lazyListState = lazyListState,
    ) {
        itemsIndexed(
            state.items,
            key = { _, it -> it.id },
            contentType = { _, _ -> 1 },
        ) { index, info ->
            val requester = remember { BringIntoViewRequester() }
            // 记录 item 对应的 requester
            DisposableEffect(requester) {
                bringIntoViewRequesters[info.id] = requester
                onDispose {
                    bringIntoViewRequesters.remove(info.id)
                }
            }

            SubjectPreviewItem(
                selected = index == selectedItemIndex(),
                onClick = { onSelect(index) },
                onPlay = { onPlay(info) },
                info = info,
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(requester)
                    .padding(vertical = currentWindowAdaptiveInfo().windowSizeClass.paneVerticalPadding / 2),
            )
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow(selectedItemIndex)
            .collectLatest {
                bringIntoViewRequesters[state.items.getOrNull(it)?.id]?.bringIntoView()
            }
    }
}

/**
 * @param searchBar contentPadding: 页面的左右 24.dp 边距
 */
@Composable
internal fun SearchPageLayout(
    navigator: ThreePaneScaffoldNavigator<*>,
    windowInsets: WindowInsets,
    searchBar: @Composable (contentPadding: Modifier) -> Unit,
    searchResultList: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    searchBarHeight: Dp = 64.dp,
) {
    AniListDetailPaneScaffold(
        navigator,
        listPaneTopAppBar = {
            AniTopAppBar(
                title = { Text("搜索") },
                windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                Modifier.fillMaxWidth(),
                navigationIcon = {
                    TopAppBarGoBackButton()
                },
            )
        },
        listPaneContent = {
            Box(
                Modifier
                    .consumeWindowInsets(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                // Use TopAppBar as a container for scroll behavior
//                TopAppBar(
//                    title = { searchBar() },
//                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
////                    expandedHeight = with(LocalDensity.current) {
////                        LocalWindowInfo.current.containerSize.height.toDp()
////                    },
//                    colors = AniThemeDefaults.topAppBarColors(),
//                )
                Column(
                    Modifier
                        .paneContentPadding()
                        .padding(top = searchBarHeight),
                ) {
                    searchResultList()
                }

                Row(Modifier.fillMaxWidth()) {
                    searchBar(Modifier.paneContentPadding())
                }
            }
        },
        detailPane = {
            detailContent()
        },
        modifier,
        listPanePreferredWidth = 480.dp,
    )
}

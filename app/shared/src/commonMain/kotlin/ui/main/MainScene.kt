/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScenePage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuite
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteLayout
import me.him188.ani.app.ui.cache.CacheManagementPage
import me.him188.ani.app.ui.cache.CacheManagementViewModel
import me.him188.ani.app.ui.exploration.ExplorationPage
import me.him188.ani.app.ui.exploration.search.SearchPage
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.subject.collection.CollectionPage
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsPage
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.update.TextButtonUpdateLogo
import me.him188.ani.utils.platform.isAndroid


@Composable
fun MainScene(
    page: MainScenePage,
    modifier: Modifier = Modifier,
    onNavigateToPage: (MainScenePage) -> Unit,
    onNavigateToSettings: () -> Unit,
    navigationLayoutType: NavigationSuiteType = AniNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    if (LocalPlatform.current.isAndroid()) {
        val context = LocalContext.current
        val window = LocalPlatformWindow.current
        LaunchedEffect(true) {
            context.setRequestFullScreen(window, false)
        }
    }

    MainSceneContent(page, onNavigateToPage, onNavigateToSettings, modifier, navigationLayoutType)
}

@Composable
private fun MainSceneContent(
    page: MainScenePage,
    onNavigateToPage: (MainScenePage) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    navigationLayoutType: NavigationSuiteType = AniNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    AniNavigationSuiteLayout(
        navigationSuite = {
            AniNavigationSuite(
                layoutType = navigationLayoutType,
                colors = NavigationSuiteDefaults.colors(
                    navigationDrawerContainerColor = AniThemeDefaults.navigationContainerColor,
                    navigationBarContainerColor = AniThemeDefaults.navigationContainerColor,
                    navigationRailContainerColor = AniThemeDefaults.navigationContainerColor,
                ),
                navigationRailHeader = {
                    FloatingActionButton(
                        { onNavigateToPage(MainScenePage.Search) },
                        Modifier
                            .desktopTitleBarPadding()
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.windowHeightSizeClass.isAtLeastMedium) {
                                // 移动端横屏不增加额外 padding
                                padding(vertical = 48.dp)
                            },
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        Icon(Icons.Rounded.Search, "搜索")
                    }
                },
                navigationRailFooter = {
                    NavigationRailItem(
                        modifier = Modifier.padding(bottom = itemSpacing)
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.windowHeightSizeClass.isAtLeastMedium) {
                                // 移动端横屏不增加额外 padding
                                padding(vertical = 16.dp)
                            },
                        selected = false,
                        onClick = onNavigateToSettings,
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        enabled = true,
                        label = { Text("设置") },
                        alwaysShowLabel = true,
                        colors = itemColors,
                    )
                },
                navigationRailItemSpacing = 8.dp,
            ) {
                for (entry in MainScenePage.visibleEntries) {
                    item(
                        page == entry,
                        onClick = { onNavigateToPage(entry) },
                        icon = { Icon(entry.getIcon(), null) },
                        label = { Text(text = entry.getText()) },
                    )
                }
            }
        },
        modifier,
        layoutType = navigationLayoutType,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val navigator by rememberUpdatedState(LocalNavigator.current)
        AnimatedContent(
            page,
            Modifier.fillMaxSize(),
            transitionSpec = {
//                val easing = CubicBezierEasing(0f, 0f, 1f, 1f)
//                val fadeIn = fadeIn(tween(25, easing = easing))
//                val fadeOut = fadeOut(tween(25, easing = easing))
//                fadeIn togetherWith fadeOut
                fadeIn(snap()) togetherWith fadeOut(snap())
            },
        ) { page ->
            TabContent(
                layoutType = navigationLayoutType,
                Modifier.ifThen(navigationLayoutType != NavigationSuiteType.NavigationBar) {
                    // macos 标题栏只会在 NavigationRail 的区域内, TabContent 区域无需这些 padding.
                    consumeWindowInsets(WindowInsets.desktopTitleBar())
                },
            ) {
                when (page) {
                    MainScenePage.Exploration -> {
                        ExplorationPage(
                            viewModel { ExplorationPageViewModel() }.explorationPageState,
                            onSearch = { onNavigateToPage(MainScenePage.Search) },
                            onClickSettings = { navigator.navigateSettings() },
                            modifier.fillMaxSize(),
                            actions = {
                                TextButtonUpdateLogo()
                            },
                        )
                    }

                    MainScenePage.Collection -> {
                        val vm = viewModel<UserCollectionsViewModel> { UserCollectionsViewModel() }
                        CollectionPage(
                            state = vm.state,
                            items = vm.items,
                            onClickSearch = { onNavigateToPage(MainScenePage.Search) },
                            onClickSettings = { navigator.navigateSettings() },
                            Modifier.fillMaxSize(),
                            enableAnimation = vm.myCollectionsSettings.enableListAnimation1,
                            lazyGridState = vm.lazyGridState,
                            actions = {
                                TextButtonUpdateLogo()
                            },
                        )
                    }

                    MainScenePage.CacheManagement -> CacheManagementPage(
                        viewModel { CacheManagementViewModel(navigator) },
                        navigationIcon = { },
                        Modifier.fillMaxSize(),
                    )

                    MainScenePage.Search -> {
                        val vm = viewModel { SearchViewModel() }
                        val onBack = {
                            onNavigateToPage(MainScenePage.Exploration)
                        }
                        BackHandler(true, onBack)
                        val listDetailNavigator = rememberListDetailPaneScaffoldNavigator()
                        SearchPage(
                            vm.searchPageState,
                            detailContent = {
                                val result by vm.subjectDetailsStateLoader.result
                                SubjectDetailsPage(
                                    result,
                                    onPlay = { episodeId ->
                                        val curr = result
                                        if (curr is SubjectDetailsStateLoader.LoadState.Ok) {
                                            navigator.navigateEpisodeDetails(curr.value.subjectId, episodeId)
                                        }
                                    },
                                    onLoadErrorRetry = { vm.reloadCurrentSubjectDetails() },
                                    navigationIcon = {
                                        // 只有在单面板模式下才显示返回按钮
                                        if (listDetailLayoutParameters.isSinglePane) {
                                            BackNavigationIconButton(
                                                onNavigateBack = {
                                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        listDetailNavigator.navigateBack()
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                            Modifier.fillMaxSize(),
                            onSelect = { index, item ->
                                vm.searchPageState.selectedItemIndex = index
                                vm.viewSubjectDetails(item)
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                }
                            },
                            navigator = listDetailNavigator,
                            contentWindowInsets = WindowInsets.safeDrawing, // 不包含 macos 标题栏, 因为左侧有 navigation rail
                            navigationIcon = {
                                BackNavigationIconButton(onBack)
                            },
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TabContent(
    layoutType: NavigationSuiteType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = when (layoutType) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.None -> RectangleShape

        NavigationSuiteType.NavigationRail,
        NavigationSuiteType.NavigationDrawer -> MaterialTheme.shapes.extraLarge.copy(
            topEnd = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )

        else -> RectangleShape
    }
    Surface(
        modifier.clip(shape),
        shape = shape,
        color = AniThemeDefaults.pageContentBackgroundColor,
    ) {
        content()
    }
}

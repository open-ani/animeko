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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuite
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteLayout
import me.him188.ani.app.ui.cache.CacheManagementScreen
import me.him188.ani.app.ui.cache.CacheManagementViewModel
import me.him188.ani.app.ui.exploration.ExplorationScreen
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopCaptionButton
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isTopRight
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.settings.account.ProfilePopup
import me.him188.ani.app.ui.settings.account.ProfileViewModel
import me.him188.ani.app.ui.subject.collection.CollectionPage
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.app.ui.update.UpdateNotifier
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.utils.platform.isAndroid


@Composable
fun MainScreen(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    modifier: Modifier = Modifier,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
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

    MainScreenContent(
        page,
        selfInfo,
        onNavigateToPage,
        onNavigateToSettings,
        onNavigateToSearch,
        modifier,
        navigationLayoutType,
    )
}

@Composable
private fun MainScreenContent(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    navigationLayoutType: NavigationSuiteType = AniNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    val explorationPageViewModel = viewModel { ExplorationPageViewModel() }
    val userCollectionsViewModel = viewModel<UserCollectionsViewModel> { UserCollectionsViewModel() }
    val cacheManagementViewModel = viewModel { CacheManagementViewModel() }
    val scope = rememberCoroutineScope()

    var showAccountSettingsPopup: Boolean by remember { mutableStateOf(false) }
    val profileViewModel = viewModel { ProfileViewModel() }

    val navigatorState = rememberUpdatedState(LocalNavigator.current)
    val navigator by navigatorState

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
                        onNavigateToSearch,
                        Modifier
                            .desktopTitleBarPadding()
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium) {
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
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium) {
                                // 移动端横屏不增加额外 padding
                                padding(vertical = 16.dp)
                            },
                        selected = false,
                        onClick = { onNavigateToSettings(null) },
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        enabled = true,
                        label = { Text("设置") },
                        alwaysShowLabel = true,
                        colors = itemColors,
                    )
                },
                navigationRailItemSpacing = 8.dp,
            ) {
                for (entry in MainScreenPage.visibleEntries) {
                    item(
                        page == entry,
                        onClick = { onNavigateToPage(entry) },
                        onDoubleClick = {
                            scope.launch {
                                when (entry) {
                                    MainScreenPage.Exploration ->
                                        explorationPageViewModel.explorationPageState.pageScrollState.animateScrollToItem(
                                            0,
                                        )

                                    MainScreenPage.Collection ->
                                        userCollectionsViewModel.state.scrollToTop()

                                    MainScreenPage.CacheManagement ->
                                        cacheManagementViewModel.lazyGridState.animateScrollToItem(0)
                                }
                            }
                        },
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
        // Windows caption button 在右侧, 没有足够空间放置按钮, 需要保留 title bar insets
        val isRightCaptionButton = WindowInsets.desktopCaptionButton.isTopRight()
        val toaster = LocalToaster.current

        TabContent(
            layoutType = navigationLayoutType,
            Modifier.ifThen(navigationLayoutType != NavigationSuiteType.NavigationBar && !isRightCaptionButton) {
                // macos 标题栏只会在 NavigationRail 的区域内, TabContent 区域无需这些 padding.
                consumeWindowInsets(WindowInsets.desktopTitleBar())
            },
        ) {
            val aniMotionScheme = LocalAniMotionScheme.current
            AnimatedContent(
                page,
                Modifier.fillMaxSize(),
                transitionSpec = {
                    aniMotionScheme.topLevelTransition
                },
            ) { page ->
                when (page) {
                    MainScreenPage.Exploration -> {
                        ExplorationScreen(
                            explorationPageViewModel.explorationPageState,
                            selfInfo,
                            onSearch = onNavigateToSearch,
                            onClickSettings = { navigator.navigateSettings() },
                            onClickLogin = { showAccountSettingsPopup = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MainScreenPage.Collection -> {
                        CollectionPage(
                            state = userCollectionsViewModel.state,
                            selfInfo = selfInfo,
                            fullSyncState = userCollectionsViewModel.fullSyncState.collectAsStateWithLifecycle().value,
                            onClickSearch = onNavigateToSearch,
                            onClickLogin = { showAccountSettingsPopup = true },
                            onClickSettings = { navigator.navigateSettings() },
                            onCollectionUpdate = { subjectId, episode ->
                                coroutineScope.launch {
                                    userCollectionsViewModel.toggleEpisodeCollection(
                                        subjectId,
                                        episode.episodeId,
                                        episode.collectionType,
                                    )?.let { toaster.showLoadError(it) }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            enableAnimation = userCollectionsViewModel.myCollectionsSettings.enableListAnimation1,
                        )
                    }

                    MainScreenPage.CacheManagement -> {
                        CacheManagementScreen(
                            cacheManagementViewModel,
                            onPlay = { navigator.navigateEpisodeDetails(it.subjectId, it.episodeId) },
                            Modifier.fillMaxSize(),
                            navigationIcon = { },
                        )
                    }
                }
            }
        }
    }

    if (showAccountSettingsPopup) {
        ProfilePopup(
            vm = profileViewModel,
            onDismissRequest = { showAccountSettingsPopup = false },
            onNavigateToSettings = {
                showAccountSettingsPopup = false
                onNavigateToSettings(null)
            },
            onNavigateToAccountSettings = {
                showAccountSettingsPopup = false
                onNavigateToSettings(SettingsTab.PROFILE)
            },
            onNavigateToLogin = {
                showAccountSettingsPopup = false
                navigator.navigateEmailLoginStart()
            },
        )
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
        Box(Modifier.fillMaxWidth()) {
            content()

            UpdateNotifier()
        }
    }
}

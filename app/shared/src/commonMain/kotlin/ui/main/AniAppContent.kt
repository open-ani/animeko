/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MAIN_REQUESTED_PAGE_KEY
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.OverrideNavigation
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.cache.CacheManagementScreen
import me.him188.ani.app.ui.cache.CacheManagementViewModel
import me.him188.ani.app.ui.cache.details.MediaCacheDetailsPageViewModel
import me.him188.ani.app.ui.cache.details.MediaCacheDetailsScreen
import me.him188.ani.app.ui.cache.details.MediaDetails
import me.him188.ani.app.ui.cache.details.MediaDetailsLazyGrid
import me.him188.ani.app.ui.cache.subject.SubjectCacheScreen
import me.him188.ani.app.ui.cache.subject.SubjectCacheViewModelImpl
import me.him188.ani.app.ui.exploration.schedule.ScheduleScreen
import me.him188.ani.app.ui.exploration.schedule.ScheduleViewModel
import me.him188.ani.app.ui.foundation.animation.NavigationMotionScheme
import me.him188.ani.app.ui.foundation.animation.ProvideAniMotionCompositionLocals
import androidx.compose.ui.graphics.Color
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.watchtogether.LocalWatchTogetherEntry
import me.him188.ani.app.ui.foundation.watchtogether.WatchTogetherEntryState
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.TopAppBarActionButton
import me.him188.ani.app.ui.login.EmailLoginStartScreen
import me.him188.ani.app.ui.login.EmailLoginVerifyScreen
import me.him188.ani.app.ui.login.EmailLoginViewModel
import me.him188.ani.app.ui.oauth.BangumiAuthorizeScreen
import me.him188.ani.app.ui.oauth.BangumiAuthorizeViewModel
import me.him188.ani.app.ui.onboarding.OnboardingCompleteScreen
import me.him188.ani.app.ui.onboarding.OnboardingCompleteViewModel
import me.him188.ani.app.ui.onboarding.OnboardingScreen
import me.him188.ani.app.ui.onboarding.OnboardingViewModel
import me.him188.ani.app.ui.onboarding.WelcomeScreen
import me.him188.ani.app.ui.playback.PlaybackHistoryScreen
import me.him188.ani.app.ui.playback.PlaybackHistorySyncStatusScreen
import me.him188.ani.app.ui.playback.PlaybackHistoryViewModel
import me.him188.ani.app.ui.profile.auth.AniContactList
import me.him188.ani.app.ui.search.SearchScreen
import me.him188.ani.app.ui.settings.SettingsScreen
import me.him188.ani.app.ui.settings.SettingsViewModel
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceScreen
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceViewModel
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourceScreen
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourceViewModel
import me.him188.ani.app.ui.settings.tabs.media.torrent.peer.PeerFilterSettingsScreen
import me.him188.ani.app.ui.settings.tabs.media.torrent.peer.PeerFilterSettingsViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsScreen
import me.him188.ani.app.ui.subject.person.CharacterDetailsScreen
import me.him188.ani.app.ui.subject.person.CharacterDetailsViewModel
import me.him188.ani.app.ui.subject.person.PersonDetailsScreen
import me.him188.ani.app.ui.subject.person.PersonDetailsViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.episode.EpisodeScreen
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.watchtogether.WatchTogetherOverlayHost
import me.him188.ani.app.ui.watchtogether.WatchTogetherViewModel
import me.him188.ani.datasources.api.source.FactoryId
import kotlin.reflect.typeOf

/**
 * UI 入口点. 包含所有子页面, 以及组合这些子页面的方式 (navigation).
 */
@Composable
fun AniAppContent(aniNavigator: AniNavigator) {
    val aniAppViewModel = viewModel<AniAppViewModel>()
    val appState = aniAppViewModel.appState.collectAsStateWithLifecycle(null).value ?: return

    val navigator = rememberNavController()
    aniNavigator.setNavController(navigator)

    // 根底色: 页面切换过渡的淡入淡出间隙会露出它, 见 AniUiBehavior.blackRootBackground
    val rootBackground =
        if (LocalAniUiBehavior.current.blackRootBackground) Color.Black
        else MaterialTheme.colorScheme.background
    // "一起看" 入口把手: 弹窗本体在下面的 WatchTogetherOverlayHost 里 (与 NavHost 同级),
    // 入口按钮在 NavHost 内的各页面上 (TV 侧边栏 / 播放器胶囊行), 两边隔着 NavHost 靠它通气
    val watchTogetherEntry = remember { WatchTogetherEntryState() }
    Box(Modifier.fillMaxSize().background(rootBackground)) {
        CompositionLocalProvider(
            LocalNavigator provides aniNavigator,
            LocalBrowserNavigator providesDefault aniAppViewModel.browserNavigator,
            LocalWatchTogetherEntry provides watchTogetherEntry,
        ) {
            ProvideAniMotionCompositionLocals {
                AniAppContentImpl(
                    aniNavigator,
                    appState.initialNavRoute, // 只有在 APP 首次启动的时候加载这个, 只加载一次
                    appState.mainSceneInitialPage,
                    Modifier.fillMaxSize(),
                )
            }
            BangumiSessionExpiredPromptHost(
                viewModel = aniAppViewModel,
                enabled = appState.initialNavRoute is NavRoutes.Main,
                onLogin = {
                    aniNavigator.navigateBangumiAuthorize()
                },
            )
            WatchTogetherOverlayHost(
                viewModel = viewModel { WatchTogetherViewModel() },
                aniNavigator = aniNavigator,
            )
        }
    }
}

@Composable
private fun AniAppContentImpl(
    aniNavigator: AniNavigator,
    initialRoute: NavRoutes,
    mainSceneInitialPage: MainScreenPage,
    modifier: Modifier = Modifier,
) {
    val navController by aniNavigator.collectNavigatorAsState()
    // 必须传给所有 Scaffold 和 TopAppBar. 注意, 如果你不传, 你的 UI 很可能会在 macOS 不工作.
    val windowInsetsWithoutTitleBar = ScaffoldDefaults.contentWindowInsets
    val windowInsets = ScaffoldDefaults.contentWindowInsets
        .add(WindowInsets.desktopTitleBar()) // Compose 目前不支持这个所以我们要自己加上
    val navMotionScheme by rememberUpdatedState(NavigationMotionScheme.current)
    val emailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() }

    val navHostModifier = modifier.ifThen(LocalAniUiBehavior.current.focusDrivenNavigation) {
        // 焦点导航的通用兜底 (无需任何页面单独配合): 没有任何焦点时 Compose 不会自动分配,
        // 方向键会完全失效 (按键只会派发到根部的 onKeyEvent). 这里常驻监视 —— 只要本窗口
        // 持有窗口焦点而 NavHost 内没有任何焦点 (刚导航到的页面只有加载动画、聚焦元素被
        // 数据刷新移除、内容迟到等), 就持续把焦点送入当前页面 (requestFocus 挂在 focusGroup
        // 上会进入默认可聚焦子元素), 直到成功为止. 页面自己的焦点锚点 (如详情页播放按钮,
        // 播放器画面) 优先: 已有焦点时这里不动作.
        // 弹窗/对话框 (独立窗口) 打开期间本窗口失去窗口焦点, 兜底自动暂停 ——
        // 不会与弹窗关闭后的焦点恢复逻辑竞争.
        val focusRequester = remember { FocusRequester() }
        var hasFocusInside by remember { mutableStateOf(false) }
        val windowInfo = LocalWindowInfo.current
        val currentEntry by navController.currentBackStackEntryAsState()
        LaunchedEffect(currentEntry) {
            if (currentEntry == null) return@LaunchedEffect
            snapshotFlow { hasFocusInside to windowInfo.isWindowFocused }
                .collectLatest { (focused, windowFocused) ->
                    if (focused || !windowFocused) return@collectLatest
                    // 持续重试 (状态一变 collectLatest 即取消): 转场动画期间请求可能落在
                    // 将被移除的旧页面上, 旧页面销毁后焦点再次丢失会自动再触发
                    while (true) {
                        runCatching { focusRequester.requestFocus() }
                        delay(100)
                    }
                }
        }
        onFocusChanged { hasFocusInside = it.hasFocus }
            .focusRequester(focusRequester)
            .focusGroup()
    }

    NavHost(navController, startDestination = initialRoute, navHostModifier) {
        val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
            { navMotionScheme.enterTransition }
        val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
            { navMotionScheme.exitTransition }
        val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
            { navMotionScheme.popEnterTransition }
        val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
            { navMotionScheme.popExitTransition }

        composable<NavRoutes.Welcome>(
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        ) {
            WelcomeScreen(
                onClickContinue = {
                    // 从 WelcomeScreen 进入 onboarding, 最后 navigateMain 要 popupTo Welcome
                    aniNavigator.navigateOnboarding(NavRoutes.Welcome)
                },
                contactActions = { AniContactList() },
                Modifier.fillMaxSize(),
                windowInsets,
            )
        }
        composable<NavRoutes.EmailLoginStart>(
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        ) {
            EmailLoginStartScreen(
                onOtpSent = {
                    aniNavigator.navigateEmailLoginVerify()
                },
                onBangumiLoginClick = {
                    aniNavigator.navigateBangumiAuthorize()
                },
                onNavigateSettings = {
                    aniNavigator.navigateSettings()
                },
                onNavigateBack = {
                    aniNavigator.popBackStack(NavRoutes.EmailLoginStart, true)
                },
                vm = emailLoginViewModel,
            )
        }
        composable<NavRoutes.EmailLoginVerify>(
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        ) {
            EmailLoginVerifyScreen(
                onSuccess = {
                    aniNavigator.popBackOrNavigateToMain(mainSceneInitialPage)
                },
                onBangumiLoginClick = {
                    aniNavigator.navigateBangumiAuthorize()
                },
                onNavigateSettings = {
                    aniNavigator.navigateSettings()
                },
                onNavigateBack = {
                    aniNavigator.popBackStack(NavRoutes.EmailLoginVerify, true)
                },
                vm = emailLoginViewModel,
            )
        }
            composable<NavRoutes.BangumiAuthorize>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) {
                val vm = viewModel<BangumiAuthorizeViewModel> { BangumiAuthorizeViewModel() }
                BangumiAuthorizeScreen(
                    vm,
                    onNavigateBack = {
                        aniNavigator.popBackStack(NavRoutes.BangumiAuthorize, true)
                    },
                    onNavigateSettings = {
                        aniNavigator.navigateSettings()
                    },
                    contactActions = {
                        AniContactList()
                    },
                    onAuthorizeSuccess = {
                        aniNavigator.popBackStack(NavRoutes.BangumiAuthorize, true)
                        aniNavigator.popBackStack(NavRoutes.EmailLoginVerify, true)
                        aniNavigator.popBackStack(NavRoutes.EmailLoginStart, true)
                    },
                )
            }
            composable<NavRoutes.Onboarding>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<NavRoutes?>() to NavRoutes.NavType,
                ),
            ) { backStackEntry ->
                OnboardingScreen(
                    viewModel { OnboardingViewModel() },
                    onFinishOnboarding = {
                        // 传递 popUpTarget 给 OnboardingComplete
                        val currentRoute = backStackEntry.toRoute<NavRoutes.Onboarding>()
                        aniNavigator.navigateOnboardingComplete(currentRoute.popUpTargetInclusive)
                    },
                    contactActions = { AniContactList() },
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                navController.popBackStack()
                            },
                        )
                    },
                    Modifier
                        .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                        .fillMaxHeight(),
                    windowInsets,
                )
            }
            composable<NavRoutes.OnboardingComplete>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<NavRoutes?>() to NavRoutes.NavType,
                ),
            ) { backStackEntry ->
                OnboardingCompleteScreen(
                    viewModel { OnboardingCompleteViewModel() },
                    onClickContinue = {
                        // 传递 popUpTarget 给 OnboardingComplete
                        val currentRoute = backStackEntry.toRoute<NavRoutes.OnboardingComplete>()
                        aniNavigator.navigateMain(
                            page = mainSceneInitialPage,
                            popUpTargetInclusive = currentRoute.popUpTargetInclusive,
                        )
                    },
                    backNavigation = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack()
                            },
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets,
                )
            }
            composable<NavRoutes.Main>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<MainScreenPage>() to MainScreenPage.NavType,
                ),
            ) { backStack ->
                val route = backStack.toRoute<NavRoutes.Main>()
                val navigationLayoutType =
                    AniNavigationSuiteDefaults.calculateLayoutType(
                        currentWindowAdaptiveInfo1(),
                    )

                val vm = viewModel { MainScreenSharedViewModel() }
                var currentPage by rememberSaveable { mutableStateOf(route.initialPage) }

                // 从其他页面 (如详情页侧边栏) 弹回主页时切到指定 tab: 弹回不会重建 Main,
                // route.initialPage 不会重新生效, 故经 SavedStateHandle 传递 (见 requestMainPage).
                val requestedPage by backStack.savedStateHandle
                    .getStateFlow<String?>(MAIN_REQUESTED_PAGE_KEY, null)
                    .collectAsState()
                LaunchedEffect(requestedPage) {
                    val name = requestedPage ?: return@LaunchedEffect
                    runCatching { MainScreenPage.valueOf(name) }.getOrNull()?.let { currentPage = it }
                    backStack.savedStateHandle[MAIN_REQUESTED_PAGE_KEY] = null
                }

                OverrideNavigation(
                    {
                        object : AniNavigator by it {
                            override fun navigateMain(page: MainScreenPage, popUpTargetInclusive: NavRoutes?) {
                                currentPage = page
                            }
                        }
                    },
                ) {
                    /*CompositionLocalProvider(
                        LocalSharedTransitionScopeProvider provides SharedTransitionScopeProvider(
                            this@SharedTransitionLayout, this,
                        ),
                    ) {*/
                    val selfInfo by vm.selfInfo.collectAsState() // not -WithLifecycle
                    MainScreen(
                        page = currentPage,
                        selfInfo = selfInfo,
                        onNavigateToPage = { currentPage = it },
                        onNavigateToSettings = { aniNavigator.navigateSettings(it) },
                        onNavigateToSearch = { aniNavigator.navigateSubjectSearch() },
                        navigationLayoutType = navigationLayoutType,
                    )
                    // }
                }
            }
            composable<NavRoutes.SubjectSearch>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) {
                val route = it.toRoute<NavRoutes.SubjectSearch>()
                val navigator = LocalNavigator.current
                val vm = viewModel(key = route.toString()) { SearchViewModel(route.toQuery()) }

                SearchScreen(
                    vm,
                    onNavigateBack = {
                        aniNavigator.popBackStack()
                    },
                    onNavigateToSubjectDetails = { subjectId, placeholder ->
                        navigator.navigateSubjectDetails(subjectId, placeholder)
                    },
                    onNavigateToEpisodeDetails = { subjectId, episodeId ->
                        navigator.navigateEpisodeDetails(subjectId, episodeId)
                    },
                    windowInsets = windowInsets,
                )
            }
            composable<NavRoutes.SubjectDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<SubjectDetailPlaceholder?>() to SubjectDetailPlaceholder.NavType,
                ),
            ) { backStackEntry ->
                val details = backStackEntry.toRoute<NavRoutes.SubjectDetail>()
                val vm = viewModel<SubjectDetailsViewModel>(key = details.subjectId.toString()) {
                    val placeholder = details.placeholder?.run {
                        SubjectInfo.createPlaceholder(id, name, coverUrl, nameCN)
                    }
                    SubjectDetailsViewModel(details.subjectId, placeholder)
                }
                /*CompositionLocalProvider(
                    LocalSharedTransitionScopeProvider provides SharedTransitionScopeProvider(
                        this@SharedTransitionLayout, this,
                    ),
                ) {*/
                SubjectDetailsScreen(
                    vm,
                    onPlay = { aniNavigator.navigateEpisodeDetails(details.subjectId, it) },
                    onLoadErrorRetry = { vm.reload() },
                    onClickTag = { aniNavigator.navigateSubjectSearch(NavRoutes.SubjectSearch(tags = listOf(it.name))) },
                    windowInsets = windowInsets,
                    navigationIcon = {
                        // 有硬件返回键的设备上不显示返回/主页按钮: 连按返回即可回到主页
                        if (LocalAniUiBehavior.current.showBackNavigationButton) {
                            Row {
                                BackNavigationIconButton(
                                    {
                                        aniNavigator.popBackStack(details, inclusive = true)
                                    },
                                )
                                TopAppBarActionButton(
                                    {
                                        aniNavigator.popBackOrNavigateToMain(mainSceneInitialPage)
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Home,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    },
                )
                // }
            }
            composable<NavRoutes.EpisodeDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.EpisodeDetail>()
                val context = LocalContext.current
                val vm = viewModel<EpisodeViewModel>(
                    key = route.toString(),
                ) {
                    EpisodeViewModel(
                        subjectId = route.subjectId,
                        initialEpisodeId = route.episodeId,
                        initialIsFullscreen = false,
                        context,
                    )
                }
                EpisodeScreen(vm, Modifier.fillMaxSize(), windowInsets)
            }
            composable<NavRoutes.Settings>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<SettingsTab?>() to SettingsTab.NavType,
                ),
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.Settings>()
                SettingsScreen(
                    viewModel {
                        SettingsViewModel()
                    },
                    onNavigateToEmailLogin = { aniNavigator.navigateEmailLoginStart() },
                    onNavigateToBangumiOAuth = { aniNavigator.navigateBangumiAuthorize() },
                    Modifier.fillMaxSize(),
                    route.tab,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            composable<NavRoutes.PlaybackHistory>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.PlaybackHistory>()
                PlaybackHistoryScreen(
                    vm = viewModel { PlaybackHistoryViewModel() },
                    onNavigateBack = { aniNavigator.popBackStack(route, inclusive = true) },
                    onOpenHistory = { history ->
                        val subjectId = history.subjectId
                        if (subjectId != null) {
                            aniNavigator.navigateEpisodeDetails(subjectId, history.episodeId)
                        }
                    },
                    onOpenSyncStatus = {
                        aniNavigator.navigatePlaybackHistorySyncStatus()
                    },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    windowInsets = windowInsetsWithoutTitleBar,
                )
            }
            composable<NavRoutes.PlaybackHistorySyncStatus>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.PlaybackHistorySyncStatus>()
                PlaybackHistorySyncStatusScreen(
                    vm = viewModel { PlaybackHistoryViewModel() },
                    onNavigateBack = { aniNavigator.popBackStack(route, inclusive = true) },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    windowInsets = windowInsetsWithoutTitleBar,
                )
            }
            composable<NavRoutes.Caches>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.Caches>()
                val selfInfo by remember { SelfInfoStateProducer() }.flow.collectAsState(null)
                CacheManagementScreen(
                    vm = viewModel { CacheManagementViewModel() },
                    selfInfo = selfInfo,
                    onPlay = {
                        aniNavigator.navigateEpisodeDetails(it.subjectId, it.episodeId)
                    },
                    onClickLogin = { },
                    onNavigateCacheDetail = { aniNavigator.navigateCacheDetails(it) },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            composable<NavRoutes.CacheDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.CacheDetail>()
                MediaCacheDetailsScreen(
                    viewModel(key = route.toString()) { MediaCacheDetailsPageViewModel(route.cacheId) },
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                )
            }
            composable<NavRoutes.PersonDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.PersonDetail>()
                val vm = viewModel<PersonDetailsViewModel>(key = "person-${route.personId}") {
                    PersonDetailsViewModel(route.personId)
                }
                PersonDetailsScreen(
                    vm,
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton({ aniNavigator.popBackStack(route, inclusive = true) })
                    },
                )
            }
            composable<NavRoutes.CharacterDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.CharacterDetail>()
                val vm = viewModel<CharacterDetailsViewModel>(key = "character-${route.characterId}") {
                    CharacterDetailsViewModel(route.characterId)
                }
                CharacterDetailsScreen(
                    vm,
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton({ aniNavigator.popBackStack(route, inclusive = true) })
                    },
                )
            }
            composable<NavRoutes.SubjectCaches>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.SubjectCaches>()
                // viewModel (而非 remember): 从更深页面 (管理全部缓存) 返回时本页整个重新
                // 组合, remember 会重建 VM —— TV 的播放器暂停帧背景是一次性消费的,
                // 重建后就丢了 (页面退回浅色白底). VM 存活于返回栈, 随路由退出销毁.
                val vm = viewModel(key = "SubjectCaches-${route.subjectId}") {
                    SubjectCacheViewModelImpl(route.subjectId)
                }
                SubjectCacheScreen(
                    vm, Modifier.fillMaxSize(), windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            composable<NavRoutes.EditMediaSource>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.EditMediaSource>()
                val factoryId = FactoryId(route.factoryId)
                val mediaSourceInstanceId = route.mediaSourceInstanceId
                when (factoryId) {
                    RssMediaSource.FactoryId -> EditRssMediaSourceScreen(
                        viewModel<EditRssMediaSourceViewModel>(key = mediaSourceInstanceId) {
                            EditRssMediaSourceViewModel(mediaSourceInstanceId)
                        },
                        mediaDetailsColumn = { media ->
                            MediaDetailsLazyGrid(
                                MediaDetails.from(media, null, null),
                                Modifier.fillMaxSize(),
                                showSourceInfo = false,
                            )
                        },
                        Modifier,
                        windowInsets,
                        navigationIcon = {
                            BackNavigationIconButton(
                                {
                                    aniNavigator.popBackStack(route, inclusive = true)
                                },
                            )
                        },
                    )

                    SelectorMediaSource.FactoryId -> {
                        val context = LocalContext.current
                        EditSelectorMediaSourceScreen(
                            viewModel<EditSelectorMediaSourceViewModel>(key = mediaSourceInstanceId) {
                                EditSelectorMediaSourceViewModel(mediaSourceInstanceId, context)
                            },
                            Modifier,
                            windowInsets = windowInsets,
                            navigationIcon = {
                                BackNavigationIconButton(
                                    {
                                        aniNavigator.popBackStack(route, inclusive = true)
                                    },
                                )
                            },
                        )
                    }

                    else -> error("Unknown factoryId: $factoryId")
                }
            }
            composable<NavRoutes.TorrentPeerSettings>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.TorrentPeerSettings>()
                val viewModel = viewModel { PeerFilterSettingsViewModel() }
                PeerFilterSettingsScreen(
                    viewModel.state,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            composable<NavRoutes.Schedule>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.Schedule>()

                val vm = viewModel { ScheduleViewModel() }
                val presentation by vm.presentationFlow.collectAsStateWithLifecycle()
                ScheduleScreen(
                    presentation,
                    onRetry = { vm.refresh() },
                    onClickItem = {
                        aniNavigator.navigateSubjectDetails(
                            it.subjectId,
                            placeholder = SubjectDetailPlaceholder(
                                id = it.subjectId,
                                nameCN = it.subjectTitle,
                                coverUrl = it.imageUrl,
                            ),
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    state = vm.pageState,
                )
            }
        }

        LaunchedEffect(true, navController) {
            navController.currentBackStack.collect { list ->
                if (list.isEmpty()) { // workaround for 快速点击左上角返回键会白屏.
                    navController.navigate(initialRoute)
                }
            }
    }
}

private fun NavRoutes.SubjectSearch.toQuery(): SubjectSearchQuery {
    return SubjectSearchQuery(
        keywords = keyword ?: "",
        tags = tags,
    )
}

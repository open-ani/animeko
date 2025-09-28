/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
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
import me.him188.ani.app.ui.exploration.schedule.ScheduleScreen
import me.him188.ani.app.ui.exploration.schedule.ScheduleViewModel
import me.him188.ani.app.ui.foundation.animation.NavigationMotionScheme
import me.him188.ani.app.ui.foundation.animation.ProvideAniMotionCompositionLocals
import me.him188.ani.app.ui.foundation.layout.LocalSharedTransitionScopeProvider
import me.him188.ani.app.ui.foundation.layout.SharedTransitionScopeProvider
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
import me.him188.ani.app.ui.subject.cache.SubjectCacheScreen
import me.him188.ani.app.ui.subject.cache.SubjectCacheViewModelImpl
import me.him188.ani.app.ui.subject.details.SubjectDetailsScreen
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.episode.EpisodeScreen
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(
            LocalNavigator provides aniNavigator,
            LocalBrowserNavigator providesDefault aniAppViewModel.browserNavigator,
        ) {
            ProvideAniMotionCompositionLocals {
                AniAppContentImpl(
                    aniNavigator,
                    appState.initialNavRoute, // 只有在 APP 首次启动的时候加载这个, 只加载一次
                    appState.mainSceneInitialPage,
                    Modifier.fillMaxSize(),
                )
            }
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

    SharedTransitionLayout {
        NavHost(navController, startDestination = initialRoute, modifier) {
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

                OverrideNavigation(
                    {
                        object : AniNavigator by it {
                            override fun navigateMain(page: MainScreenPage, popUpTargetInclusive: NavRoutes?) {
                                currentPage = page
                            }
                        }
                    },
                ) {
                    CompositionLocalProvider(
                        LocalSharedTransitionScopeProvider provides SharedTransitionScopeProvider(
                            this@SharedTransitionLayout, this,
                        ),
                    ) {
                        val selfInfo by vm.selfInfo.collectAsState() // not -WithLifecycle
                        MainScreen(
                            page = currentPage,
                            selfInfo = selfInfo,
                            onNavigateToPage = { currentPage = it },
                            onNavigateToSettings = { aniNavigator.navigateSettings(it) },
                            onNavigateToSearch = { aniNavigator.navigateSubjectSearch() },
                            navigationLayoutType = navigationLayoutType,
                        )
                    }
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
                CompositionLocalProvider(
                    LocalSharedTransitionScopeProvider provides SharedTransitionScopeProvider(
                        this@SharedTransitionLayout, this,
                    ),
                ) {
                    SubjectDetailsScreen(
                        vm,
                        onPlay = { aniNavigator.navigateEpisodeDetails(details.subjectId, it) },
                        onLoadErrorRetry = { vm.reload() },
                        onClickTag = { aniNavigator.navigateSubjectSearch(NavRoutes.SubjectSearch(tags = listOf(it.name))) },
                        windowInsets = windowInsets,
                        navigationIcon = {
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
                        },
                    )
                }
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
            composable<NavRoutes.Caches>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.Caches>()
                CacheManagementScreen(
                    vm = viewModel { CacheManagementViewModel() },
                    onPlay = {
                        aniNavigator.navigateEpisodeDetails(it.subjectId, it.episodeId)
                    },
                    Modifier.fillMaxSize(),
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
            composable<NavRoutes.SubjectCaches>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.SubjectCaches>()
                // Don't use rememberViewModel to save memory
                val vm = remember(route.subjectId) { SubjectCacheViewModelImpl(route.subjectId) }
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
}

private fun NavRoutes.SubjectSearch.toQuery(): SubjectSearchQuery {
    return SubjectSearchQuery(
        keywords = keyword ?: "",
        tags = tags,
    )
}

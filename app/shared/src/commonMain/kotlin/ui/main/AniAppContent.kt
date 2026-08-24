/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.navigation.rememberAniBackStack
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.shared.Res
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
import me.him188.ani.app.ui.foundation.animation.LocalSharedTransitionScope
import me.him188.ani.app.ui.foundation.animation.NavigationMotionScheme
import me.him188.ani.app.ui.foundation.animation.ProvideAniMotionCompositionLocals
import me.him188.ani.app.ui.foundation.animation.subjectContainerTransform
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
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.episode.EpisodeScreen
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.person.CharacterDetailsScreen
import me.him188.ani.app.ui.subject.person.CharacterDetailsViewModel
import me.him188.ani.app.ui.subject.person.PersonDetailsScreen
import me.him188.ani.app.ui.subject.person.PersonDetailsViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.watchtogether.LocalWatchTogetherPlayerController
import me.him188.ani.app.ui.watchtogether.WatchTogetherOverlayHost
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlayerController
import me.him188.ani.app.ui.watchtogether.WatchTogetherViewModel
import me.him188.ani.datasources.api.source.FactoryId

/**
 * UI 入口点. 包含所有子页面, 以及组合这些子页面的方式 (navigation).
 */
@Composable
fun AniAppContent(aniNavigator: AniNavigator) {
    val aniAppViewModel = viewModel<AniAppViewModel>()
    val appState = aniAppViewModel.appState.collectAsStateWithLifecycle(null).value ?: return
    val watchTogetherViewModel = viewModel { WatchTogetherViewModel() }
    val watchTogetherPlayerController = remember(watchTogetherViewModel) {
        WatchTogetherPlayerController(watchTogetherViewModel::onPlayerEntryClick)
    }

    // 只有在 APP 首次启动的时候使用 initialNavRoute, 之后 back stack 自己维护并跨进程恢复
    val backStack = rememberAniBackStack(appState.initialNavRoute)
    aniNavigator.setBackStack(backStack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(
            LocalNavigator provides aniNavigator,
            LocalBrowserNavigator providesDefault aniAppViewModel.browserNavigator,
            LocalWatchTogetherPlayerController provides watchTogetherPlayerController,
        ) {
            ProvideAniMotionCompositionLocals {
                AniAppContentImpl(
                    aniNavigator,
                    backStack,
                    appState.mainSceneInitialPage,
                    Modifier.fillMaxSize(),
                )
                BangumiSessionExpiredPromptHost(
                    viewModel = aniAppViewModel,
                    enabled = appState.initialNavRoute is NavRoutes.Main,
                    onLogin = {
                        aniNavigator.navigateBangumiAuthorize()
                    },
                )
                WatchTogetherOverlayHost(
                    viewModel = watchTogetherViewModel,
                    aniNavigator = aniNavigator,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AniAppContentImpl(
    aniNavigator: AniNavigator,
    backStack: List<NavRoutes>,
    mainSceneInitialPage: MainScreenPage,
    modifier: Modifier = Modifier,
) {
    // 必须传给所有 Scaffold 和 TopAppBar. 注意, 如果你不传, 你的 UI 很可能会在 macOS 不工作.
    val windowInsetsWithoutTitleBar = ScaffoldDefaults.contentWindowInsets
    val windowInsets = ScaffoldDefaults.contentWindowInsets
        .add(WindowInsets.desktopTitleBar()) // Compose 目前不支持这个所以我们要自己加上
    val navMotionScheme by rememberUpdatedState(NavigationMotionScheme.current)
    val emailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() }

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            AniAppContentImplNavDisplay(
                aniNavigator = aniNavigator,
                backStack = backStack,
                mainSceneInitialPage = mainSceneInitialPage,
                navMotionScheme = navMotionScheme,
                emailLoginViewModel = emailLoginViewModel,
                windowInsets = windowInsets,
                windowInsetsWithoutTitleBar = windowInsetsWithoutTitleBar,
            )
        }
    }
}

@Composable
private fun AniAppContentImplNavDisplay(
    aniNavigator: AniNavigator,
    backStack: List<NavRoutes>,
    mainSceneInitialPage: MainScreenPage,
    navMotionScheme: NavigationMotionScheme,
    emailLoginViewModel: EmailLoginViewModel,
    windowInsets: WindowInsets,
    windowInsetsWithoutTitleBar: WindowInsets,
) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { aniNavigator.popBackStack() },
        entryDecorators = listOf(
            // 让每个页面各自持有 rememberSaveable 状态和 ViewModel, 出栈时一并销毁
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            navMotionScheme.enterTransition togetherWith navMotionScheme.exitTransition
        },
        popTransitionSpec = {
            navMotionScheme.popEnterTransition togetherWith navMotionScheme.popExitTransition
        },
        predictivePopTransitionSpec = {
            navMotionScheme.predictivePopEnterTransition togetherWith navMotionScheme.predictivePopExitTransition
        },
        entryProvider = entryProvider {
            entry<NavRoutes.Welcome> {
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
            entry<NavRoutes.EmailLoginStart> {
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
            entry<NavRoutes.EmailLoginVerify> {
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
            entry<NavRoutes.BangumiAuthorize> {
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
            entry<NavRoutes.Onboarding> { route ->
                OnboardingScreen(
                    viewModel { OnboardingViewModel() },
                    onFinishOnboarding = {
                        // 传递 popUpTarget 给 OnboardingComplete
                        aniNavigator.navigateOnboardingComplete(route.popUpTargetInclusive)
                    },
                    contactActions = { AniContactList() },
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                aniNavigator.popBackStack()
                            },
                        )
                    },
                    Modifier
                        .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                        .fillMaxHeight(),
                    windowInsets,
                )
            }
            entry<NavRoutes.OnboardingComplete> { route ->
                OnboardingCompleteScreen(
                    viewModel { OnboardingCompleteViewModel() },
                    onClickContinue = {
                        // 传递 popUpTarget 给 OnboardingComplete
                        aniNavigator.navigateMain(
                            page = mainSceneInitialPage,
                            popUpTargetInclusive = route.popUpTargetInclusive,
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
            entry<NavRoutes.Main> { route ->
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
            entry<NavRoutes.SubjectSearch> { route ->
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
            entry<NavRoutes.SubjectDetail>(
                // 条目详情页和列表卡片之间是 container transform, 页面级动画不能再叠加 predictive back
                // 的全屏缩放, 否则会双重缩放. 见 SharedTransitionNavTransition.
                metadata = NavDisplay.transitionSpec {
                    navMotionScheme.predictiveSharedContainer.enterTransition togetherWith
                            navMotionScheme.predictiveSharedContainer.exitTransition
                } +
                        NavDisplay.popTransitionSpec {
                            navMotionScheme.predictiveSharedContainer.popEnterTransition togetherWith
                                    navMotionScheme.predictiveSharedContainer.popExitTransition
                        } +
                        NavDisplay.predictivePopTransitionSpec {
                            navMotionScheme.predictiveSharedContainer.popEnterTransition togetherWith
                                    navMotionScheme.predictiveSharedContainer.popExitTransition
                        },
            ) { route ->
                val vm = viewModel<SubjectDetailsViewModel>(key = route.subjectId.toString()) {
                    val placeholder = route.placeholder?.run {
                        SubjectInfo.createPlaceholder(id, name, coverUrl, nameCN)
                    }
                    SubjectDetailsViewModel(route.subjectId, placeholder)
                }
                SubjectDetailsScreen(
                    vm,
                    onPlay = { aniNavigator.navigateEpisodeDetails(route.subjectId, it) },
                    onLoadErrorRetry = { vm.reload() },
                    onClickTag = {
                        aniNavigator.navigateSubjectSearch(NavRoutes.SubjectSearch(tags = listOf(it.name)))
                    },
                    modifier = Modifier.ifThen(route.imageSharedElementKey != null) {
                        subjectContainerTransform(
                            checkNotNull(route.imageSharedElementKey) { "route.imageSharedElementKey was null." },
                        )
                    },
                    windowInsets = windowInsets,
                    navigationIcon = {
                        Row {
                            BackNavigationIconButton(
                                {
                                    aniNavigator.popBackStack(route, inclusive = true)
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
            entry<NavRoutes.EpisodeDetail> { route ->
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
            entry<NavRoutes.Settings> { route ->
                SettingsScreen(
                    viewModel {
                        SettingsViewModel()
                    },
                    onNavigateToEmailLogin = { aniNavigator.navigateEmailLoginStart() },
                    onNavigateToBangumiOAuth = { aniNavigator.navigateBangumiAuthorize() },
                    loadOpenSourceLibrariesJsons = {
                        listOf(
                            Res.readBytes("files/aboutlibraries.json"),
                            Res.readBytes("files/additional_libraries.json"),
                        )
                    },
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
            entry<NavRoutes.PlaybackHistory> { route ->
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
            entry<NavRoutes.PlaybackHistorySyncStatus> { route ->
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
            entry<NavRoutes.Caches> { route ->
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
            entry<NavRoutes.CacheDetail> { route ->
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
            entry<NavRoutes.PersonDetail> { route ->
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
            entry<NavRoutes.CharacterDetail> { route ->
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
            entry<NavRoutes.SubjectCaches> { route ->
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
            entry<NavRoutes.EditMediaSource> { route ->
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
            entry<NavRoutes.TorrentPeerSettings> { route ->
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
            entry<NavRoutes.Schedule> { route ->
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
        },
    )
}

private fun NavRoutes.SubjectSearch.toQuery(): SubjectSearchQuery {
    return SubjectSearchQuery(
        keywords = keyword ?: "",
        tags = tags,
    )
}

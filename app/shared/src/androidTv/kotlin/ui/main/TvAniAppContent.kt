/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.PersonDetailRole
import me.him188.ani.app.navigation.findLast
import me.him188.ani.app.navigation.rememberAniBackStack
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.tv.ui.collection.TvCollectionRoute
import me.him188.ani.tv.ui.collection.TvCollectionViewModel
import me.him188.ani.tv.ui.di.TvAppDependencies
import me.him188.ani.tv.ui.episode.TvEpisodeRoute
import me.him188.ani.tv.ui.episode.TvEpisodeViewModel
import me.him188.ani.tv.ui.exploration.TvExplorationRoute
import me.him188.ani.tv.ui.exploration.TvExplorationViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.foundation.tvViewModel
import me.him188.ani.tv.ui.login.TvLoginRoute
import me.him188.ani.tv.ui.login.TvLoginViewModel
import me.him188.ani.tv.ui.schedule.TvScheduleRoute
import me.him188.ani.tv.ui.schedule.TvScheduleViewModel
import me.him188.ani.tv.ui.search.TvSearchRoute
import me.him188.ani.tv.ui.search.TvSearchViewModel
import me.him188.ani.tv.ui.settings.TvSettingsRoute
import me.him188.ani.tv.ui.settings.TvSettingsViewModel
import me.him188.ani.app.shared.loadOpenSourceLibrariesJsons
import me.him188.ani.tv.ui.subject.TvSubjectDetailsRoute
import me.him188.ani.tv.ui.subject.TvSubjectDetailsViewModel
import me.him188.ani.tv.ui.subject.person.TvPeopleDetailsRoute
import me.him188.ani.tv.ui.subject.person.TvPeopleDetailsViewModel
import me.him188.ani.tv.ui.subject.person.TvPeopleKind
import me.him188.ani.tv.ui.subject.person.TvPeopleTarget
import me.him188.ani.tv.ui.watchtogether.TvTogetherIntent
import me.him188.ani.tv.ui.watchtogether.TvWatchTogetherViewModel

/**
 * TV 端根内容: 注册 TV 支持的 [NavRoutes] 子集, Navigation 3
 * backStack 模型 (接线同手机 AniAppContent).
 *
 * 所有 TV ViewModel 只在这里通过 tvViewModel 显式构造, 生命周期归属所在导航条目.
 * 主壳内的功能页首次显示时才创建对应 ViewModel, Route 只接收实例并连接状态/Intent.
 *
 * `Caches`/`BangumiAuthorize` 等按 §1.2 裁剪永不注册.
 */
@Composable
fun TvAniAppContent(
    aniNavigator: AniNavigator,
    dependencies: TvAppDependencies,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberAniBackStack(NavRoutes.Main(MainScreenPage.Exploration))
    aniNavigator.setBackStack(backStack)
    // 主壳焦点记忆放 NavDisplay 之上: 进详情页返回时 Main 条目组合重建, 记忆须跨 route 存活
    // (身份键恢复流程见 TvFocusMemory)
    val shellFocusMemory = remember { TvFocusMemory() }
    val togetherViewModel = tvViewModel {
        TvWatchTogetherViewModel(
            dependencies.watchTogetherManager,
            dependencies.settingsRepository,
            dependencies.sessionStateProvider,
            dependencies.koin,
        )
    }
    LaunchedEffect(togetherViewModel, aniNavigator) {
        togetherViewModel.navigationEvents.collect { event ->
            if (event.replacePlayer) aniNavigator.findLast<NavRoutes.EpisodeDetail>()
                ?.let { aniNavigator.popBackStack(it, inclusive = true) }
            aniNavigator.navigateEpisodeDetails(event.subjectId, event.episodeId, force = true)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(togetherViewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) togetherViewModel.onIntent(TvTogetherIntent.Foreground(true))
            if (event == Lifecycle.Event.ON_STOP) togetherViewModel.onIntent(TvTogetherIntent.Foreground(false))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val onNavigate: (TvNavigationEvent) -> Unit = { event ->
        when (event) {
            is TvNavigationEvent.Subject -> aniNavigator.navigateSubjectDetails(event.subjectId, event.placeholder)
            is TvNavigationEvent.Episode -> aniNavigator.navigateEpisodeDetails(event.subjectId, event.episodeId)
            is TvNavigationEvent.Character -> aniNavigator.navigateCharacterDetails(event.characterId)
            is TvNavigationEvent.VoiceActor -> aniNavigator.navigate(
                NavRoutes.PersonDetail(
                    event.personId,
                    PersonDetailRole.VoiceActor,
                ),
            )

            is TvNavigationEvent.Staff -> aniNavigator.navigate(
                NavRoutes.PersonDetail(
                    event.personId,
                    PersonDetailRole.Staff,
                ),
            )

            TvNavigationEvent.LoggedIn -> Unit // handled by the Main entry
            TvNavigationEvent.Login -> aniNavigator.navigateBangumiAuthorize()
        }
    }
    CompositionLocalProvider(
        LocalNavigator provides aniNavigator,
        LocalTimeFormatter provides remember { TimeFormatter() },
    ) {
        // tv MaterialTheme 不绘制窗口背景, 根部铺一层 Surface (深色 surface + content color)
        Surface(modifier.fillMaxSize()) {
            val pages = entryProvider<NavRoutes> {
                entry<NavRoutes.Main> {
                    var shellContent by rememberSaveable { mutableStateOf(TvShellContent.Exploration) }
                    val mainViewModel = tvViewModel {
                        TvMainViewModel(dependencies.userRepository, dependencies.sessionStateProvider)
                    }
                    TvMainRoute(
                        mainViewModel,
                        content = shellContent,
                        onContentChange = { shellContent = it },
                        onOpenSettings = { aniNavigator.navigateSettings() },
                        focusMemory = shellFocusMemory,
                    ) { content, navigationRailInsets ->
                        when (content) {
                            TvShellContent.Exploration -> {
                                val viewModel = tvViewModel {
                                    TvExplorationViewModel(
                                        koin = dependencies.koin,
                                        collectionRepository = dependencies.subjectCollectionRepository,
                                    )
                                }
                                TvExplorationRoute(viewModel, onNavigate, navigationRailInsets = navigationRailInsets)
                            }

                            TvShellContent.Schedule -> {
                                val viewModel = tvViewModel { TvScheduleViewModel(dependencies.koin) }
                                TvScheduleRoute(viewModel, onNavigate, navigationRailInsets = navigationRailInsets)
                            }

                            TvShellContent.Collection -> {
                                val viewModel = tvViewModel { TvCollectionViewModel() }
                                TvCollectionRoute(viewModel, onNavigate, navigationRailInsets = navigationRailInsets)
                            }

                            TvShellContent.Search -> {
                                val viewModel =
                                    tvViewModel {
                                        TvSearchViewModel(dependencies.subjectSearchRepository, dependencies.settingsRepository)
                                    }
                                TvSearchRoute(viewModel, onNavigate, navigationRailInsets = navigationRailInsets)
                            }

                            TvShellContent.Login -> {
                                val viewModel = tvViewModel { TvLoginViewModel(dependencies.koin) }
                                TvLoginRoute(
                                    viewModel,
                                    navigationRailInsets = navigationRailInsets,
                                    onNavigate = { event ->
                                        when (event) {
                                            TvNavigationEvent.LoggedIn -> shellContent = TvShellContent.Exploration
                                            else -> onNavigate(event)
                                        }
                                    },
                                )
                            }

                        }
                    }
                }
                entry<NavRoutes.Settings> {
                    val viewModel = tvViewModel {
                        TvSettingsViewModel(
                            dependencies.settingsRepository,
                            dependencies.danmakuRegexFilterRepository,
                            dependencies.mediaSourceManager,
                            dependencies.mediaSourceSubscriptionRepository,
                            loadLibraries = ::loadOpenSourceLibrariesJsons,
                        )
                    }
                    TvSettingsRoute(viewModel)
                }


                entry<NavRoutes.OAuthAuthorize> {
                    val viewModel = tvViewModel { TvLoginViewModel(dependencies.koin) }
                    TvLoginRoute(
                        viewModel,
                        onNavigate = { event ->
                            if (event == TvNavigationEvent.LoggedIn) aniNavigator.popBackStack()
                            else onNavigate(event)
                        },
                    )
                }

                entry<NavRoutes.CharacterDetail> { route ->
                    val viewModel = tvViewModel {
                        TvPeopleDetailsViewModel(
                            TvPeopleTarget(route.characterId, TvPeopleKind.Character),
                            dependencies.personDetailsRepository, dependencies.personCommentRepository,
                            dependencies.commentReportService, dependencies.sessionStateProvider,
                        )
                    }
                    TvPeopleDetailsRoute(viewModel, onNavigate)
                }
                entry<NavRoutes.PersonDetail> { route ->
                    val viewModel = tvViewModel {
                        TvPeopleDetailsViewModel(
                            TvPeopleTarget(
                                route.personId,
                                when (route.role) {
                                    PersonDetailRole.VoiceActor -> TvPeopleKind.VoiceActor
                                    PersonDetailRole.Staff -> TvPeopleKind.Staff
                                },
                            ),
                            dependencies.personDetailsRepository, dependencies.personCommentRepository,
                            dependencies.commentReportService, dependencies.sessionStateProvider,
                        )
                    }
                    TvPeopleDetailsRoute(viewModel, onNavigate)
                }
                entry<NavRoutes.SubjectDetail> { route ->
                    val viewModel = tvViewModel(key = "subject-${route.subjectId}") {
                        TvSubjectDetailsViewModel(
                            subjectId = route.subjectId,
                            placeholder = route.placeholder?.run {
                                SubjectInfo.createPlaceholder(id, name, coverUrl, nameCN)
                            },
                            factory = dependencies.subjectDetailsStateFactory,
                            collectionRepository = dependencies.subjectCollectionRepository,
                            setEpisodeCollectionType = dependencies.setEpisodeCollectionType,
                            searchRepository = dependencies.subjectSearchRepository,
                            sessionStateProvider = dependencies.sessionStateProvider,
                            settingsRepository = dependencies.settingsRepository,
                        )
                    }
                    TvSubjectDetailsRoute(viewModel, onNavigate)
                }

                entry<NavRoutes.EpisodeDetail> { route ->
                    val context = LocalContext.current.applicationContext
                    val viewModel = tvViewModel(key = "episode-${route.subjectId}-${route.episodeId}") {
                        TvEpisodeViewModel(
                            subjectId = route.subjectId,
                            initialEpisodeId = route.episodeId,
                            context = context,
                            koin = dependencies.koin,
                            playerStateFactory = dependencies.playerStateFactory,
                            episodeCollectionRepository = dependencies.episodeCollectionRepository,
                            subjectCollectionRepository = dependencies.subjectCollectionRepository,
                            danmakuRepository = dependencies.danmakuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            getDanmakuRegexFilterListFlowUseCase = dependencies.getDanmakuRegexFilterListFlowUseCase,
                            episodeCommentRepository = dependencies.episodeCommentRepository,
                            getSubjectRecommendations = dependencies.getSubjectRecommendations,
                            autoSkipRepository = dependencies.autoSkipRepository,
                            selectorEpisodeCacheRepository = dependencies.selectorEpisodeCacheRepository,
                            webSessionManager = dependencies.webSessionManager,
                            playbackAutomationGate = dependencies.playbackAutomationGate,
                        )
                    }
                    TvEpisodeRoute(viewModel, togetherViewModel, onNavigate)
                }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { aniNavigator.popBackStack() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    rememberTvNavigationFocusDecorator(pages(backStack.last()).contentKey),
                ),
                entryProvider = pages,
            )
        }
    }
}

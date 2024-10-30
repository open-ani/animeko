/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScenePage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.OverrideNavigation
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.cache.CacheManagementPage
import me.him188.ani.app.ui.cache.CacheManagementViewModel
import me.him188.ani.app.ui.cache.details.MediaCacheDetailsPage
import me.him188.ani.app.ui.cache.details.MediaCacheDetailsPageViewModel
import me.him188.ani.app.ui.cache.details.MediaDetailsLazyGrid
import me.him188.ani.app.ui.foundation.animation.EmphasizedDecelerateEasing
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.profile.BangumiOAuthViewModel
import me.him188.ani.app.ui.profile.auth.BangumiOAuthScene
import me.him188.ani.app.ui.profile.auth.BangumiTokenAuthPage
import me.him188.ani.app.ui.profile.auth.BangumiTokenAuthViewModel
import me.him188.ani.app.ui.profile.auth.WelcomeScene
import me.him188.ani.app.ui.profile.auth.WelcomeViewModel
import me.him188.ani.app.ui.settings.SettingsPage
import me.him188.ani.app.ui.settings.SettingsViewModel
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourcePage
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceViewModel
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourcePage
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourceViewModel
import me.him188.ani.app.ui.settings.tabs.media.torrent.peer.PeerFilterSettingsPage
import me.him188.ani.app.ui.settings.tabs.media.torrent.peer.PeerFilterSettingsViewModel
import me.him188.ani.app.ui.subject.cache.SubjectCacheScene
import me.him188.ani.app.ui.subject.cache.SubjectCacheViewModelImpl
import me.him188.ani.app.ui.subject.details.SubjectDetailsScene
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.episode.EpisodeScene
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.datasources.api.source.FactoryId
import kotlin.math.roundToInt
import kotlin.reflect.typeOf

/**
 * UI 入口点. 包含所有子页面, 以及组合这些子页面的方式 (navigation).
 */
@Composable
fun AniAppContent(
    aniNavigator: AniNavigator,
    initialRoute: NavRoutes,
) {
    val navigator = rememberNavController()
    aniNavigator.setNavController(navigator)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalNavigator provides aniNavigator) {
            AniAppContentImpl(aniNavigator, initialRoute, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AniAppContentImpl(
    aniNavigator: AniNavigator,
    initialRoute: NavRoutes,
    modifier: Modifier = Modifier,
) {
    val navController = aniNavigator.navigator
    // 必须传给所有 Scaffold 和 TopAppBar. 注意, 如果你不传, 你的 UI 很可能会在 macOS 不工作.
    val windowInsetsWithoutTitleBar = ScaffoldDefaults.contentWindowInsets
    val windowInsets = ScaffoldDefaults.contentWindowInsets
        .add(WindowInsets.desktopTitleBar()) // Compose 目前不支持这个所以我们要自己加上

    SharedTransitionLayout {
        NavHost(navController, startDestination = initialRoute, modifier) {
            // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#e5b958f0-435d-4e84-aed4-8d1ea395fa5c
            val enterDuration = 500
            val exitDuration = 200

            // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#26a169fb-caf3-445e-8267-4f1254e3e8bb
            // TODO: We should actually use Container transform in CMP 1.7
            // https://developer.android.com/develop/ui/compose/animation/shared-elements
            val enterEasing = EmphasizedDecelerateEasing
            val exitEasing = LinearOutSlowInEasing

            val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
                {
                    slideInHorizontally(
                        tween(
                            enterDuration,
                            easing = enterEasing,
                        ),
                    ) { (it * (1f / 5)).roundToInt() }
                        .plus(fadeIn(tween(enterDuration, easing = enterEasing)))
                }

            val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
                {
                    fadeOut(tween(exitDuration, easing = exitEasing))
                }

            val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
                {
                    fadeIn(tween(enterDuration, easing = enterEasing))
                }

            // 从页面 A 回到上一个页面 B, 切走页面 A 的动画
            val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
                {
                    slideOutHorizontally(
                        tween(
                            exitDuration,
                            easing = exitEasing,
                        ),
                    ) { (it * (1f / 7)).roundToInt() }
                        .plus(fadeOut(tween(exitDuration, easing = exitEasing)))
                }

            composable<NavRoutes.Welcome>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { // 由 SessionManager.requireAuthorize 跳转到
                WelcomeScene(viewModel { WelcomeViewModel() }, Modifier.fillMaxSize(), windowInsets)
            }
            composable<NavRoutes.Main>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                typeMap = mapOf(
                    typeOf<MainScenePage>() to MainScenePage.NavType,
                ),
            ) { backStack ->
                val route = backStack.toRoute<NavRoutes.Main>()
                val navigationLayoutType =
                    AniNavigationSuiteDefaults.calculateLayoutType(
                        currentWindowAdaptiveInfo(),
                    )
                var currentPage by rememberSaveable { mutableStateOf(route.initialPage) }

                OverrideNavigation(
                    {
                        object : AniNavigator by it {
                            override fun navigateMain(page: MainScenePage, requestFocus: Boolean) {
                                currentPage = page
                            }
                        }
                    },
                ) {
                    MainScene(
                        page = currentPage,
                        windowInsets =
                        // macOS 上的手机状态需要有顶部的 insets
                        if (navigationLayoutType == NavigationSuiteType.NavigationBar) windowInsets
                        // 横屏状态不需要有
                        else windowInsetsWithoutTitleBar,
                        onNavigateToPage = { currentPage = it },
                        navigationLayoutType = navigationLayoutType,
                    )
                }
            }
            composable<NavRoutes.BangumiOAuth>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) {
                BangumiOAuthScene(
                    viewModel { BangumiOAuthViewModel() },
                    windowInsets = windowInsets,
                )
            }
            composable<NavRoutes.BangumiTokenAuth>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) {
                BangumiTokenAuthPage(
                    viewModel { BangumiTokenAuthViewModel() },
                    Modifier.fillMaxSize(),
                    windowInsets,
                )
            }
            composable<NavRoutes.SubjectDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val details = backStackEntry.toRoute<NavRoutes.SubjectDetail>()
                val vm = viewModel<SubjectDetailsViewModel>(key = details.subjectId.toString()) {
                    SubjectDetailsViewModel(details.subjectId)
                }
                SubjectDetailsScene(
                    vm,
                    onPlay = { aniNavigator.navigateEpisodeDetails(details.subjectId, it) },
                    windowInsets = windowInsets,
                )
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
                        initialSubjectId = route.subjectId,
                        initialEpisodeId = route.episodeId,
                        initialIsFullscreen = false,
                        context,
                    )
                }
                EpisodeScene(vm, Modifier.fillMaxSize(), windowInsets)
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
                SettingsPage(
                    viewModel {
                        SettingsViewModel()
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    route.tab,
                    showNavigationIcon = true,
                )
            }
            composable<NavRoutes.Caches>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) {
                CacheManagementPage(
                    viewModel { CacheManagementViewModel(aniNavigator) },
                    showBack = true,
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                )
            }
            composable<NavRoutes.CacheDetail>(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.CacheDetail>()
                MediaCacheDetailsPage(
                    viewModel(key = route.toString()) { MediaCacheDetailsPageViewModel(route.cacheId) },
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
                SubjectCacheScene(vm, Modifier.fillMaxSize(), windowInsets)
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
                    RssMediaSource.FactoryId -> EditRssMediaSourcePage(
                        viewModel<EditRssMediaSourceViewModel>(key = mediaSourceInstanceId) {
                            EditRssMediaSourceViewModel(mediaSourceInstanceId)
                        },
                        mediaDetailsColumn = { media ->
                            MediaDetailsLazyGrid(
                                media,
                                null,
                                Modifier.fillMaxSize(),
                                showSourceInfo = false,
                            )
                        },
                        Modifier,
                        windowInsets,
                    )

                    SelectorMediaSource.FactoryId -> {
                        val context = LocalContext.current
                        EditSelectorMediaSourcePage(
                            viewModel<EditSelectorMediaSourceViewModel>(key = mediaSourceInstanceId) {
                                EditSelectorMediaSourceViewModel(mediaSourceInstanceId, context)
                            },
                            Modifier,
                            windowInsets = windowInsets,
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
            ) {
                val viewModel = viewModel { PeerFilterSettingsViewModel() }
                PeerFilterSettingsPage(
                    viewModel.state,
                    Modifier.desktopTitleBarPadding(),
                    windowInsets = ScaffoldDefaults.contentWindowInsets,
                )
            }
        }
    }
}
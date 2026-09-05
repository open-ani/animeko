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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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
import me.him188.ani.app.navigation.rememberAniBackStack
import me.him188.ani.tv.ui.episode.TvEpisodeScreen
import me.him188.ani.tv.ui.episode.TvEpisodeViewModel
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.subject.TvSubjectDetailsScreen

/**
 * TV 端根内容: 注册 TV 支持的 [NavRoutes] 子集 (atv-architecture.md §6.3), Navigation 3
 * backStack 模型 (接线同手机 AniAppContent).
 *
 * `Caches`/`BangumiAuthorize` 等按 §1.2 裁剪永不注册.
 */
@Composable
fun TvAniAppContent(
    aniNavigator: AniNavigator,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberAniBackStack(NavRoutes.Main(MainScreenPage.Exploration))
    aniNavigator.setBackStack(backStack)
    // 主壳焦点记忆放 NavDisplay 之上: 进详情页返回时 Main 条目组合重建, 记忆须跨 route 存活
    // (身份键恢复流程见 TvFocusMemory)
    val shellFocusMemory = remember { TvFocusMemory() }

    CompositionLocalProvider(LocalNavigator provides aniNavigator) {
        // tv MaterialTheme 不绘制窗口背景, 根部铺一层 Surface (深色 surface + content color)
        Surface(modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { aniNavigator.popBackStack() },
                entryDecorators = listOf(
                    // 让每个页面各自持有 rememberSaveable 状态和 ViewModel, 出栈时一并销毁
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<NavRoutes.Main> {
                        TvMainShell(focusMemory = shellFocusMemory)
                    }

                    entry<NavRoutes.SubjectDetail> { route ->
                        TvSubjectDetailsScreen(
                            subjectId = route.subjectId,
                            placeholder = route.placeholder?.run {
                                SubjectInfo.createPlaceholder(id, name, coverUrl, nameCN)
                            },
                            onPlayEpisode = { episodeId ->
                                aniNavigator.navigateEpisodeDetails(route.subjectId, episodeId)
                            },
                            onClickRelated = { relatedId ->
                                aniNavigator.navigateSubjectDetails(relatedId, null)
                            },
                        )
                    }

                    entry<NavRoutes.EpisodeDetail> { route ->
                        val context = LocalContext.current.applicationContext
                        val vm = viewModel<TvEpisodeViewModel>(key = route.episodeId.toString()) {
                            TvEpisodeViewModel(route.subjectId, route.episodeId, context)
                        }
                        TvEpisodeScreen(
                            vm,
                            onClickRelatedSubject = { relatedId ->
                                aniNavigator.navigateSubjectDetails(relatedId, null)
                            },
                        )
                    }
                },
            )
        }
    }
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Stable
import androidx.paging.cachedIn
import androidx.paging.compose.launchAsLazyPagingItemsIn
import androidx.paging.filter
import androidx.paging.flatMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.ui.exploration.ExplorationPageState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class ExplorationPageViewModel : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val recommendationRepository: RecommendationRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val followedSubjectsRepository: FollowedSubjectsRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val nsfwSettingFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }
    private val horizontalScrollTipFlow =
        settingsRepository.oneshotActionConfig.flow.map { it.horizontalScrollTip }

    // 继续观看栏目的强制刷新 (TV 端长按播放键). 它平时只跟着仓库里一小时一跳的 ticker 走 ——
    // 那一跳会先同步服务器最近改动的收藏再重算每部的播放进度, restart 等价于立刻走一遍
    private val followedSubjectsRestarter = FlowRestarter()

    val explorationPageState: ExplorationPageState = ExplorationPageState(
        trendingSubjectInfoPager = trendsRepository.trendsInfoPager()
            .map { pagingData ->
                pagingData.flatMap { it.subjects.take(10) }
            }
            .cachedIn(backgroundScope)
            .launchAsLazyPagingItemsIn(backgroundScope),
//        TrendingSubjectsState(
//            suspend { trendsRepository.getTrendsInfo() }
//                .asFlow()
//                .retryWithBackoffDelay()
//                .map { it.subjects }
//                .produceState(null),
//        ),
        followedSubjectsPager = combine(
            settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            followedSubjectsRepository.followedSubjectsPager().restartable(followedSubjectsRestarter),
        ) { nsfwMode, subjects ->
            if (nsfwMode != NsfwMode.HIDE) return@combine subjects
            subjects.filter { !it.subjectInfo.nsfw }
        }.cachedIn(backgroundScope),
        onRefreshFollowedSubjects = { followedSubjectsRestarter.restart() },
        recommendationPager = recommendationRepository.recommendedSubjectsPager().cachedIn(backgroundScope),
        horizontalScrollTipFlow = horizontalScrollTipFlow,
        onSetDisableHorizontalScrollTip = {
            backgroundScope.launch {
                settingsRepository.oneshotActionConfig.update { copy(horizontalScrollTip = false) }
            }
        },
//            .onStart<List<FollowedSubjectInfo?>> {
//                emit(arrayOfNulls<FollowedSubjectInfo>(10).toList())
//            }
    )
}

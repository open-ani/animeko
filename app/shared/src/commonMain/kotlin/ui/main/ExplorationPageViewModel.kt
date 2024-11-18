/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.paging.flatMap
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.userInfo
import me.him188.ani.app.ui.exploration.ExplorationPageState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class ExplorationPageViewModel(
    val subjectDetailsStateLoader: SubjectDetailsStateLoader,
) : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val followedSubjectsRepository: FollowedSubjectsRepository by inject()
    private val authState = AuthState()

    @OptIn(OpaqueSession::class)
    private val selfInfoState = sessionManager.userInfo.produceState(null)

    val explorationPageState: ExplorationPageState = ExplorationPageState(
        authState,
        selfInfoState,
        trendingSubjectInfoPager = trendsRepository.trendsInfoPager()
            .map { pagingData ->
                pagingData.flatMap { it.subjects }
            }
            .launchAsLazyPagingItemsIn(backgroundScope),
//        TrendingSubjectsState(
//            suspend { trendsRepository.getTrendsInfo() }
//                .asFlow()
//                .retryWithBackoffDelay()
//                .map { it.subjects }
//                .produceState(null),
//        ),
        followedSubjectsPager = followedSubjectsRepository.followedSubjectsPager().cachedIn(backgroundScope),
//            .onStart<List<FollowedSubjectInfo?>> {
//                emit(arrayOfNulls<FollowedSubjectInfo>(10).toList())
//            }
        subjectDetailsStateLoader = subjectDetailsStateLoader,
    )
}

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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.userInfo
import me.him188.ani.app.ui.exploration.ExplorationPageState
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.utils.coroutines.retryUntilSuccess
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class ExplorationPageViewModel : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val authState = AuthState()

    @OptIn(OpaqueSession::class)
    private val selfInfoState = sessionManager.userInfo.produceState(null)

    val explorationPageState: ExplorationPageState = ExplorationPageState(
        authState,
        selfInfoState,
        TrendingSubjectsState(
            suspend { trendsRepository.getTrending() }
                .asFlow()
                .map { it.getOrNull() }
                .retryUntilSuccess()
                .map { it?.subjects }
                .produceState(null),
        ),
    )
}

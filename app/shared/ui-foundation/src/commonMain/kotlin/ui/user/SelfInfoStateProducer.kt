/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.user

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

@Immutable
data class SelfInfoUiState(
    val selfInfo: SelfInfo?,
    val isLoading: Boolean,
    /**
     * `null` means loading
     */
    val isSessionValid: Boolean?,
    /**
     * `null means loading
     */
    val bangumiConnected: Boolean?
)

@TestOnly
val TestSelfInfoUiState
    get() = SelfInfoUiState(
        SelfInfo(
            id = Uuid.random(),
            nickname = "TestUser",
            email = "test@animeko.org",
            hasPassword = false,
            avatarUrl = null,
            bangumiUsername = "TestBangumiUser",
        ),
        isLoading = false,
        isSessionValid = true,
        bangumiConnected = true,
    )

class SelfInfoStateProducer(
    flowContext: CoroutineContext = Dispatchers.Default,
    koin: Koin = GlobalKoin,
) {
    private val sessionStateProvider: SessionStateProvider by koin.inject()
    private val userRepository: UserRepository by koin.inject()

    /**
     * 如果重新 collect 这个 flow, 会导致多次网络请求.
     */
    val flow = combine(sessionStateProvider.stateFlow, userRepository.selfInfoFlow) { sessionState, selfInfo ->
        val isSessionValid = sessionState is SessionState.Valid
        SelfInfoUiState(
            selfInfo = if (isSessionValid) selfInfo else null,
            isLoading = false,
            isSessionValid = isSessionValid,
            bangumiConnected = isSessionValid && sessionState.bangumiConnected,
        )
    }.stateIn(
        CoroutineScope(flowContext),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SelfInfoUiState(
            selfInfo = null,
            isLoading = true,
            isSessionValid = null,
            bangumiConnected = null,
        ),
    )
}

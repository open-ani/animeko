/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.AniAuthConfigurator
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OnboardingCompleteViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    private val authClient: AniAuthClient by inject()

    private val authConfigurator = AniAuthConfigurator(
        sessionManager = sessionManager,
        authClient = authClient,
        onLaunchAuthorize = { },
        parentCoroutineContext = backgroundScope.coroutineContext,
    )
    
    val state: Flow<OnboardingCompleteState> = authConfigurator.state
        .filterIsInstance<AuthStateNew.Success>()
        .map { 
            OnboardingCompleteState(
                username = if (it.isGuest) null else it.username,
                avatarUrl = if (it.isGuest) DEFAULT_AVATAR else (it.avatarUrl ?: DEFAULT_AVATAR)
            )
        }
        .stateInBackground(
            OnboardingCompleteState.Placeholder,
            SharingStarted.WhileSubscribed()
        )
    
    init {
        authConfigurator.checkAuthorizeState()
    }
    
    companion object {
        internal const val DEFAULT_AVATAR = "https://lain.bgm.tv/r/200/pic/user/l/icon.jpg"
    }
}
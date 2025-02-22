/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.AniAuthStateProvider
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OnboardingCompleteViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    private val authStateProvider: AniAuthStateProvider by inject()

    val state: Flow<OnboardingCompleteState> =
        combine(
            authStateProvider.state.filterIsInstance<AuthState.Success>(),
            settings.uiSettings.flow.map { it.mainSceneInitialPage },
        ) { authState, initialPage ->
            OnboardingCompleteState(
                username = if (authState.isGuest) null else authState.username,
                avatarUrl = if (authState.isGuest) DEFAULT_AVATAR else (authState.avatarUrl ?: DEFAULT_AVATAR),
                mainSceneInitialPage = initialPage,
            )
        }
            .stateInBackground(
                OnboardingCompleteState.Placeholder,
                SharingStarted.WhileSubscribed(),
            )

    companion object {
        internal const val DEFAULT_AVATAR = "https://lain.bgm.tv/r/200/pic/user/l/icon.jpg"
    }
}
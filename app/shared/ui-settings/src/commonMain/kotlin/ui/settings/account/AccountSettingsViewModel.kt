/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.runtime.Immutable
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * It is used on both [AccountSettingsPopup] and [AccountS].
 */
class AccountSettingsViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    private val selfStateFlow = SelfInfoStateProducer(koin = getKoin()).flow.stateInBackground()

    private val logoutTasker = MonoTasker(backgroundScope)
    private val avatarUploadTasker = MonoTasker(backgroundScope)

    private val stateRefresher = FlowRestarter()

    private val avatarUploadState =
        MutableStateFlow<EditProfileState.UploadAvatarState>(EditProfileState.UploadAvatarState.Default)

    val state = combine(
        selfStateFlow.filterNotNull(),
        sessionManager.stateProvider.stateFlow,
        avatarUploadState,
    ) { selfInfo, sessionState, avatarState ->
        AccountSettingsState(
            selfInfo = selfInfo,
            boundBangumi = sessionState is SessionState.Valid && sessionState.bangumiConnected,
            avatarUploadState = avatarState,
        )
    }
        .restartable(stateRefresher)
        .stateInBackground(
            initialValue = AccountSettingsState.Empty,
            started = SharingStarted.WhileSubscribed(5_000),
        )

    fun logout() {
        logoutTasker.launch {
            sessionManager.clearSession()
        }
    }

    fun resetAvatarUploadState() {
        avatarUploadState.value = EditProfileState.UploadAvatarState.Default
    }

    fun uploadAvatar(file: PlatformFile) {
        avatarUploadTasker.launch {
            avatarUploadState.value = EditProfileState.UploadAvatarState.Uploading

            /*delay(1.seconds)

            if (Random.nextFloat() >= 0.5) {
                avatarUploadState.value = EditProfileState.UploadAvatarState.Success("")
            } else {
                avatarUploadState.value = EditProfileState.UploadAvatarState.Failed(
                    file = file,
                    loadError = LoadError.fromException(IllegalStateException("Upload failed, this is a test error")),
                )
            }*/

            refreshState()
        }
    }

    fun validateUsername(username: String): Boolean {
        if (username.isEmpty()) {
            return true
        }

        if (username.isBlank() || username.length > 20) {
            return false
        }
        return USERNAME_MATCHER.matches(username)
    }

    fun saveProfile(profile: EditProfileState) {
        backgroundScope.launch {

            refreshState()
        }
    }

    fun refreshState() {
        stateRefresher.restart()
    }

    companion object {
        private val USERNAME_MATCHER = Regex("^[\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FFa-zA-Z\\d_]+$")
    }
}

@Immutable
class AccountSettingsState(
    val selfInfo: SelfInfoUiState,
    val boundBangumi: Boolean,
    val avatarUploadState: EditProfileState.UploadAvatarState,
) {
    companion object {
        val Empty = AccountSettingsState(
            selfInfo = SelfInfoUiState(null, true, null),
            boundBangumi = false,
            avatarUploadState = EditProfileState.UploadAvatarState.Default,
        )
    }
}

@Immutable
class EditProfileState(
    val username: String,
) {
    companion object {
        val Empty = EditProfileState(
            username = "",
        )
    }

    @Immutable
    sealed class UploadAvatarState {
        data object Default : UploadAvatarState()

        data object Uploading : UploadAvatarState()

        data class Success(val url: String) : UploadAvatarState()

        data class Failed(val file: PlatformFile, val loadError: LoadError) : UploadAvatarState()
    }
}

@OptIn(TestOnly::class)
val TestAccountSettingsState
    get() = AccountSettingsState(
        TestSelfInfoUiState,
        false,
        EditProfileState.UploadAvatarState.Default,
    )
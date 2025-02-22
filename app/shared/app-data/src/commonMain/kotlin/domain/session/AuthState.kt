/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.utils.platform.annotations.TestOnly

// This class is intend to replace current [AuthState]
@Stable
sealed class AuthState {
    @Immutable
    data object Idle : AuthState()

    @Stable
    sealed class AwaitingResult : AuthState() {
        abstract val requestId: String
    }

    @Stable
    data class AwaitingToken(override val requestId: String) : AwaitingResult()

    @Stable
    data class AwaitingUserInfo(override val requestId: String) : AwaitingResult()

    sealed class Error : AuthState()

    @Immutable
    data object NetworkError : Error()

    @Immutable
    data object TokenExpired : Error()

    @Stable
    data class UnknownError(val throwable: Throwable) : Error()

    @Stable
    data class Success(
        val username: String,
        val avatarUrl: String?,
        val isGuest: Boolean
    ) : AuthState()

    val isKnownLoggedIn: Boolean get() = this is Success && !isGuest
    val isKnownGuest: Boolean get() = this is Success && isGuest
    val isKnownLoggedOut: Boolean get() = this is NetworkError || this is TokenExpired || this is Idle
    val isKnownExpired: Boolean get() = this is TokenExpired
    val isLoading: Boolean get() = this is AwaitingResult
}

@Stable
@TestOnly
val TestUserInfo
    get() = UserInfo(
        id = 1,
        username = "Tester",
        nickname = "Tester",
    )

@Stable
@TestOnly
val TestSelfInfo get() = TestUserInfo


@Stable
@TestOnly
val TestAuthState
    get() = AuthState.Success(
        username = "Tester",
        avatarUrl = null,
        isGuest = false,
    )

@Stable
@TestOnly
val TestGuestAuthState
    get() = AuthState.Success(
        username = "",
        avatarUrl = null,
        isGuest = true,
    )
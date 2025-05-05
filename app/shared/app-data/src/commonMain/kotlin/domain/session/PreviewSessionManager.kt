/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.Session
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.days

object PreviewSessionManager : SessionManager {
    private val savedSession: MutableStateFlow<Session?> = MutableStateFlow(
        AccessTokenSession(
            tokens = AccessTokenPair(
                bangumiAccessToken = "testToken",
                aniAccessToken = "testToken",
                expiresAtMillis = currentTimeMillis() + 1.days.inWholeMilliseconds,
            ),
        ),
    )
    override val state: Flow<SessionStatus> =
        MutableStateFlow(
            SessionStatus.Verified(
                AccessTokenPair(
                    bangumiAccessToken = "testToken",
                    aniAccessToken = "testToken",
                    expiresAtMillis = currentTimeMillis() + 1.days.inWholeMilliseconds,
                ),
                UserInfo.EMPTY,
            ),
        )
    override val processingRequest: MutableStateFlow<ExternalOAuthRequest?> =
        MutableStateFlow(null)
    override val events: SharedFlow<SessionEvent> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun requireAuthorize(onLaunch: suspend () -> Unit, skipOnGuest: Boolean) {
    }

    override fun requireAuthorizeAsync(onLaunch: suspend () -> Unit, skipOnGuest: Boolean) {
    }

    override suspend fun setSession(session: Session) {
        savedSession.value = session
    }

    override suspend fun retry() {
    }

    override suspend fun clearSession() {
        savedSession.value = null
    }
}
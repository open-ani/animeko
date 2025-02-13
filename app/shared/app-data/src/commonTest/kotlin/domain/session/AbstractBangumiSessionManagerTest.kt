/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package me.him188.ani.app.domain.session

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepositoryImpl
import me.him188.ani.app.domain.media.fetch.AtomicInteger
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.client.apis.BangumiOAuthAniApi
import me.him188.ani.client.infrastructure.HttpResponse
import me.him188.ani.client.models.AniBangumiUserToken
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

sealed class AbstractBangumiSessionManagerTest {
    internal companion object {
        internal const val ACCESS_TOKEN = "testToken"
        internal const val REFRESH_TOKEN = "refreshToken"
        internal val SUCCESS_USER_INFO = UserInfo.EMPTY
    }

    // default state:
    // - no session (access token)
    // - no refresh token

    internal val tokenRepository = TokenRepositoryImpl(MemoryDataStore(mutablePreferencesOf()))
    internal val refreshToken = MutableStateFlow<String?>(null)

    internal val getSelfInfoCalled = AtomicInteger(0)
    internal val refreshAccessTokenCalled = AtomicInteger(0)

    internal fun TestScope.createManager(
        getSelfInfo: suspend (accessToken: String) -> ApiResponse<UserInfo>,
        refreshAccessToken: suspend (refreshToken: String) -> ApiResponse<NewSession>,
        tokenRepository: TokenRepositoryImpl = this@AbstractBangumiSessionManagerTest.tokenRepository,
        refreshToken: Flow<String?> = this@AbstractBangumiSessionManagerTest.refreshToken,
        parentCoroutineContext: CoroutineContext = testScheduler,
    ) = BangumiSessionManager(
        tokenRepository = tokenRepository,
        refreshToken = refreshToken,
        getSelfInfo = {
            try {
                getSelfInfo(it)
            } finally {
                getSelfInfoCalled.incrementAndGet()
            }
        },
        refreshAccessToken = {
            try {
                refreshAccessToken(it)
            } finally {
                refreshAccessTokenCalled.incrementAndGet()
            }
        },
        parentCoroutineContext = parentCoroutineContext,
        enableSharing = false,
    )

    internal suspend fun BangumiSessionManager.awaitState(drop: Int = 0): SessionStatus {
        return statePass.drop(drop).first()
    }

    internal fun <T> noCall(): ApiResponse<T> {
        // 必须要返回一个, 因为 flow 实际上还会在跑一会
        return ApiResponse.failure(ApiFailure.Unauthorized)
    }

    internal suspend fun setExpiredToken() {
        tokenRepository.setSession(
            AccessTokenSession(
                accessToken = ACCESS_TOKEN,
                expiresAtMillis = 0, // expired
            ),
        )
    }

    internal suspend fun setValidToken(token: String = ACCESS_TOKEN) {
        tokenRepository.setSession(
            AccessTokenSession(
                accessToken = token,
                expiresAtMillis = Long.MAX_VALUE,
            ),
        )
    }

    internal fun TestScope.runCoroutines() {
        testScheduler.runCurrent()
    }

    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        testBody: suspend TestScope.() -> Unit
    ): TestResult = runComposeStateTest(context, testBody)
}
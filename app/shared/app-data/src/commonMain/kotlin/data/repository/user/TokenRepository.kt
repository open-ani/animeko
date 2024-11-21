/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.hours

/**
 * Do not access directly. Use [SessionManager] instead.
 */
interface TokenRepository {
    val refreshToken: Flow<String?>
    suspend fun setRefreshToken(value: String)

    /**
     * 当前的登录会话, 为 `null` 表示未登录.
     */
    val session: Flow<Session?>
    suspend fun setSession(session: Session)

    suspend fun clear()
}

sealed interface Session

/**
 * 以游客登录
 */
data object GuestSession : Session

/**
 * 以 Bangumi access token 登录
 */
// don't remove `data`. required for equals
data class AccessTokenSession(
    val accessToken: String,
    val expiresAtMillis: Long,
) : Session

fun AccessTokenSession.isValid() = !isExpired()
fun AccessTokenSession.isExpired() = expiresAtMillis <= currentTimeMillis() + 1.hours.inWholeMilliseconds

class TokenRepositoryImpl(
    store: DataStore<Preferences>,
) : TokenRepository {
    private companion object Keys {
        val USER_ID = longPreferencesKey("user_id")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")

        // Note: we added this because we cannot change ACCESS_TOKEN anymore because old users are using them.
        val IS_GUEST = stringPreferencesKey("is_guest")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val ACCESS_TOKEN_EXPIRE_AT = longPreferencesKey("access_token_expire_at")
    }

    private val tokenStore = store

    override val refreshToken: Flow<String?> = tokenStore.data.map { it[REFRESH_TOKEN] }

    override suspend fun setRefreshToken(value: String) {
        tokenStore.edit { it[REFRESH_TOKEN] = value }
    }

    override val session: Flow<Session?> = tokenStore.data.map { preferences ->
        val accessToken = preferences[ACCESS_TOKEN]
        val expireAt = preferences[ACCESS_TOKEN_EXPIRE_AT]
        val isGuest = preferences[IS_GUEST]?.toBooleanStrict()
        if (isGuest == true) {
            GuestSession
        } else {
            if (accessToken == null || expireAt == null) {
                return@map null
            }
            AccessTokenSession(
                accessToken = accessToken,
                expiresAtMillis = expireAt,
            )
        }
    }

    override suspend fun setSession(session: Session) {
        tokenStore.edit {
            when (session) {
                is AccessTokenSession -> {
                    it[ACCESS_TOKEN] = session.accessToken
                    it[ACCESS_TOKEN_EXPIRE_AT] = session.expiresAtMillis
                    it[IS_GUEST] = false.toString()
                }

                GuestSession -> {
                    it[IS_GUEST] = true.toString()
                }
            }

        }
    }

    override suspend fun clear() {
        tokenStore.edit {
            it.remove(USER_ID)
            it.remove(ACCESS_TOKEN)
            it.remove(ACCESS_TOKEN_EXPIRE_AT)
            it.remove(REFRESH_TOKEN)
            it.remove(IS_GUEST)
        }
    }
}

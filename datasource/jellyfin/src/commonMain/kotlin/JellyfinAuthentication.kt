/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.utils.ktor.ScopedHttpClient

internal data class JellyfinLoginSession(
    val userId: String,
    val accessToken: String,
    val authorizationHeader: String,
)

internal class JellyfinPasswordAuthenticator(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val deviceId: String,
    private val client: ScopedHttpClient,
) {
    private val sessionMutex = Mutex()
    private var session: JellyfinLoginSession? = null

    suspend fun getSession(): JellyfinLoginSession = sessionMutex.withLock {
        session ?: authenticate().also { session = it }
    }

    suspend fun invalidate(accessToken: String) {
        sessionMutex.withLock {
            if (session?.accessToken == accessToken) {
                session = null
            }
        }
    }

    private suspend fun authenticate(): JellyfinLoginSession {
        require(username.isNotBlank()) {
            "Jellyfin username must not be blank when username/password authentication is selected"
        }

        val authorizationHeader = buildAuthorizationHeader(accessToken = null)
        val result = try {
            client.use {
                val response = post("$baseUrl/Users/AuthenticateByName") {
                    header(HttpHeaders.Authorization, authorizationHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticateUserByNameRequest(
                            username = username,
                            password = password,
                        ),
                    )
                }
                if (response.status == HttpStatusCode.Unauthorized) {
                    throw JellyfinLoginException()
                }
                response.body<AuthenticationResult>()
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized) {
                throw JellyfinLoginException()
            }
            throw e
        }

        val accessToken = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw JellyfinLoginException("Jellyfin login response did not include an access token")
        val userId = result.user?.id?.takeIf { it.isNotBlank() }
            ?: throw JellyfinLoginException("Jellyfin login response did not include a user id")

        return JellyfinLoginSession(
            userId = userId,
            accessToken = accessToken,
            authorizationHeader = buildAuthorizationHeader(accessToken),
        )
    }

    private fun buildAuthorizationHeader(accessToken: String?): String {
        val values = buildList {
            add("""Client="Animeko"""")
            add("""Device="Animeko"""")
            add("""DeviceId="$deviceId"""")
            add("""Version="1.0"""")
            if (accessToken != null) {
                add("""Token="$accessToken"""")
            }
        }
        return values.joinToString(separator = ", ", prefix = "MediaBrowser ")
    }
}

internal class JellyfinLoginException(
    message: String = "Jellyfin rejected the username or password",
) : IllegalStateException(message)

@Serializable
private data class AuthenticateUserByNameRequest(
    @SerialName("Username")
    val username: String,
    @SerialName("Pw")
    val password: String,
)

@Serializable
private data class AuthenticationResult(
    @SerialName("AccessToken")
    val accessToken: String? = null,
    @SerialName("User")
    val user: AuthenticationUser? = null,
)

@Serializable
private data class AuthenticationUser(
    @SerialName("Id")
    val id: String? = null,
)

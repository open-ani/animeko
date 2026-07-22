/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.client.apis.WatchTogetherAniApi
import me.him188.ani.client.infrastructure.ApiClient
import me.him188.ani.client.models.AniJoinWatchTogetherRoomRequest
import me.him188.ani.client.models.AniLeaveWatchTogetherRoomRequest
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi

interface WatchTogetherApiService {
    suspend fun join(roomName: String, password: String, following: Boolean): AniWatchTogetherJoinResponse

    suspend fun report(
        roomId: String,
        request: AniReportWatchTogetherStateRequest,
    ): AniWatchTogetherReportResponse

    suspend fun leave(roomId: String, sessionNonce: String)

    fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent>
}

enum class WatchTogetherJoinFailure {
    WRONG_PASSWORD,
    ROOM_FULL,
    ROOM_CLOSED,
    INVALID_NAME,
    INVALID_PASSWORD,
    RATE_LIMITED,
    TEMPORARY,
}

class WatchTogetherJoinException(
    val failure: WatchTogetherJoinFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

sealed interface WatchTogetherServerEvent {
    data object Connected : WatchTogetherServerEvent
    data object Ping : WatchTogetherServerEvent
    data class Snapshot(val snapshot: AniWatchTogetherRoomSnapshot) : WatchTogetherServerEvent
    data class Bye(val reason: String) : WatchTogetherServerEvent
}

class DefaultWatchTogetherApiService(
    provider: AniApiProvider,
    private val eventsClient: ScopedHttpClient,
    private val json: Json = ApiClient.JSON_DEFAULT,
) : WatchTogetherApiService {
    private val api: ApiInvoker<WatchTogetherAniApi> = provider.watchTogetherApi
    override suspend fun join(roomName: String, password: String, following: Boolean): AniWatchTogetherJoinResponse {
        try {
            return api {
                joinWatchTogetherRoom(AniJoinWatchTogetherRoomRequest(roomName, password, following)).body()
            }
        } catch (exception: ResponseException) {
            val responseBody = runCatching { exception.response.bodyAsText() }.getOrNull().orEmpty()
            val failure = WatchTogetherJoinFailure.entries.firstOrNull { failure ->
                responseBody.contains(failure.name) || exception.response.status.description == failure.name
            } ?: when (exception.response.status.value) {
                429 -> WatchTogetherJoinFailure.RATE_LIMITED
                in 500..599 -> WatchTogetherJoinFailure.TEMPORARY
                else -> null
            }
            if (failure != null) throw WatchTogetherJoinException(failure, exception)
            throw exception
        }
    }

    override suspend fun report(
        roomId: String,
        request: AniReportWatchTogetherStateRequest,
    ): AniWatchTogetherReportResponse = api {
        reportWatchTogetherState(roomId, request).body()
    }

    override suspend fun leave(roomId: String, sessionNonce: String) {
        api {
            leaveWatchTogetherRoom(roomId, AniLeaveWatchTogetherRoomRequest(sessionNonce)).body()
        }
    }

    @OptIn(UnsafeScopedHttpClientApi::class)
    override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = flow {
        val url = URLBuilder(ServerListFeatureConfig.MAGIC_ANI_SERVER).apply {
            appendPathSegments("v2", "watch-together", "rooms", roomId, "events")
            parameters.append("sessionNonce", sessionNonce)
        }.buildString()
        val ticket = eventsClient.borrow()
        try {
            ticket.client.sse(urlString = url) {
                incoming.collect { event ->
                    when (event.event) {
                        "snapshot" -> event.data?.let { data ->
                            emit(WatchTogetherServerEvent.Snapshot(json.decodeFromString(data)))
                        }

                        "bye" -> {
                            val reason = event.data?.let { data ->
                                runCatching {
                                    json.parseToJsonElement(data).jsonObject["reason"]?.jsonPrimitive?.content
                                }.getOrNull()
                            }.orEmpty()
                            emit(WatchTogetherServerEvent.Bye(reason))
                        }

                        else -> when (event.comments) {
                            "connected" -> emit(WatchTogetherServerEvent.Connected)
                            "ping" -> emit(WatchTogetherServerEvent.Ping)
                        }
                    }
                }
            }
        } finally {
            eventsClient.returnClient(ticket)
        }
    }
}

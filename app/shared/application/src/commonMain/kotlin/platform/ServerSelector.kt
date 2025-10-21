/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.selects.select
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

class ServerSelector(
    preferGlobalServerFlow: Flow<Boolean?>,
    private val httpClientProvider: HttpClientProvider,
    scope: CoroutineScope,
) {
    private val logger = logger<ServerSelector>()

    val flow = preferGlobalServerFlow.distinctUntilChanged().map { preferGlobalServer ->
        val globalServerList = AniServers.preferGlobal.map { it.url }
        val directServerList = AniServers.preferDirect.map { it.url }
        try {
            when (preferGlobalServer) {
                true -> globalServerList
                false -> directServerList

                null -> {
                    getFastestServerUrls(globalServerList)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { // should not happen
            logger.warn("Failed to select server, see cause", e)
            Analytics.recordEvent(AnalyticsEvent.AppServerSelectError)
            // 出错时返回默认列表
            globalServerList
        }
    }.shareIn(scope, started = SharingStarted.Lazily, replay = 1)

    private suspend fun getFastestServerUrls(globalServerList: List<Url>): List<Url> {
        // 测速判断
        val fastestServer = try {
            val (fastest, time) = getFastestServer()
            Analytics.recordEvent(AnalyticsEvent.AppServerTestSuccess) {
                put("fastest_server_id", fastest.id)
                put("fastest_server_url", fastest.url.toString())
            }
            logger.info {
                "Server speed test completed in ${time.inWholeMilliseconds} ms, " +
                        "fastest server: ${fastest.id} (${fastest.url})"
            }
            fastest.url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn("Failed to test server speed, see cause", e)
            Analytics.recordEvent(AnalyticsEvent.AppServerTestError)
            null
        }

        return if (fastestServer == null) {
            globalServerList
        } else {
            globalServerList.toMutableList().apply {
                remove(fastestServer)
                add(0, fastestServer)
            }
        }
    }

    private suspend fun getFastestServer(): TimedValue<AniServer> = measureTimedValue {
        coroutineScope {
            val deferreds = AniServers.allServers.map { server ->
                async {
                    try {
                        connect(server.url.toString())
                        Result.success(server)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
            }.toMutableList()

            var lastFailure: Throwable? = null

            try {
                while (deferreds.isNotEmpty()) {
                    var selected: Deferred<Result<AniServer>>? = null
                    val result = select {
                        deferreds.forEach { deferred ->
                            deferred.onAwait { value ->
                                selected = deferred
                                value
                            }
                        }
                    }
                    selected?.let { deferreds.remove(it) }
                    if (result.isSuccess) {
                        return@coroutineScope result.getOrThrow()
                    } else {
                        lastFailure = result.exceptionOrNull()
                    }
                }
            } finally {
                deferreds.forEach { it.cancel() }
            }

            throw lastFailure ?: IllegalStateException("No server completed successfully")
        }
    }

    private suspend fun connect(url: String) {
        httpClientProvider.get().use {
            get(url) {
                url { appendPathSegments("status") }
            }.body<Unit>()
        }
    }
}

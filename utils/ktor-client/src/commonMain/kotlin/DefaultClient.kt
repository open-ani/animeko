/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.HttpLogger.logHttp
import me.him188.ani.utils.logging.Logger
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.measureTimedValue

// 根据不同平台，选择相应的 HttpClientEngine
expect fun getPlatformKtorEngine(): HttpClientEngineFactory<*>

/**
 * Note: 尽可能使用 `HttpClientProvider` 来共享 [HttpClient] 实例. 因为每个实例都潜在地会有一个线程池.
 */
fun createDefaultHttpClient(
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(getPlatformKtorEngine()) {
    install(HttpRequestRetry) {
        maxRetries = 1
        delayMillis { 1000 }
        retryIf { cause, response ->
            // 只重试网络异常
            cause is IOException
        }
    }
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    BrowserUserAgent()
    install(ContentNegotiation) {
        val xmlConverter = getXmlConverter()
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
        register(ContentType.Text.Html, xmlConverter)
        register(ContentType.Text.Xml, xmlConverter)
    }
    followRedirects = true
    install(HttpRedirect) {
        checkHttpMethod = false
        allowHttpsDowngrade = true
    }
    expectSuccess = true // All clients actually expect success by default in clientConfig, so we move them here
    clientConfig()
}

fun HttpClient.registerLogging(
    logger: Logger = logger("ktor"),
) {
    plugin(HttpSend).intercept { request ->
        val (result, duration) = measureTimedValue {
            kotlin.runCatching { execute(request) }
        }

        logger.logHttp(
            method = request.method,
            url = request.url.toString(),
            isAuthorized = request.headers.contains(HttpHeaders.Authorization),
            responseStatus = result.map { it.response.status },
            duration = duration,
        )
        result.getOrThrow()
    }
}

object HttpLogger {
    fun Logger.logHttp(
        method: HttpMethod,
        url: String,
        isAuthorized: Boolean,
        responseStatus: Result<HttpStatusCode>,
        duration: Duration,
    ) {
        when {
            // 刻意没记录 exception, 因为外面应该会处理
            responseStatus.isFailure -> {
                if (responseStatus.exceptionOrNull() is CancellationException) {
                    warn { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
                } else {
                    error { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
                }
            }

            responseStatus.getOrNull()?.isSuccess() == true ->
                info { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }

            else -> warn { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
        }
    }

    private fun buildHttpRequestLog(
        method: HttpMethod,
        url: String,
        isAuthorized: Boolean,
        responseStatus: Result<HttpStatusCode>,
        duration: Duration,
    ): String {
        val methodStr = method.value.padStart(5, ' ')
        return buildString {
            append(methodStr)
            append(" ")
            append(url)
            append(" ")
            if (isAuthorized) {
                append("[Authorized]")
            }

            append(": ")

            responseStatus.fold(
                onSuccess = {
                    append(it.toString()) // "404 Not Found"
                },
                onFailure = {
                    when (it) {
                        is CancellationException -> append("CANCELLED")
                        is IOException -> append("IO_EXCEPTION")
                        else -> append("FAILED")
                    }
                },
            )

            append(" in ")
            append(duration.toString())
        }
    }
}


internal expect fun getXmlConverter(): ContentConverter

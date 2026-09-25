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
import io.ktor.client.statement.HttpResponse
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
 * 设置 [getPlatformKtorEngine] 引擎对单个 host 的并发请求上限. 使用其他引擎时不做任何设置.
 *
 * OkHttp 默认每个 client 只放行 5 个, 超出的请求在引擎内部排队, 调用方自己的并发数因此不起作用.
 * NSURLSession 只限制连接数而不限制请求数, 见 apple 端的实现.
 */
expect fun HttpClientConfig<*>.engineMaxRequestsPerHost(value: Int)

/**
 * 向这个响应所在的 host 并发发出多个请求时, 它们是否共用少数几条连接, 而不是每个请求各开一条.
 * 对同一源站同时发出几十个请求之前据此决定并发数: 各开一条连接就是几十次 TLS 握手, 源站可能因此限流.
 */
expect fun HttpResponse.sharesConnectionsAcrossRequests(): Boolean

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

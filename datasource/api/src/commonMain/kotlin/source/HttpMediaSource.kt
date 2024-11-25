/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.ContentConverter
import io.ktor.utils.io.core.Closeable
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.registerLogging
import me.him188.ani.utils.logging.logger

fun Closeable.asAutoCloseable() = AutoCloseable { close() }

/**
 * 支持执行 HTTP 请求的 [MediaSource]. 封装一些便捷的操作
 */
abstract class HttpMediaSource : MediaSource {
    private val closeables = mutableListOf<AutoCloseable>()

    /**
     * `public` because used by both [useHttpClient] and inheritors from other modules.
     */
    val logger = logger(this::class)

    fun addCloseable(closeable: AutoCloseable) {
        closeables.add(closeable)
    }

    override fun close() {
        super.close()
        this.closeables.forEach { it.close() }
    }

    companion object
}

/**
 * @param bearerTokens 可便捷地为每个请求附加 bearer token: "Authorization: Bearer token"
 * @param basicAuth 可便捷地为每个请求附加 basic auth: "Authorization: Basic username:password"
 */
fun HttpMediaSource.useHttpClient(
    mediaSourceConfig: MediaSourceConfig,
    timeoutMillis: Long = 30_000,
    bearerTokens: BearerTokens? = null,
    basicAuth: BasicAuthCredentials? = null,
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    return HttpMediaSource.createHttpClient(timeoutMillis, bearerTokens, basicAuth) {
        applyMediaSourceConfig(mediaSourceConfig)
        clientConfig()
    }.apply {
        registerLogging(logger)
    }.also { addCloseable(it.asAutoCloseable()) }
}

fun HttpMediaSource.Companion.createHttpClient(
    timeoutMillis: Long = 30_000,
    bearerTokens: BearerTokens? = null,
    basicAuth: BasicAuthCredentials? = null,
    clientConfig: HttpClientConfig<*>.() -> Unit
) = createDefaultHttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = timeoutMillis
    }
    if (bearerTokens != null) {
        Auth {
            bearer {
                loadTokens { bearerTokens }
            }
        }
    }
    if (basicAuth != null) {
        Auth {
            basic {
                credentials { basicAuth }
            }
        }
    }
    expectSuccess = true
    install(ContentNegotiation) {
        val xmlConverter = getXmlConverter()
        register(ContentType.Text.Xml, xmlConverter)
        register(ContentType.Text.Html, xmlConverter)
    }

    clientConfig()
}

internal expect fun getXmlConverter(): ContentConverter

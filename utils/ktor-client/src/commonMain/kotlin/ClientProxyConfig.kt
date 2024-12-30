/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.http
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class ClientProxyConfig(
    /**
     * "http://sample-proxy-server:3128/"
     * or
     * "socks5://sample-proxy-server:3128"
     */
    val url: String,

    /**
     * ProxyAuthorization header
     *
     * "Basic username:password"
     */
    val authorization: String? = null
)

object ClientProxyConfigValidator {
    fun parseProxy(url: String): ProxyConfig = if (url.startsWith("socks")) {
        Url(url).run {
            ProxyBuilder.socks(host, port)
        }
    } else {
        ProxyBuilder.http(url)
    }

    fun isValidProxy(url: String, allowSocks: Boolean = true): Boolean {
        return try {
            val u = URLBuilder(protocol = URLProtocol("dummy", 1)).takeFrom(url).build()
            if (u.host.isBlank()) return false
            if (!allowSocks && u.protocol.name in setOf("socks", "socks5")) return false
            if (u.protocol.name !in setOf("http", "https", "socks", "socks5")) return false
            true
        } catch (e: URLParserException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}

fun HttpClientConfig<*>.userAgent(
    value: String?
) {
    value ?: return
    install(UserAgent) {
        agent = value
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun HttpClientConfig<*>.proxy(
    config: ClientProxyConfig?,
) {
    engine {
        this.setProxy(config)
    }

    defaultRequest {
        val credentials = Base64.encode("jetbrains:foobar".toByteArray())
        header(HttpHeaders.ProxyAuthorization, "Basic $credentials")
    }
}

fun HttpClientEngineConfig.setProxy(
    config: ClientProxyConfig?,
) {
    proxy = if (config == null) {
        null
    } else {
        ClientProxyConfigValidator.parseProxy(config.url)
    }
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.utils.coroutines.Symbol
import me.him188.ani.utils.ktor.userAgent
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField


/**
 * @param V type of the feature value.
 */ // stable equals required
data class ScopedHttpClientFeatureKey<V>(
    // must be unique
    val id: String,
)

/**
 * 用于在 [HttpClientProvider] 构造新的 [HttpClient] 时，根据请求的 [ScopedHttpClientFeatureKeyValue] 来配置 [HttpClient] 的特性.
 *
 * 有两个 [applyToConfig] 方法，接受 [HttpClientConfig] 参数的会被优先调用.
 * 待所有 [handler][ScopedHttpClientFeatureHandler] 的 [applyToConfig] 方法调用完毕后，
 * 才会分别调用它们的 [applyToClient] 方法.
 */
abstract class ScopedHttpClientFeatureHandler<V>(
    val key: ScopedHttpClientFeatureKey<V>,
) {
    /**
     * 如果这个 feature 是在 client 之前就可以设置 (修改 [HttpClientConfig]), 则应该重写这个方法.
     */
    open fun applyToConfig(config: HttpClientConfig<*>, value: V) {}

    /**
     * 如果这个 feature 是在 client 创建后才可以设置, 则应该重写这个方法.
     */
    open fun applyToClient(client: HttpClient, value: V) {}
}

/**
 * 为一个特性 [this] 指定一个值.
 * @see HttpClientProvider.get
 */
fun <T> ScopedHttpClientFeatureKey<T>.withValue(value: T): ScopedHttpClientFeatureKeyValue<T> =
    ScopedHttpClientFeatureKeyValue.create(this, value)

/**
 * 为一个特性 [key] 指定的值.
 * @see HttpClientProvider.get
 */
@ConsistentCopyVisibility
data class ScopedHttpClientFeatureKeyValue<V> private constructor(
    val key: ScopedHttpClientFeatureKey<V>,
    internal val value: Any?,
) {
    companion object {
        /**
         * 为 [key] 指定值为 [value].
         */
        fun <V> create(
            key: ScopedHttpClientFeatureKey<V>,
            value: V,
        ): ScopedHttpClientFeatureKeyValue<V> {
            require(value != FEATURE_NOT_SET) { "Value must not be FEATURE_NOT_SET" }
            return ScopedHttpClientFeatureKeyValue(key, value)
        }

        /**
         * 为 [key] 采用默认值.
         */
        fun <V> createNotSet(key: ScopedHttpClientFeatureKey<V>): ScopedHttpClientFeatureKeyValue<V> {
            return ScopedHttpClientFeatureKeyValue(key, FEATURE_NOT_SET)
        }
    }
}

@JvmField
internal val FEATURE_NOT_SET = Symbol("NOT_REQUESTED")


// region UserAgent
val UserAgentFeature = ScopedHttpClientFeatureKey<ScopedHttpClientUserAgent>("UserAgent")

object UserAgentFeatureHandler :
    ScopedHttpClientFeatureHandler<ScopedHttpClientUserAgent>(UserAgentFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: ScopedHttpClientUserAgent) {
        when (value) {
            ScopedHttpClientUserAgent.ANI -> config.userAgent(getAniUserAgent())
            ScopedHttpClientUserAgent.BROWSER -> config.BrowserUserAgent()
        }
    }
}

enum class ScopedHttpClientUserAgent {
    ANI,
    BROWSER
}

// endregion

abstract class AbstractBearerTokenHandler(
    key: ScopedHttpClientFeatureKey<Boolean>,
    private val bearerToken: Flow<String?>,
    private val onRefresh: suspend () -> BearerTokens?,
) : ScopedHttpClientFeatureHandler<Boolean>(key) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: Boolean) {
        if (!value) return
        config.install(Auth) {
            bearer {
                loadTokens {
                    bearerToken.first()?.let {
                        BearerTokens(it, "")
                    }
                }

                refreshTokens {
                    onRefresh()
                }
            }
        }
    }

    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            val originalCall = execute(request)
            if (originalCall.response.status.value !in 100..399) {
                execute(request)
            } else {
                originalCall
            }
        }
    }
}

// region UseBangumiToken = ScopedHttpClientFeatureKey<Boolean>("UseBangumiToken")
val UseAniTokenFeature = ScopedHttpClientFeatureKey<Boolean>("UseAniToken")

class UseAniTokenFeatureHandler(
    bearerToken: Flow<String?>,
    onRefresh: suspend () -> BearerTokens?,
) : AbstractBearerTokenHandler(UseAniTokenFeature, bearerToken, onRefresh)

// endregion


// region ServerListFeature
/**
 * 自动替换请求中的 host, 使用多个 URL 重试.
 */
val ServerListFeature = ScopedHttpClientFeatureKey<ServerListFeatureConfig>("ServerList")

data class ServerListFeatureConfig(
    val aniServerRules: AniServerRule?,
) {
    data class AniServerRule(
        /**
         * 如果请求的 host 为其中任何一个, 则替换为 ani 服务器地址, 并依次重试.
         */
        val hostMatches: Set<String>,
    ) {
        init {
            require(hostMatches.isNotEmpty()) { "hostMatches must not be empty" }
        }
    }

    companion object {
        const val MAGIC_ANI_SERVER_HOST = "MAGIC_ANI_SERVER"
        const val MAGIC_ANI_SERVER = "https://$MAGIC_ANI_SERVER_HOST/"

        val Default = ServerListFeatureConfig(
            aniServerRules = AniServerRule(
                hostMatches = setOf(MAGIC_ANI_SERVER_HOST),
            ),
        )
    }
}

data class ServerListFeatureHandler(
    private val aniServerUrls: Flow<List<Url>>,
) : ScopedHttpClientFeatureHandler<ServerListFeatureConfig>(ServerListFeature) {
    // Sticky selection state: remembers the last successful server index for the
    // current list of servers. Reset when the server list changes (by signature).
    private data class SelectionState(val signature: Int, val index: Int)

    private val selectionState = kotlinx.atomicfu.atomic<SelectionState?>(null)
    override fun applyToClient(client: HttpClient, value: ServerListFeatureConfig) {
        client.plugin(HttpSend).intercept { request ->
            value.aniServerRules?.let { rule ->
                handleAniRule(rule, request)
            }?.let {
                return@intercept it
            }

            execute(request)
        }
    }

    /**
     * @return non-null if this rule is applied, which means further processing is NOT needed.
     * Returns `null` if the request does not match this rule, so further handling is needed.
     */
    private suspend fun Sender.handleAniRule(
        rule: ServerListFeatureConfig.AniServerRule,
        request: HttpRequestBuilder
    ): HttpClientCall? {
        if (rule.hostMatches.isEmpty() || rule.hostMatches.none { request.url.host.startsWith(it) }) {
            return null
        }

        val urls = aniServerUrls.first()
        if (urls.isEmpty()) {
            error("No server URL to try for ani server request")
        }

        // compute a signature for the current server list to know when to reset stickiness
        val signature = urls.joinToString("|") { it.toString() }.hashCode()

        // determine starting index using sticky selection
        val startIndex = selectionState.value
            .takeIf { it != null && it.signature == signature && it.index in urls.indices }
            ?.index ?: 0

        var lastCall: HttpClientCall? = null
        // iterate servers in cyclic order starting from startIndex
        for (i in urls.indices) {
            val index = (startIndex + i) % urls.size // 从上次成功的 index 开始
            val serverUrl = urls[index]
            replaceUrl(request.url, serverUrl)

            if (lastCall != null) { // 第二个请求了, 就开始打日志
                logger.debug { "Trying alternative server $serverUrl for request ${request.url}" }
            }
            val thisCall = try {
                execute(request) // if `expectSuccess` is true, this will throw on failure, otherwise return the call
            } catch (e: CancellationException) {
                throw e // don't prevent cancellation
            } catch (e: ClientRequestException) {
                // client error, don't retry
                throw e
            } catch (_: ResponseException) {
                continue // server error try next server
            } catch (_: IOException) {
                continue // try next server
            }
            lastCall = thisCall

            if (thisCall.response.status.value in 100..399) {
                // success: remember the index for next time
                selectionState.value = SelectionState(signature, index)
                return thisCall
            } else {
                // failed
                continue // try next server
            }
        }

        // all servers failed, return the last failure (for exception and logging)
        return lastCall ?: throw IOException(
            "All servers failed for request ${request.url}. Tried: " +
                    "\n${urls.joinToString("\n")}",
        )
    }

    private val logger = logger<ServerListFeatureHandler>()

    internal companion object {
        // for testing
        internal fun replaceUrl(urlBuilder: URLBuilder, newServerUrl: Url) {
            // Apply server URL to request
            urlBuilder.protocol = newServerUrl.protocol
            urlBuilder.host = newServerUrl.host
            urlBuilder.port = newServerUrl.port
            urlBuilder.encodedUser = newServerUrl.encodedUser
            urlBuilder.encodedPassword = newServerUrl.encodedPassword
        }
    }
}

// endregion


// region ConvertSendCountExceedExceptionFeature

val ConvertSendCountExceedExceptionFeature = ScopedHttpClientFeatureKey<Boolean>("ConvertSendCountExceedException")

data object ConvertSendCountExceedExceptionFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(
    ConvertSendCountExceedExceptionFeature,
) {
    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            try {
                execute(request)
            } catch (e: SendCountExceedException) {
                throw IOException("Send count exceeded for url ${request.url}, see cause", e)
            }
        }
    }
}

// endregion

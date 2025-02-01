/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.utils.coroutines.Symbol
import me.him188.ani.utils.ktor.userAgent
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

// region UseBangumiToken = ScopedHttpClientFeatureKey<Boolean>("UseBangumiToken")
val UseBangumiTokenFeature = ScopedHttpClientFeatureKey<Boolean>("UseBangumiToken")

class UseBangumiTokenFeatureHandler(
    private val bearerToken: Flow<String?>,
    private val onRefresh: suspend () -> BearerTokens?,
) : ScopedHttpClientFeatureHandler<Boolean>(UseBangumiTokenFeature) {

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

// endregion



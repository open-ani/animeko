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
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
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

abstract class ScopedHttpClientFeatureHandler<V>(
    val key: ScopedHttpClientFeatureKey<V>,
) {
    /**
     * Applies the feature to the [HttpClientConfig].
     */
    open fun apply(config: HttpClientConfig<*>, value: V) {}

    open fun apply(client: HttpClient, value: V) {}
}

fun <T> ScopedHttpClientFeatureKey<T>.withValue(value: T): ScopedHttpClientFeatureKeyValue<T> =
    ScopedHttpClientFeatureKeyValue.create(this, value)

@ConsistentCopyVisibility
data class ScopedHttpClientFeatureKeyValue<V> private constructor(
    val key: ScopedHttpClientFeatureKey<V>,
    internal val value: Any?,
) {
    companion object {
        fun <V> create(key: ScopedHttpClientFeatureKey<V>, value: V): ScopedHttpClientFeatureKeyValue<V> {
            require(value != FEATURE_NOT_SET) { "Value must not be FEATURE_NOT_SET" }
            return ScopedHttpClientFeatureKeyValue(key, value)
        }

        fun <V> createNotSet(key: ScopedHttpClientFeatureKey<V>): ScopedHttpClientFeatureKeyValue<V> {
            return ScopedHttpClientFeatureKeyValue(key, FEATURE_NOT_SET)
        }
    }
}

@JvmField
internal val FEATURE_NOT_SET = Symbol("NOT_REQUESTED")


// region UserAgent
val UserAgentFeature = ScopedHttpClientFeatureKey<ScopedHttpClientUserAgent>("UserAgent")

object UserAgentFeatureHandler : ScopedHttpClientFeatureHandler<ScopedHttpClientUserAgent>(UserAgentFeature) {
    override fun apply(config: HttpClientConfig<*>, value: ScopedHttpClientUserAgent) {
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
) : ScopedHttpClientFeatureHandler<Boolean>(UseBangumiTokenFeature) {
    override fun apply(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            if (!request.headers.contains(HttpHeaders.Authorization)) {
                bearerToken.first()?.let {
                    request.bearerAuth(it)
                }
            }
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



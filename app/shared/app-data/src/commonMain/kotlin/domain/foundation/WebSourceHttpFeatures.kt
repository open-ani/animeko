/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry

// region CookieJarFeature

/**
 * 在 [HttpClient] 构造时注入 [WebSourceCookieJar] 作为 ktor `HttpCookies` 的存储,
 * 取代旧版对 ktor 私有字段的反射写入.
 */
val CookieJarFeature = ScopedHttpClientFeatureKey<WebSourceCookieJar?>("WebSourceCookieJar")

object CookieJarFeatureHandler : ScopedHttpClientFeatureHandler<WebSourceCookieJar?>(CookieJarFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: WebSourceCookieJar?) {
        value ?: return
        config.install(HttpCookies) {
            storage = value
        }
    }
}

// endregion

// region WebSourceIdentityFeature

/**
 * Web 数据源的 User-Agent 对齐. 按优先级覆写请求的 `User-Agent`:
 * 1. 请求通过 [webSourceUserAgent] 指定的数据源 UA;
 * 2. 已 solve 的 host 记录的浏览器 UA.
 *
 * 数据源指定了 UA 时, 它的验证码浏览器也以同一 UA 创建, solve 后记录的就是这个 UA, 两者一致.
 *
 * @see WebSourceIdentityRegistry
 */
val WebSourceIdentityFeature = ScopedHttpClientFeatureKey<WebSourceIdentityRegistry?>("WebSourceIdentity")

object WebSourceIdentityFeatureHandler :
    ScopedHttpClientFeatureHandler<WebSourceIdentityRegistry?>(WebSourceIdentityFeature) {
    override fun applyToClient(client: HttpClient, value: WebSourceIdentityRegistry?) {
        value ?: return
        client.plugin(HttpSend).intercept { request ->
            (request.attributes.getOrNull(WebSourceUserAgentAttribute) ?: value.userAgentFor(request.url.host))?.let { ua ->
                request.headers[HttpHeaders.UserAgent] = ua
            }
            execute(request)
        }
    }
}

private val WebSourceUserAgentAttribute = AttributeKey<String>("WebSourceUserAgent")

/**
 * 以数据源配置的 [userAgent] 发出这个请求, 由 [WebSourceIdentityFeature] 写入请求头. `null` 时不做改变.
 *
 * 不能直接设置请求头: client 上的 UserAgent 插件会再追加一个值.
 */
fun HttpRequestBuilder.webSourceUserAgent(userAgent: String?) {
    if (userAgent != null) attributes.put(WebSourceUserAgentAttribute, userAgent)
}

// endregion

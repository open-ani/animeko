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
import io.ktor.http.HttpHeaders
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
 * per-host User-Agent 对齐: 对已 solve 的 host 覆写 `User-Agent` 为浏览器真实 UA.
 *
 * @see WebSourceIdentityRegistry
 */
val WebSourceIdentityFeature = ScopedHttpClientFeatureKey<WebSourceIdentityRegistry?>("WebSourceIdentity")

object WebSourceIdentityFeatureHandler :
    ScopedHttpClientFeatureHandler<WebSourceIdentityRegistry?>(WebSourceIdentityFeature) {
    override fun applyToClient(client: HttpClient, value: WebSourceIdentityRegistry?) {
        value ?: return
        client.plugin(HttpSend).intercept { request ->
            value.userAgentFor(request.url.host)?.let { ua ->
                request.headers[HttpHeaders.UserAgent] = ua
            }
            execute(request)
        }
    }
}

// endregion

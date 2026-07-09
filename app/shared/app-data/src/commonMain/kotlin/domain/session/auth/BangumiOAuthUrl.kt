/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import me.him188.ani.app.data.models.preference.DEFAULT_BANGUMI_WEB_BASE_URL

private val bangumiWebHosts = setOf("bgm.tv", "bangumi.tv")

fun normalizeBangumiWebBaseUrl(value: String): String? {
    val trimmed = value.trim().trimEnd('/')
    val withScheme = if (trimmed.isBlank()) {
        DEFAULT_BANGUMI_WEB_BASE_URL
    } else if ("://" in trimmed) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val parsed = runCatching { Url(withScheme) }.getOrNull() ?: return null
    if (parsed.protocol != URLProtocol.HTTPS && parsed.protocol != URLProtocol.HTTP) return null
    if (parsed.host.isBlank()) return null

    return URLBuilder().apply {
        protocol = parsed.protocol
        host = parsed.host
        port = parsed.port
    }.buildString().trimEnd('/')
}

fun rewriteBangumiOAuthUrl(url: String, bangumiWebBaseUrl: String): String {
    val target = normalizeBangumiWebBaseUrl(bangumiWebBaseUrl) ?: return url
    val original = runCatching { Url(url) }.getOrNull() ?: return url
    if (original.host.lowercase() !in bangumiWebHosts) return url

    val targetUrl = Url(target)
    return URLBuilder(original).apply {
        protocol = targetUrl.protocol
        host = targetUrl.host
        port = targetUrl.port
        encodedUser = null
        encodedPassword = null
    }.buildString()
}

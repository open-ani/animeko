/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom

object UrlHelpers {
    fun computeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        require(baseUrl.isNotEmpty()) { "baseUrl must not be empty" }
        return URLBuilder(baseUrl).apply {
            if (relativeUrl.isNotBlank()) {
                encodedParameters = ParametersBuilder()
                encodedFragment = ""
            }
        }.takeFrom(relativeUrl).buildString()
    }

    /**
     * Computes an absolute URL, or returns `null` when either URL cannot be parsed.
     */
    fun computeAbsoluteUrlOrNull(baseUrl: String, relativeUrl: String): String? {
        return try {
            computeAbsoluteUrl(baseUrl, relativeUrl)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 与浏览器一致: 站点常把 https 页面的链接硬编码为绝对 http (浏览器会经 301 跳转).
     * 同 host (允许 `www.` 差异) 的默认端口 http 链接升级为 https, 其余原样返回.
     */
    fun computeSelectorUrl(baseUrl: String, rawUrl: String): String {
        val absolute = computeAbsoluteUrl(baseUrl, rawUrl)
        val base = runCatching { Url(baseUrl) }.getOrNull() ?: return absolute
        if (base.protocol != URLProtocol.HTTPS) return absolute
        val target = runCatching { Url(absolute) }.getOrNull() ?: return absolute
        if (target.protocol != URLProtocol.HTTP) return absolute
        if (!isSameHost(base.host, target.host)) return absolute
        if (target.port != URLProtocol.HTTP.defaultPort) return absolute
        return URLBuilder(target).apply {
            protocol = URLProtocol.HTTPS
            port = URLProtocol.HTTPS.defaultPort
        }.buildString()
    }

    private fun isSameHost(a: String, b: String): Boolean {
        fun normalize(host: String) = host.removePrefix("www.").lowercase()
        return normalize(a) == normalize(b)
    }
}

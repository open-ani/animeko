/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
import io.ktor.http.Cookie
import io.ktor.http.Url
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi

@OptIn(UnsafeScopedHttpClientApi::class)
internal actual suspend fun storeCaptchaCookies(
    client: ScopedHttpClient,
    pageUrl: String,
    cookies: List<String>,
) {
    val ticket = client.borrow()
    try {
        val plugin = runCatching { ticket.client.plugin(HttpCookies) }.getOrNull() ?: return
        val storageField = HttpCookies::class.java.getDeclaredField("storage").apply {
            isAccessible = true
        }
        val storage = storageField.get(plugin) as CookiesStorage
        val url = Url(pageUrl)
        for (cookie in cookies) {
            val name = cookie.substringBefore("=").trim()
            val value = cookie.substringAfter("=", missingDelimiterValue = "").trim()
            if (name.isBlank() || value.isBlank()) continue
            storage.addCookie(url, Cookie(name, value))
        }
    } finally {
        client.returnClient(ticket)
    }
}

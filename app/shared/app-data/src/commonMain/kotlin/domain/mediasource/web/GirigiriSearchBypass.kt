/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * This source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Uses girigiri's public VOD API when its HTML search entry point is protected by a verification page.
 *
 * The API response is converted to the HTML shape used by the existing girigiri selector subscription, so this
 * workaround does not require a site-specific option in [SelectorSearchConfig].
 */
internal class GirigiriSearchBypass private constructor(
    val requestUrl: Url,
) {
    fun configureRequest(builder: HttpRequestBuilder) {
        builder.header(HttpHeaders.UserAgent, USER_AGENT)
        builder.header(HttpHeaders.Referrer, REFERER)
        builder.header(HttpHeaders.Accept, ACCEPT)
    }

    fun adaptResponse(body: String): String? {
        val list = runCatching {
            Json.parseToJsonElement(body).jsonObject["list"]?.jsonArray
        }.getOrNull() ?: return null

        return buildString {
            append("<html><body><div class=\"box-width\">")
            for (item in list) {
                val fields = runCatching { item.jsonObject }.getOrNull() ?: continue
                val id = fields.stringValue("vod_id")?.takeIf { it.isNotBlank() } ?: continue
                val name = fields.stringValue("vod_name")?.takeIf { it.isNotBlank() } ?: continue
                append("<div class=\"vod-detail\"><div class=\"detail-info\">")
                append("<a href=\"/GV")
                append(id.escapeHtmlAttribute())
                append("/\"><span class=\"slide-info-title\">")
                append(name.escapeHtmlText())
                append("</span></a></div></div>")
            }
            append("</div></body></html>")
        }
    }

    companion object {
        private const val API_URL = "https://m3u8.girigirilove.com/api.php/provide/vod/"
        private const val USER_AGENT = "Girigiri/1.0 (https://github.com/MareDevi/girigiri)"
        private const val REFERER = "https://bgm.girigirilove.com/"
        private const val ACCEPT = "application/json, text/plain, */*"

        private val supportedHosts = setOf(
            "ani.girigirilove.com",
            "anime.girigirilove.com",
            "bgm.girigirilove.com",
        )

        fun create(searchUrl: Url): GirigiriSearchBypass? {
            if (searchUrl.host.lowercase() !in supportedHosts) return null
            val keyword = searchUrl.parameters["wd"]?.takeIf { it.isNotBlank() } ?: return null
            val requestUrl = URLBuilder(API_URL).apply {
                parameters.append("ac", "detail")
                parameters.append("wd", keyword)
            }.build()
            return GirigiriSearchBypass(requestUrl)
        }
    }
}

private fun JsonObject.stringValue(key: String): String? {
    return runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
}

private fun String.escapeHtmlText(): String = buildString(length) {
    for (character in this@escapeHtmlText) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(character)
        }
    }
}

private fun String.escapeHtmlAttribute(): String = escapeHtmlText()
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

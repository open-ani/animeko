/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * This source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * Girigiri 的 HTML 搜索入口被验证页拦截时, 改走其公开 VOD API.
 *
 * API 响应会转换为现有 Girigiri selector 能解析的 HTML 形状, 再交给 [PageEvaluator] 判定;
 * 路由不适用或请求失败时返回 `null`, 由 [WebSessionManager] 回落到普通网页与验证码链路.
 */
class GirigiriSearchRoute(
    private val evaluator: PageEvaluator,
) : SearchRoute {
    override val id: String = "girigiri-vod-api"

    override fun matches(host: String): Boolean = host.lowercase() in SUPPORTED_HOSTS

    override suspend fun <T> fetch(
        url: String,
        expectation: PageExpectation<T>,
        http: ScopedHttpClient,
    ): PageVerdict<T>? {
        if (expectation !is PageExpectation.SearchResults) return null
        val searchUrl = runCatching { Url(url) }.getOrNull() ?: return null
        val keyword = searchUrl.parameters["wd"]?.takeIf { it.isNotBlank() } ?: return null
        val requestUrl = URLBuilder(API_URL).apply {
            parameters.append("ac", "detail")
            parameters.append("wd", keyword)
        }.build()

        return try {
            http.use {
                prepareGet(requestUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Referrer, REFERER)
                    header(HttpHeaders.Accept, ACCEPT)
                }.execute { response ->
                    if (response.status.value !in 200..299) return@execute null
                    val html = adaptResponse(response.bodyAsText()) ?: return@execute null
                    evaluator.evaluate(
                        LoadedPage(
                            finalUrl = searchUrl.toString(),
                            html = html,
                            status = response.status.value,
                        ),
                        expectation,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun adaptResponse(body: String): String? {
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

    private companion object {
        const val API_URL = "https://m3u8.girigirilove.com/api.php/provide/vod/"
        const val USER_AGENT = "Girigiri/1.0 (https://github.com/MareDevi/girigiri)"
        const val REFERER = "https://bgm.girigirilove.com/"
        const val ACCEPT = "application/json, text/plain, */*"

        val SUPPORTED_HOSTS = setOf(
            "ani.girigirilove.com",
            "anime.girigirilove.com",
            "bgm.girigirilove.com",
        )
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

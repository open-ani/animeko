/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.net.ssl.SSLHandshakeException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class HandshakeFailureDomainAdvisor(
    private val httpClient: HttpClient,
) {
    suspend fun analyzeIfNeeded(
        sourceName: String,
        mediaSourceSpec: MediaSourceSpec?,
        exception: Throwable,
    ): HandshakeFailureDomainHint? {
        if (!isHandshakeFailure(exception)) {
            return null
        }

        val searchUrl = extractSearchUrl(mediaSourceSpec) ?: return null
        val currentHost = parseHost(searchUrl) ?: return null
        val hostToken = currentHost.substringBefore('.')
        val queries = listOf(
            "$sourceName 最新域名",
            "$sourceName $hostToken",
        )

        return runCatching {
            val suggestions = queries
                .flatMap { query -> fetchSearchResults(query) }
                .filter { candidate ->
                    val candidateHost = normalizeHost(candidate.host)
                    candidateHost.isNotBlank() &&
                            candidateHost != currentHost &&
                            EXCLUDED_HOSTS.none { candidateHost == it || candidateHost.endsWith(".$it") } &&
                            matchesSource(candidate, sourceName, hostToken)
                }
                .distinctBy { normalizeHost(it.host) }
                .take(MAX_SUGGESTIONS)

            HandshakeFailureDomainHint(
                currentHost = currentHost,
                searchProvider = "bing-rss",
                queries = queries,
                suggestions = suggestions,
                summary = if (suggestions.isEmpty()) {
                    "Handshake failure detected, but no alternative host hint was found"
                } else {
                    "Handshake failure detected. Possible replacement hosts: ${
                        suggestions.joinToString { it.host }
                    }"
                },
            )
        }.getOrElse { searchError ->
            HandshakeFailureDomainHint(
                currentHost = currentHost,
                searchProvider = "bing-rss",
                queries = queries,
                suggestions = emptyList(),
                summary = "Handshake failure detected, but the domain search failed",
                searchError = "${searchError::class.simpleName}: ${searchError.message.orEmpty()}",
            )
        }
    }

    private suspend fun fetchSearchResults(query: String): List<DomainSearchSuggestion> {
        val response = httpClient.get(BING_SEARCH_URL) {
            parameter("format", "rss")
            parameter("q", query)
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Bing RSS lookup failed: ${response.status.value} ${response.status.description}"
        }
        return parseRss(body)
    }

    private fun parseRss(xml: String): List<DomainSearchSuggestion> {
        val builderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        val document = builderFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = document.getElementsByTagName("item")
        return buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val title = element.childText("title")
                val url = element.childText("link")
                val description = element.childText("description")
                val host = parseHost(url) ?: continue
                add(
                    DomainSearchSuggestion(
                        title = title,
                        url = url,
                        host = host,
                        description = description,
                    ),
                )
            }
        }
    }

    private fun matchesSource(
        candidate: DomainSearchSuggestion,
        sourceName: String,
        hostToken: String,
    ): Boolean {
        val haystack = buildString {
            append(candidate.title)
            append('\n')
            append(candidate.description)
            append('\n')
            append(candidate.url)
        }.lowercase()
        return haystack.contains(sourceName.lowercase()) || haystack.contains(hostToken.lowercase())
    }

    private fun parseHost(url: String): String? {
        val sanitizedUrl = url.replace(PLACEHOLDER_REGEX, "keyword")
        val rawHost = runCatching { URI(sanitizedUrl).host }.getOrNull()?.lowercase() ?: return null
        return normalizeHost(rawHost)
    }

    private fun extractSearchUrl(mediaSourceSpec: MediaSourceSpec?): String? {
        val serializedArguments = mediaSourceSpec?.serializedArguments?.jsonObject ?: return null
        val searchConfig = serializedArguments["searchConfig"]?.jsonObject ?: return null
        return searchConfig["searchUrl"]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val BING_SEARCH_URL = "https://www.bing.com/search"
        private const val MAX_SUGGESTIONS = 5
        private val PLACEHOLDER_REGEX = Regex("\\{[^}]+}")

        private val EXCLUDED_HOSTS = setOf(
            "bing.com",
            "zhihu.com",
            "bilibili.com",
            "weibo.com",
            "baidu.com",
            "youtube.com",
            "x.com",
            "twitter.com",
            "sohu.com",
            "163.com",
        )

        fun isHandshakeFailure(exception: Throwable): Boolean {
            return generateSequence(exception) { it.cause }.any { current ->
                current is SSLHandshakeException ||
                        current.message.orEmpty().contains("handshake", ignoreCase = true)
            }
        }

        private fun normalizeHost(host: String): String {
            return host.removePrefix("www.").trim()
        }
    }
}

private fun Element.childText(name: String): String {
    return getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
}

@Serializable
data class HandshakeFailureDomainHint(
    val currentHost: String,
    val searchProvider: String,
    val queries: List<String>,
    val suggestions: List<DomainSearchSuggestion>,
    val summary: String,
    val searchError: String? = null,
)

@Serializable
data class DomainSearchSuggestion(
    val title: String,
    val url: String,
    val host: String,
    val description: String,
)

/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.webdav

import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodedPath
import kotlin.io.encoding.Base64
import kotlinx.io.IOException
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.xml.Element
import me.him188.ani.utils.xml.Xml

internal data class WebDavEntry(
    val url: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
) {
    val isVideo: Boolean
        get() = !isDirectory && name.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts", "mpg", "mpeg")
    }
}

/** Depth 1 目录请求只接受配置根目录内的直接子项，路径与资源标识使用统一的百分号编码。 */
internal class WebDavClient(
    baseUrl: String,
    username: String,
    password: String,
    private val client: ScopedHttpClient,
) {
    val root: Url = parseRoot(baseUrl)
    val location: MediaSourceLocation = if (isLanHost(root.host)) {
        MediaSourceLocation.Lan
    } else {
        MediaSourceLocation.Online
    }
    val playbackHeaders: Map<String, String> = if (username.isEmpty()) {
        emptyMap()
    } else {
        mapOf(HttpHeaders.Authorization to "Basic ${Base64.encode("$username:$password".encodeToByteArray())}")
    }

    fun requireOwnedUrl(text: String): Url {
        val url = Url(text)
        require(sameOrigin(root, url) && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) {
            "WebDAV URL must belong to the configured server"
        }
        val path = canonicalPath(url.encodedPath)
        require(path.startsWith(root.encodedPath) && url.encodedQuery.isEmpty() && url.fragment.isEmpty()) {
            "WebDAV URL must be inside the configured root directory"
        }
        return URLBuilder(url).apply { encodedPath = path }.build()
    }

    suspend fun list(folderUrl: String): List<WebDavEntry> {
        val folder = requireOwnedUrl(folderUrl)
        require(folder.encodedPath.endsWith('/')) { "WebDAV directory URL must end with a slash" }
        return client.use {
            val response = request(folder) {
                method = HttpMethod("PROPFIND")
                timeout { requestTimeoutMillis = 30_000 }
                header("Depth", "1")
                playbackHeaders.forEach { (name, value) -> header(name, value) }
                contentType(ContentType.Application.Xml)
                setBody(PROPFIND_BODY)
            }
            if (response.status.value !in 200..299) {
                throw IOException("WebDAV directory request failed with HTTP ${response.status.value}")
            }
            parseMultistatus(response.bodyAsText(), folder)
        }
    }

    internal fun parseMultistatus(xml: String, folder: Url): List<WebDavEntry> {
        val document = Xml.parse(xml)
        require(document.getAllElements().any { it.localName == "multistatus" }) {
            "The server did not return a WebDAV multistatus response"
        }
        return document.getAllElements().filter { it.localName == "response" }.mapNotNull { response ->
            val href = response.children().firstOrNull { it.localName == "href" }?.text()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = resolveHref(href, folder) ?: return@mapNotNull null
            val relative = url.encodedPath.removePrefix(folder.encodedPath).trimEnd('/')
            if (relative.isEmpty() || '/' in relative) return@mapNotNull null
            val properties = response.children().filter { it.localName == "propstat" }
                .filter { propstat ->
                    val status = propstat.children().firstOrNull { it.localName == "status" }?.text().orEmpty()
                    status.isBlank() || status.trim().split(Regex("\\s+")).getOrNull(1) == "200"
                }
                .flatMap { it.children().filter { prop -> prop.localName == "prop" }.flatMap { prop -> prop.children() } }
            if (properties.isEmpty()) return@mapNotNull null
            val resourceType = properties.firstOrNull { it.localName == "resourcetype" }
            val isDirectory = resourceType?.getAllElements()?.any { it.localName == "collection" }
                ?: url.encodedPath.endsWith('/')
            val path = url.encodedPath.trimEnd('/') + if (isDirectory) "/" else ""
            WebDavEntry(
                url = URLBuilder(url).apply { encodedPath = path }.buildString(),
                name = relative.decodeURLPart(),
                isDirectory = isDirectory,
                size = properties.firstOrNull { it.localName == "getcontentlength" }
                    ?.text()?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
            )
        }.distinctBy { it.url }
    }

    private fun resolveHref(href: String, folder: Url): Url? {
        return try {
            val url = when {
                href.startsWith("http://", true) || href.startsWith("https://", true) -> requireOwnedUrl(href)
                href.startsWith("/") -> requireOwnedUrl(URLBuilder(root).apply { encodedPath = href }.buildString())
                else -> requireOwnedUrl(URLBuilder(folder).apply { encodedPath += href }.buildString())
            }
            url.takeIf { it.encodedPath.startsWith(folder.encodedPath) }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>""" +
                """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""

        fun parseRoot(text: String): Url {
            val url = Url(text.trim())
            require(url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS) {
                "WebDAV server URL must use http or https"
            }
            require(url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
                    url.encodedQuery.isEmpty() && url.fragment.isEmpty()) {
                "WebDAV server URL must contain a host and directory path without credentials or query parameters"
            }
            return URLBuilder(url).apply { encodedPath = canonicalPath(url.encodedPath).trimEnd('/') + "/" }.build()
        }

        private fun sameOrigin(first: Url, second: Url): Boolean =
            first.protocol == second.protocol && first.host.equals(second.host, true) && first.port == second.port

        private fun canonicalPath(path: String): String = path.split('/').joinToString("/") { segment ->
            val decoded = segment.decodeURLPart()
            require(decoded != "." && decoded != ".." && '/' !in decoded && '\\' !in decoded) {
                "WebDAV path must not contain traversal segments"
            }
            decoded.encodeURLPathPart()
        }

        private fun isLanHost(host: String): Boolean {
            val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
            if (normalized.isEmpty() || normalized == "localhost") return true
            val parts = normalized.split('.')
            if (parts.size == 4 && parts.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }) {
                val first = parts[0].toInt()
                val second = parts[1].toInt()
                return first == 10 || first == 127 || (first == 172 && second in 16..31) ||
                        (first == 192 && second == 168) || (first == 169 && second == 254)
            }
            if (':' in normalized) {
                if (normalized == "::1") return true
                val first = normalized.substringBefore(':').toIntOrNull(16) ?: return false
                return (first and 0xfe00) == 0xfc00 || (first and 0xffc0) == 0xfe80
            }
            return '.' !in normalized || LAN_SUFFIXES.any { normalized.endsWith(it) }
        }

        private val LAN_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".localdomain", ".home.arpa")
    }
}

private val Element.localName: String get() = tagName().substringAfter(':').lowercase()

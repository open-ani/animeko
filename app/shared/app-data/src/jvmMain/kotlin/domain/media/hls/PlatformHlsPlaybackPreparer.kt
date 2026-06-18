/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.UriMediaData
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

class PlatformHlsPlaybackPreparer(
    private val httpClientProvider: HttpClientProvider,
) : HlsPlaybackPreparer {
    override suspend fun prepare(data: UriMediaData): HlsPlaybackPreparerResult {
        if (!data.uri.isCandidateHlsUri()) {
            return HlsPlaybackPreparerResult(data)
        }

        val requestedUri = runCatching { URI(data.uri) }.getOrNull() ?: return HlsPlaybackPreparerResult(data)
        var baseUri = requestedUri
        val manifest = try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }
                baseUri = runCatching { URI(response.call.request.url.toString()) }.getOrDefault(requestedUri)
                response.bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return HlsPlaybackPreparerResult(data)
        }

        val filterResult = HlsManifestFilter.filter(manifest, baseUri.toString())
        val session = when {
            filterResult.status == HlsManifestFilterStatus.Filtered -> {
                LocalHlsPlaylistSession.static(filterResult.content.rewriteMediaPlaylistUris(baseUri))
            }

            filterResult.status == HlsManifestFilterStatus.Unsupported &&
                filterResult.reason == "master_playlist" -> {
                LocalHlsPlaylistSession.master(
                    content = manifest,
                    baseUri = baseUri,
                    headers = data.headers,
                    httpClientProvider = httpClientProvider,
                )
            }

            else -> return HlsPlaybackPreparerResult(data)
        }

        return HlsPlaybackPreparerResult(
            data = UriMediaData(session.playlistUri, data.headers, data.extraFiles),
            session = session,
        )
    }
}

private class LocalHlsPlaylistSession(
    initialPlaylistContent: LocalPlaylistContent,
    rewriteInitialPlaylist: Boolean,
    private val headers: Map<String, String>,
    private val httpClientProvider: HttpClientProvider?,
) : HlsPlaybackProxySession {
    private val closed = AtomicBoolean(false)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val nextRouteId = AtomicInteger(1)
    private val remoteRoutes = ConcurrentHashMap<String, URI>()
    private val initialContent = if (rewriteInitialPlaylist) {
        initialPlaylistContent.rewriteMasterPlaylistUrisIfNeeded()
    } else {
        initialPlaylistContent
    }

    val playlistUri: String = "http://127.0.0.1:${serverSocket.localPort}/playlist.m3u8"

    private val thread = thread(
        name = "HlsPlaylistProxy-${serverSocket.localPort}",
        isDaemon = true,
        start = true,
    ) {
        while (!closed.get()) {
            try {
                serverSocket.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    val requestLine = reader.readLine()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val path = requestLine
                        ?.substringAfter(" ", missingDelimiterValue = "")
                        ?.substringBefore(" ", missingDelimiterValue = "")
                        ?.substringBefore("?")

                    val content = runCatching {
                        contentFor(path ?: "/playlist.m3u8")
                    }.getOrElse { e ->
                        logger.warn(e) { "Failed to prepare HLS playlist proxy response" }
                        null
                    }

                    socket.getOutputStream().use { output ->
                        if (content == null) {
                            output.write(errorResponseHeader().toByteArray(StandardCharsets.US_ASCII))
                        } else {
                            val bytes = content.toByteArray(StandardCharsets.UTF_8)
                            output.write(responseHeader(bytes.size).toByteArray(StandardCharsets.US_ASCII))
                            output.write(bytes)
                        }
                        output.flush()
                    }
                }
            } catch (e: SocketException) {
                if (!closed.get()) {
                    logger.warn(e) { "Failed to serve HLS playlist request" }
                }
            } catch (e: IOException) {
                logger.warn(e) { "Failed to serve HLS playlist request" }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            serverSocket.close()
        }
    }

    @Suppress("unused")
    private fun keepThreadReachable(): Thread = thread

    private fun contentFor(path: String): String? {
        if (path == "/playlist.m3u8") {
            return initialContent.content
        }
        val remoteUri = remoteRoutes[path] ?: return null
        val remoteContent = fetchRemote(remoteUri) ?: return null
        return remoteContent.rewriteMasterPlaylistUrisIfNeeded().content
    }

    private fun fetchRemote(uri: URI): LocalPlaylistContent? {
        val provider = httpClientProvider ?: return null
        return runBlocking {
            provider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(uri.toString()) {
                    this@LocalHlsPlaylistSession.headers.forEach { (name, value) -> header(name, value) }
                }
                val finalUri = runCatching { URI(response.call.request.url.toString()) }.getOrDefault(uri)
                LocalPlaylistContent(response.bodyAsText(), finalUri)
            }
        }
    }

    private fun LocalPlaylistContent.rewriteMasterPlaylistUrisIfNeeded(): LocalPlaylistContent {
        val filterResult = HlsManifestFilter.filter(content, baseUri.toString())
        val rewrittenContent = when {
            filterResult.status == HlsManifestFilterStatus.Filtered -> {
                filterResult.content.rewriteMediaPlaylistUris(baseUri)
            }

            filterResult.status == HlsManifestFilterStatus.Unsupported &&
                filterResult.reason == "master_playlist" -> {
                content.rewriteMasterPlaylistUris(baseUri)
            }

            else -> {
                content.rewriteMediaPlaylistUris(baseUri)
            }
        }
        return copy(content = rewrittenContent)
    }

    private fun String.rewriteMasterPlaylistUris(baseUri: URI): String {
        return lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = baseUri.resolveIfRelative(match.groupValues[2])
                        match.groupValues[1] + localPlaylistUri(uri) + match.groupValues[3]
                    }
                }

                line.startsWith("#") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = baseUri.resolveIfRelative(match.groupValues[2])
                        match.groupValues[1] + uri + match.groupValues[3]
                    }
                }

                line.isBlank() -> line
                else -> localPlaylistUri(baseUri.resolveIfRelative(line))
            }
        } + if (endsWith('\n')) "\n" else ""
    }

    private fun localPlaylistUri(remoteUri: String): String {
        val route = "/playlist/${nextRouteId.getAndIncrement()}.m3u8"
        remoteRoutes[route] = URI(remoteUri)
        return "http://127.0.0.1:${serverSocket.localPort}$route"
    }

    private fun responseHeader(contentLength: Int): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/vnd.apple.mpegurl; charset=utf-8\r\n")
            append("Content-Length: ").append(contentLength).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    private fun errorResponseHeader(): String {
        return buildString {
            append("HTTP/1.1 502 Bad Gateway\r\n")
            append("Content-Length: 0\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    companion object {
        fun static(content: String): LocalHlsPlaylistSession {
            return LocalHlsPlaylistSession(
                initialPlaylistContent = LocalPlaylistContent(content, URI("http://127.0.0.1/")),
                rewriteInitialPlaylist = false,
                headers = emptyMap(),
                httpClientProvider = null,
            )
        }

        fun master(
            content: String,
            baseUri: URI,
            headers: Map<String, String>,
            httpClientProvider: HttpClientProvider,
        ): LocalHlsPlaylistSession {
            return LocalHlsPlaylistSession(
                initialPlaylistContent = LocalPlaylistContent(content, baseUri),
                rewriteInitialPlaylist = true,
                headers = headers,
                httpClientProvider = httpClientProvider,
            )
        }
    }
}

private val logger = me.him188.ani.utils.logging.logger<PlatformHlsPlaybackPreparer>()

private data class LocalPlaylistContent(
    val content: String,
    val baseUri: URI,
)

private fun String.isCandidateHlsUri(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && lowercase().contains(".m3u8")
}

private fun String.rewriteMediaPlaylistUris(baseUri: URI): String {
    return lineSequence().joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.startsWith("#") -> {
                line.replace(URI_ATTRIBUTE_REGEX) { match ->
                    val uri = match.groupValues[2]
                    match.groupValues[1] + baseUri.resolveIfRelative(uri) + match.groupValues[3]
                }
            }
            else -> baseUri.resolveIfRelative(line)
        }
    } + if (endsWith('\n')) "\n" else ""
}

private fun URI.resolveIfRelative(uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    return if (parsed.isAbsolute) uri else resolve(parsed).toString()
}

private val URI_ATTRIBUTE_REGEX = Regex("""(URI=")([^"]+)(")""")

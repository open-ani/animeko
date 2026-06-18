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
import java.util.concurrent.atomic.AtomicBoolean
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

        val filterResult = HlsManifestFilter.filter(manifest)
        if (filterResult.status != HlsManifestFilterStatus.Filtered) {
            return HlsPlaybackPreparerResult(data)
        }

        val rewrittenManifest = filterResult.content.rewriteRelativeUris(baseUri)
        val session = LocalHlsPlaylistSession(rewrittenManifest)
        return HlsPlaybackPreparerResult(
            data = UriMediaData(session.playlistUri, data.headers, data.extraFiles),
            session = session,
        )
    }
}

private class LocalHlsPlaylistSession(
    content: String,
) : HlsPlaybackProxySession {
    private val closed = AtomicBoolean(false)
    private val bytes = content.toByteArray(StandardCharsets.UTF_8)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

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
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().use { output ->
                        output.write(responseHeader(bytes.size).toByteArray(StandardCharsets.US_ASCII))
                        output.write(bytes)
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
}

private val logger = me.him188.ani.utils.logging.logger<PlatformHlsPlaybackPreparer>()

private fun String.isCandidateHlsUri(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && lowercase().contains(".m3u8")
}

private fun String.rewriteRelativeUris(baseUri: URI): String {
    return lineSequence().joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP") -> {
                line.replace(URI_ATTRIBUTE_REGEX) { match ->
                    val uri = match.groupValues[2]
                    match.groupValues[1] + baseUri.resolveIfRelative(uri) + match.groupValues[3]
                }
            }
            line.startsWith("#") -> line
            else -> baseUri.resolveIfRelative(line)
        }
    } + if (endsWith('\n')) "\n" else ""
}

private fun URI.resolveIfRelative(uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    return if (parsed.isAbsolute) uri else resolve(parsed).toString()
}

private val URI_ATTRIBUTE_REGEX = Regex("""(URI=")([^"]+)(")""")

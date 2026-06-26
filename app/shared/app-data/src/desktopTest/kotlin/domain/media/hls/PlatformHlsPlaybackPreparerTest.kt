/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PlatformHlsPlaybackPreparerTest {
    @Test
    fun `filters playlist and serves rewritten local manifest`() = runTest {
        val server = StaticManifestServer(manifest)
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)

        val result = preparer.prepare(
            UriMediaData(
                uri = "${server.baseUrl}/anime/01/index.m3u8",
                headers = mapOf("Referer" to "https://media.example.com/watch/01"),
            ),
        )

        try {
            assertEquals(listOf("https://media.example.com/watch/01"), server.referers)
            assertNotEquals("${server.baseUrl}/anime/01/index.m3u8", result.data.uri)
            assertEquals("https://media.example.com/watch/01", result.data.headers["Referer"])
            assertIs<HlsPlaybackProxySession>(result.session)

            val localManifest = URI(result.data.uri).toURL().readText()
            assertContains(localManifest, "#EXT-X-KEY:METHOD=AES-128,URI=\"${server.baseUrl}/anime/01/keys/main.key\"")
            assertContains(localManifest, "${server.baseUrl}/anime/01/main000.ts")
            assertContains(localManifest, "https://cdn.example.com/main003.ts")
            assertContains(localManifest, "${server.baseUrl}/anime/01/main004.ts")
            assertEquals(false, "ad001.ts" in localManifest)
            assertEquals(false, "ad002.ts" in localManifest)
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `rewrites relative urls against redirected manifest url`() = runTest {
        val server = StaticManifestServer(
            manifest,
            redirectFrom = "/entry/index.m3u8",
            redirectTo = "/cdn/final/index.m3u8",
        )
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)

        val result = preparer.prepare(UriMediaData("${server.baseUrl}/entry/index.m3u8"))

        try {
            assertIs<HlsPlaybackProxySession>(result.session)

            val localManifest = URI(result.data.uri).toURL().readText()
            assertContains(localManifest, "#EXT-X-KEY:METHOD=AES-128,URI=\"${server.baseUrl}/cdn/final/keys/main.key\"")
            assertContains(localManifest, "${server.baseUrl}/cdn/final/main000.ts")
            assertEquals(false, "${server.baseUrl}/entry/main000.ts" in localManifest)
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `proxies master playlist and filters variant media playlist`() = runTest {
        val server = StaticManifestServer(
            content = "",
            contentByPath = mapOf(
                "/master/index.m3u8" to masterManifest,
                "/master/media/low.m3u8" to manifest,
            ),
        )
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)

        val result = preparer.prepare(
            UriMediaData(
                uri = "${server.baseUrl}/master/index.m3u8",
                headers = mapOf("Referer" to "https://media.example.com/watch/master"),
            ),
        )

        try {
            assertIs<HlsPlaybackProxySession>(result.session)
            assertNotEquals("${server.baseUrl}/master/index.m3u8", result.data.uri)

            val localMaster = URI(result.data.uri).toURL().readText()
            val localVariantUri = localMaster.lineSequence()
                .first { it.isNotBlank() && !it.startsWith("#") }
            assertContains(localVariantUri, "http://127.0.0.1:")
            assertContains(localMaster, "#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"${server.baseUrl}/master/keys/session.key\"")
            assertEquals(false, "media/low.m3u8" in localMaster)
            assertEquals(false, "URI=\"keys/session.key\"" in localMaster)

            val localVariant = URI(localVariantUri).toURL().readText()
            assertContains(localVariant, "${server.baseUrl}/master/media/main000.ts")
            assertContains(localVariant, "${server.baseUrl}/master/media/main004.ts")
            assertEquals(false, "ad001.ts" in localVariant)
            assertEquals(false, "ad002.ts" in localVariant)
            assertEquals(
                listOf("https://media.example.com/watch/master", "https://media.example.com/watch/master"),
                server.referers,
            )
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `rewrites media playlist uri attributes when proxying master variant`() = runTest {
        val server = StaticManifestServer(
            content = "",
            contentByPath = mapOf(
                "/partial/master.m3u8" to partialMasterManifest,
                "/partial/low/index.m3u8" to partialMediaManifest,
            ),
        )
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)

        val result = preparer.prepare(UriMediaData("${server.baseUrl}/partial/master.m3u8"))

        try {
            assertIs<HlsPlaybackProxySession>(result.session)

            val localMaster = URI(result.data.uri).toURL().readText()
            val localVariantUri = localMaster.lineSequence()
                .first { it.isNotBlank() && !it.startsWith("#") }
            val localVariant = URI(localVariantUri).toURL().readText()

            assertContains(localVariant, "#EXT-X-PART:DURATION=1.0,URI=\"${server.baseUrl}/partial/low/part0.m4s\"")
            assertContains(
                localVariant,
                "#EXT-X-PRELOAD-HINT:TYPE=PART,URI=\"${server.baseUrl}/partial/low/next.m4s\"",
            )
            assertContains(localVariant, "${server.baseUrl}/partial/low/seg0.ts")
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `does not filter direct media playlist twice`() = runTest {
        val firstPass = HlsManifestFilter.filter(doubleFilterRegressionManifest)
        assertEquals(HlsManifestFilterStatus.Filtered, firstPass.status)
        assertEquals(1, firstPass.removedGroups.size)
        assertContains(firstPass.content, "short-normal000.ts")

        val server = StaticManifestServer(doubleFilterRegressionManifest)
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)

        val result = preparer.prepare(UriMediaData("${server.baseUrl}/dense/index.m3u8"))

        try {
            assertIs<HlsPlaybackProxySession>(result.session)

            val localManifest = URI(result.data.uri).toURL().readText()
            assertContains(localManifest, "${server.baseUrl}/dense/short-normal000.ts")
            assertContains(localManifest, "${server.baseUrl}/dense/short-normal003.ts")
            assertEquals(false, "ad-double000.ts" in localManifest)
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    private class StaticManifestServer(
        content: String,
        private val contentByPath: Map<String, String> = emptyMap(),
        private val redirectFrom: String? = null,
        private val redirectTo: String? = null,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val bytes = content.toByteArray(StandardCharsets.UTF_8)
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val mutableReferers = mutableListOf<String?>()

        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"
        val referers: List<String?> get() = mutableReferers.toList()

        private val thread = thread(
            name = "PlatformHlsPlaybackPreparerTest-${serverSocket.localPort}",
            isDaemon = true,
            start = true,
        ) {
            while (!closed.get()) {
                try {
                    serverSocket.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                        val lines = buildList {
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                add(line)
                            }
                        }
                        mutableReferers += lines
                            .firstOrNull { it.startsWith("Referer:", ignoreCase = true) }
                            ?.substringAfter(":")
                            ?.trim()

                        socket.getOutputStream().use { output ->
                            val requestPath = lines.firstOrNull()
                                ?.substringAfter(" ")
                                ?.substringBefore(" ")
                            if (requestPath == redirectFrom && redirectTo != null) {
                                output.write(redirectHeader("$baseUrl$redirectTo").toByteArray(StandardCharsets.US_ASCII))
                            } else {
                                val responseBytes = contentByPath[requestPath]
                                    ?.toByteArray(StandardCharsets.UTF_8)
                                    ?: bytes
                                output.write(responseHeader(responseBytes.size).toByteArray(StandardCharsets.US_ASCII))
                                output.write(responseBytes)
                            }
                            output.flush()
                        }
                    }
                } catch (e: SocketException) {
                    if (!closed.get()) throw e
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
                append("Connection: close\r\n")
                append("\r\n")
            }
        }

        private fun redirectHeader(location: String): String {
            return buildString {
                append("HTTP/1.1 302 Found\r\n")
                append("Location: ").append(location).append("\r\n")
                append("Content-Length: 0\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
        }
    }

    private val manifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:10")
        appendLine("#EXT-X-MEDIA-SEQUENCE:1")
        appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"keys/main.key\",IV=0x00000000000000000000000000000001")
        appendMainGroup(start = 0, absoluteIndex = 3)
        appendAdGroup()
        appendMainGroup(start = 30)
        appendAdGroup()
        appendMainGroup(start = 60)
        append("#EXT-X-ENDLIST")
    }

    private val masterManifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"keys/session.key\"")
        appendLine("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000,RESOLUTION=1080x608")
        appendLine("media/low.m3u8")
    }

    private val partialMasterManifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-STREAM-INF:BANDWIDTH=800000")
        appendLine("low/index.m3u8")
    }

    private val partialMediaManifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:9")
        appendLine("#EXT-X-TARGETDURATION:4")
        appendLine("#EXT-X-PART-INF:PART-TARGET=1.0")
        appendLine("#EXT-X-PART:DURATION=1.0,URI=\"part0.m4s\"")
        appendLine("#EXTINF:4,")
        appendLine("seg0.ts")
        append("#EXT-X-PRELOAD-HINT:TYPE=PART,URI=\"next.m4s\"")
    }

    private val doubleFilterRegressionManifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:10")
        appendMainGroup(start = 0)
        appendAdGroup(uriPrefix = "/ads/ad-double")
        appendShortNormalGroup()
        repeat(17) { index ->
            appendMainGroup(start = 30 + index * 30)
        }
        append("#EXT-X-ENDLIST")
    }

    private fun StringBuilder.appendMainGroup(start: Int, absoluteIndex: Int? = null) {
        if (start != 0) appendLine("#EXT-X-DISCONTINUITY")
        repeat(30) { offset ->
            val number = start + offset
            appendLine("#EXTINF:3,")
            if (number == absoluteIndex) {
                appendLine("https://cdn.example.com/main${number.toString().padStart(3, '0')}.ts")
            } else {
                appendLine("main${number.toString().padStart(3, '0')}.ts")
            }
        }
    }

    private fun StringBuilder.appendAdGroup(uriPrefix: String = "/ads/ad") {
        appendLine("#EXT-X-DISCONTINUITY")
        appendLine("#EXTINF:6,")
        appendLine("${uriPrefix}001.ts")
        appendLine("#EXTINF:6,")
        appendLine("${uriPrefix}002.ts")
    }

    private fun StringBuilder.appendShortNormalGroup() {
        appendLine("#EXT-X-DISCONTINUITY")
        repeat(4) { index ->
            appendLine("#EXTINF:4,")
            appendLine("short-normal${index.toString().padStart(3, '0')}.ts")
        }
    }
}

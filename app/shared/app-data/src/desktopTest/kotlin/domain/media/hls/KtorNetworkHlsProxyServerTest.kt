/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorNetworkHlsProxyServerTest {
    private suspend fun startEchoServer(): KtorNetworkHlsProxyServer {
        val server = KtorNetworkHlsProxyServer.create()
        server.start { request, sink ->
            val body = "path=${request.path};range=${request.headers["range"]}".encodeToByteArray()
            sink.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
            sink.write(body)
        }
        return server
    }

    private suspend fun awaitServing(url: String): String = withTimeout(15_000) {
        while (true) {
            val result = runCatching { httpGet(url) }.getOrNull()
            if (result != null && result.status == 200) return@withTimeout result.body.decodeToString()
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    @Test
    fun `parses request line and headers`() = runBlocking {
        val server = startEchoServer()
        try {
            val result = httpGet("http://127.0.0.1:${server.port}/segment/3.ts?token=1", mapOf("Range" to "bytes=0-9"))
            assertEquals("path=/segment/3.ts;range=bytes=0-9", result.body.decodeToString())
        } finally {
            server.close()
        }
    }

    /**
     * iOS 会在应用挂起期间回收监听 socket. 播放器持有的地址带着端口号, 所以必须在原端口上恢复.
     */
    @Test
    fun `recovers on the same port after the listener breaks`() = runBlocking {
        val server = startEchoServer()
        try {
            val url = "http://127.0.0.1:${server.port}/playlist.m3u8"
            assertEquals("path=/playlist.m3u8;range=null", awaitServing(url))
            val port = server.port
            repeat(3) {
                server.breakListenerForTest()
                assertEquals("path=/playlist.m3u8;range=null", awaitServing(url))
                assertEquals(port, server.port)
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `does not come back after close`() = runBlocking {
        val server = startEchoServer()
        val url = "http://127.0.0.1:${server.port}/playlist.m3u8"
        awaitServing(url)
        server.close()
        delay(1_500) // 超过重绑定的重试间隔
        assertFailsWith<Exception> { httpGet(url) }
        Unit
    }
}

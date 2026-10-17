/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal actual val PlatformHlsProxyServerFactory: HlsProxyServerFactory = HlsProxyServerFactory { JvmHlsProxyServer() }

/**
 * 基于 [ServerSocket] 的实现, 每个连接一个线程.
 */
internal class JvmHlsProxyServer : HlsProxyServer {
    private val closed = AtomicBoolean(false)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    override val port: Int get() = serverSocket.localPort

    override fun start(handler: HlsProxyRequestHandler) {
        thread(name = "HlsProxy-$port", isDaemon = true) {
            while (!closed.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: SocketException) {
                    if (!closed.get()) logger.warn(e) { "Failed to accept HLS proxy connection" }
                    continue
                } catch (e: IOException) {
                    logger.warn(e) { "Failed to accept HLS proxy connection" }
                    continue
                }
                thread(name = "HlsProxy-$port-conn", isDaemon = true) {
                    socket.use { handleConnection(it, handler) }
                }
            }
        }
    }

    private fun handleConnection(socket: Socket, handler: HlsProxyRequestHandler) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            val request = parseHlsProxyRequest(reader.readLine()) { reader.readLine() } ?: return
            socket.getOutputStream().use { output ->
                try {
                    runBlocking { handler.handle(request, OutputStreamSink(output)) }
                } catch (e: SocketException) {
                    // 播放器提前断开 (如 seek), 属正常情况
                } catch (e: Throwable) {
                    // 不能让异常逃出连接线程: 在 Android 上未捕获的异常会直接让应用崩溃
                    logger.warn(e) { "Failed to serve HLS proxy request ${request.path}" }
                }
                runCatching { output.flush() }
            }
        } catch (e: IOException) {
            if (!closed.get()) logger.warn(e) { "Failed to serve HLS proxy request" }
        }
    }

    private class OutputStreamSink(private val output: OutputStream) : HlsProxyResponseSink {
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            output.write(bytes, offset, length)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            serverSocket.close()
        }
    }

    private companion object {
        private val logger = logger<JvmHlsProxyServer>()
    }
}

internal actual fun resolveHlsUri(baseUri: String, uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    if (parsed.isAbsolute) return uri
    val base = runCatching { URI(baseUri) }.getOrNull() ?: return uri
    return base.resolve(parsed).toString()
}

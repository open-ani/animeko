/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/**
 * 基于 ktor-network 协程 socket 的 [HlsProxyServer], 不依赖 JVM, 供 iOS 使用.
 *
 * ktor 在 Darwin 上已忽略 `SIGPIPE`, 播放器提前断开连接 (seek 时很常见) 只会让写入抛出异常, 不会杀死进程.
 *
 * ### 监听 socket 失效后的恢复
 *
 * iOS 会在应用被挂起期间回收 socket (见 Apple TN2277). 播放器手里的播放列表和分片地址都带着端口号,
 * 所以监听失败时在**同一个端口**上重新监听, 回到前台后播放器的后续请求仍然能连上.
 */
internal class KtorNetworkHlsProxyServer private constructor(
    private val selector: SelectorManager,
    serverSocket: ServerSocket,
) : HlsProxyServer {
    private val closed = atomic(false)

    @Volatile
    private var serverSocket: ServerSocket = serverSocket

    override val port: Int = (serverSocket.localAddress as InetSocketAddress).port

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO_ + CoroutineName("HlsProxy-$port"))

    override fun start(handler: HlsProxyRequestHandler) {
        scope.launch {
            while (isActive && !closed.value) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (closed.value) break
                    logger.warn(e) { "HLS proxy listener on port $port failed, rebinding" }
                    rebind()
                    continue
                }
                launch {
                    try {
                        handleConnection(socket, handler)
                    } finally {
                        socket.close()
                    }
                }
            }
        }
    }

    /**
     * 在原端口上重新监听. 端口暂时被占用等原因失败时稍后由 accept 循环再次触发.
     */
    private suspend fun rebind() {
        runCatching { serverSocket.close() }
        try {
            val rebound = aSocket(selector).tcp().bind("127.0.0.1", port) { reuseAddress = true }
            serverSocket = rebound
            if (closed.value) rebound.close() // 与 close() 竞争
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to rebind HLS proxy listener on port $port" }
            delay(ACCEPT_RETRY_DELAY)
        }
    }

    /**
     * 仅供测试: 模拟系统回收了监听 socket.
     */
    internal fun breakListenerForTest() {
        serverSocket.close()
    }

    private suspend fun handleConnection(socket: Socket, handler: HlsProxyRequestHandler) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        try {
            // 播放器建立连接后迟迟不发请求时不要一直占着协程
            val request = withTimeoutOrNull(REQUEST_TIMEOUT) {
                parseHlsProxyRequest(input.readUTF8Line(MAX_LINE_LENGTH)) { input.readUTF8Line(MAX_LINE_LENGTH) }
            } ?: return
            try {
                handler.handle(request, ChannelSink(output))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 写入失败基本都是播放器提前断开 (如 seek), 属正常情况; 其他错误已由 handler 记录
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!closed.value) logger.warn(e) { "Failed to serve HLS proxy request" }
        } finally {
            // 先把缓冲区里的数据发完再关连接, 否则播放器会收到被截断的响应
            runCatching { output.flushAndClose() }
        }
    }

    private class ChannelSink(private val output: ByteWriteChannel) : HlsProxyResponseSink {
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            output.writeFully(bytes, offset, offset + length)
            output.flush()
        }
    }

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            scope.cancel()
            runCatching { serverSocket.close() }
            runCatching { selector.close() }
        }
    }

    companion object Factory : HlsProxyServerFactory {
        private val logger = logger<KtorNetworkHlsProxyServer>()
        private val REQUEST_TIMEOUT = 30.seconds
        private val ACCEPT_RETRY_DELAY = 1.seconds
        private const val MAX_LINE_LENGTH = 16 * 1024

        override suspend fun create(): KtorNetworkHlsProxyServer {
            val selector = SelectorManager(Dispatchers.IO_)
            try {
                val serverSocket = aSocket(selector).tcp().bind("127.0.0.1", 0) { reuseAddress = true }
                return KtorNetworkHlsProxyServer(selector, serverSocket)
            } catch (e: Throwable) {
                selector.close()
                throw e
            }
        }
    }
}

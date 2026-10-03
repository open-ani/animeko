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
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.him188.ani.utils.coroutines.IO_

/**
 * 测试用的 HLS 源站, 跑在模拟器进程内. 内容由 [route] 提供, 记录每个请求的路径.
 */
internal class IosHlsTestOrigin private constructor(private val server: HlsProxyServer) : AutoCloseable {
    class Content(val bytes: ByteArray, val contentType: String)

    private val lock = SynchronizedObject()
    private val routes = HashMap<String, Content>()
    private val requestLog = ArrayList<HlsProxyRequest>()

    /** 每个分片 (非播放列表) 响应前的延迟, 用来模拟慢源站. */
    var segmentLatencyMillis: Long = 0

    val requests: List<HlsProxyRequest> get() = synchronized(lock) { requestLog.toList() }
    fun requestCount(path: String): Int = requests.count { it.path == path }

    fun url(path: String): String = "http://127.0.0.1:${server.port}$path"

    fun route(path: String, bytes: ByteArray, contentType: String = "video/mp2t") {
        synchronized(lock) { routes[path] = Content(bytes, contentType) }
    }

    fun routeText(path: String, text: String) = route(path, text.encodeToByteArray(), "application/vnd.apple.mpegurl")

    private suspend fun handle(request: HlsProxyRequest, sink: HlsProxyResponseSink) {
        val content = synchronized(lock) {
            requestLog += request
            routes[request.path]
        }
        if (content == null) {
            sink.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray())
            return
        }
        if (!request.path.endsWith(".m3u8") && segmentLatencyMillis > 0) delay(segmentLatencyMillis)
        sink.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: ${content.contentType}\r\nContent-Length: ${content.bytes.size}\r\n" +
                    "Connection: close\r\n\r\n").encodeToByteArray(),
        )
        sink.write(content.bytes)
    }

    override fun close() = server.close()

    companion object {
        suspend fun start(): IosHlsTestOrigin {
            val origin = IosHlsTestOrigin(KtorNetworkHlsProxyServer.create())
            origin.server.start { request, sink -> origin.handle(request, sink) }
            return origin
        }
    }
}

internal class RawHttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

/**
 * 用裸 socket 发一个 GET 并读到连接关闭. 不经过 NSURLSession, 因此不受 ATS 影响, 也能精确控制何时断开.
 *
 * @param abortAfterBytes 读到这么多字节后直接断开连接 (模拟播放器 seek 时放弃正在下载的分片), 此时返回 `null`.
 */
internal suspend fun rawHttpGet(
    url: String,
    headers: Map<String, String> = emptyMap(),
    abortAfterBytes: Int? = null,
): RawHttpResponse? {
    val hostPort = url.removePrefix("http://").substringBefore('/')
    val path = "/" + url.removePrefix("http://").substringAfter('/', "")
    val selector = SelectorManager(Dispatchers.IO_)
    try {
        val socket = aSocket(selector).tcp().connect(hostPort.substringBefore(':'), hostPort.substringAfter(':').toInt())
        try {
            val output = socket.openWriteChannel(autoFlush = true)
            val input = socket.openReadChannel()
            output.writeStringUtf8(
                buildString {
                    append("GET $path HTTP/1.1\r\nHost: $hostPort\r\n")
                    headers.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("Connection: close\r\n\r\n")
                },
            )
            var received = ByteArray(0)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                received += buffer.copyOf(read)
                if (abortAfterBytes != null && received.size >= abortAfterBytes) return null
            }
            val headerEnd = received.indexOfHeaderEnd()
            check(headerEnd >= 0) { "malformed response: ${received.decodeToString().take(200)}" }
            val headerLines = received.copyOf(headerEnd).decodeToString().split("\r\n")
            return RawHttpResponse(
                status = headerLines.first().split(' ')[1].toInt(),
                headers = headerLines.drop(1).filter { ':' in it }
                    .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() },
                body = received.copyOfRange(headerEnd + 4, received.size),
            )
        } finally {
            socket.close()
        }
    } finally {
        selector.close()
    }
}

private fun ByteArray.indexOfHeaderEnd(): Int {
    for (i in 0..size - 4) {
        if (this[i] == '\r'.code.toByte() && this[i + 1] == '\n'.code.toByte() &&
            this[i + 2] == '\r'.code.toByte() && this[i + 3] == '\n'.code.toByte()
        ) return i
    }
    return -1
}

/** 大小是 188 的整数倍、内容可校验的伪 TS 数据. */
internal fun fakeTsBytes(seed: Int, packets: Int): ByteArray {
    val bytes = ByteArray(packets * 188)
    for (i in bytes.indices) {
        bytes[i] = if (i % 188 == 0) 0x47 else ((i * 31 + seed * 17) and 0xFF).toByte()
    }
    return bytes
}

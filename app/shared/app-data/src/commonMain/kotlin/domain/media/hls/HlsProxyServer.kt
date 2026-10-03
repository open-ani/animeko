/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

/**
 * HLS 本地代理用的极简 HTTP 服务: 只监听 127.0.0.1, 每个连接只处理一个 GET 请求, 响应后关闭连接.
 *
 * 协议处理 (路由, 响应头, 正文) 都在平台无关的 [HlsProxyRequestHandler] 里, 这里只负责 socket.
 */
internal interface HlsProxyServer : AutoCloseable {
    val port: Int

    /**
     * 开始接受连接. 只能调用一次.
     */
    fun start(handler: HlsProxyRequestHandler)
}

internal fun interface HlsProxyServerFactory {
    /**
     * 在 127.0.0.1 的任意空闲端口上创建服务. 创建后端口即已监听, 但要到 [HlsProxyServer.start] 才开始处理连接.
     */
    suspend fun create(): HlsProxyServer
}

/**
 * @param headers 请求头, 名称已转为小写
 */
internal class HlsProxyRequest(val path: String, val headers: Map<String, String>)

/**
 * 原样写到连接上, 包括状态行和响应头.
 */
internal interface HlsProxyResponseSink {
    suspend fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)
}

internal fun interface HlsProxyRequestHandler {
    /**
     * 抛出异常表示无法继续响应, 由服务关闭连接. 播放器提前断开 (如 seek) 时 [HlsProxyResponseSink.write] 会抛出异常, 属正常情况.
     */
    suspend fun handle(request: HlsProxyRequest, sink: HlsProxyResponseSink)
}

/**
 * 当前平台默认的 [HlsProxyServerFactory].
 */
internal expect val PlatformHlsProxyServerFactory: HlsProxyServerFactory

/**
 * 解析请求行和请求头. 请求行为空 (连接刚建立就被关闭) 时返回 `null`.
 */
internal inline fun parseHlsProxyRequest(requestLine: String?, readHeaderLine: () -> String?): HlsProxyRequest? {
    if (requestLine == null) return null
    val headers = LinkedHashMap<String, String>()
    while (true) {
        val line = readHeaderLine() ?: break
        if (line.isEmpty()) break
        val colon = line.indexOf(':')
        if (colon > 0) {
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
    }
    val path = requestLine
        .substringAfter(" ", missingDelimiterValue = "")
        .substringBefore(" ", missingDelimiterValue = "")
        .substringBefore("?")
        .ifEmpty { "/playlist.m3u8" }
    return HlsProxyRequest(path, headers)
}

/**
 * 把 [uri] 相对于 [baseUri] 解析为绝对地址. [uri] 已经是绝对地址或无法解析时原样返回.
 */
internal expect fun resolveHlsUri(baseUri: String, uri: String): String

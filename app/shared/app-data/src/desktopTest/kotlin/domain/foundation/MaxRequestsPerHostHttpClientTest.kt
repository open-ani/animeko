/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.settings.NoProxyProvider
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxRequestsPerHostHttpClientTest {
    /**
     * 源站在 [CONCURRENCY] 个请求全部到达前不回应. OkHttp 默认每个 host 只放行 5 个, 其余在引擎里排队,
     * 前 5 个就只能等到超时.
     */
    @Test
    fun `client with the feature sends that many requests to one host at once`() = runBlocking {
        val allArrived = CountDownLatch(CONCURRENCY)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/") { exchange ->
                allArrived.countDown()
                val body = if (allArrived.await(5, TimeUnit.SECONDS)) "concurrent" else "queued"
                val bytes = body.encodeToByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            val bodies = provider.get(ScopedHttpClientUserAgent.BROWSER, maxRequestsPerHost = CONCURRENCY).use {
                coroutineScope {
                    List(CONCURRENCY) { async(Dispatchers.IO) { get(url).bodyAsText() } }.awaitAll()
                }
            }
            assertEquals(List(CONCURRENCY) { "concurrent" }, bodies)
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            server.stop(0)
        }
    }

    private companion object {
        const val CONCURRENCY = 8
    }
}

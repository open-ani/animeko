/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import com.github.panpf.sketch.http.HttpStack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.github.panpf.sketch.http.HttpHeaders as SketchHttpHeaders
import io.ktor.http.HttpHeaders as KtorHttpHeaders

class ScopedHttpClientHttpStackTest {
    @Test
    fun `forwards headers response metadata and body inside scoped lifetime`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(listOf("first", "second"), request.headers.getAll("X-Added"))
                assertEquals("only", request.headers["X-Set"])
                respond(
                    content = "downstream failure",
                    status = HttpStatusCode.BadGateway,
                    headers = headersOf(
                        KtorHttpHeaders.ContentType to listOf("text/plain"),
                        KtorHttpHeaders.ContentLength to listOf("18"),
                        "X-Response" to listOf("present"),
                    ),
                )
            },
        )
        val scopedClient = TrackingScopedHttpClient(client)
        val stack = ScopedHttpClientHttpStack(scopedClient)

        try {
            val body = stack.request(
                url = "https://example.com/image",
                httpHeaders = SketchHttpHeaders {
                    add("X-Added", "first")
                    add("X-Added", "second")
                    set("X-Set", "only")
                },
                extras = null,
            ) { response ->
                assertEquals(1, scopedClient.activeCount)
                assertEquals(502, response.code)
                assertEquals("Bad Gateway", response.message)
                assertEquals(18L, response.contentLength)
                assertEquals("text/plain", response.contentType)
                assertEquals("present", response.getHeaderField("X-Response"))
                response.content().readUtf8AndClose()
            }

            assertEquals("downstream failure", body)
            assertEquals(1, scopedClient.borrowCount)
            assertEquals(1, scopedClient.returnCount)
            assertEquals(0, scopedClient.activeCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun `returns borrowed client exactly once when request fails`() = runTest {
        val failure = IOException("network failed")
        val client = HttpClient(MockEngine { throw failure })
        val scopedClient = TrackingScopedHttpClient(client)
        val stack = ScopedHttpClientHttpStack(scopedClient)

        try {
            val actual = assertFailsWith<IOException> {
                stack.request("https://example.com/failure", null, null) { Unit }
            }
            assertEquals(failure.message, actual.message)
            assertEquals(1, scopedClient.borrowCount)
            assertEquals(1, scopedClient.returnCount)
            assertEquals(0, scopedClient.activeCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun `cancellation returns borrowed client`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine {
                requestStarted.complete(Unit)
                awaitCancellation()
            },
        )
        val scopedClient = TrackingScopedHttpClient(client)
        val stack = ScopedHttpClientHttpStack(scopedClient)

        try {
            val job = launch {
                stack.request("https://example.com/slow", null, null) { Unit }
            }
            requestStarted.await()
            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            assertEquals(1, scopedClient.borrowCount)
            assertEquals(1, scopedClient.returnCount)
            assertEquals(0, scopedClient.activeCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun `each request borrows the latest client`() = runTest {
        val first = responseClient("first")
        val second = responseClient("second")
        val scopedClient = TrackingScopedHttpClient(first)
        val stack = ScopedHttpClientHttpStack(scopedClient)

        try {
            assertEquals("first", stack.readBody())
            scopedClient.currentClient = second
            assertEquals("second", stack.readBody())

            assertEquals(listOf(first, second), scopedClient.borrowedClients)
            assertEquals(2, scopedClient.returnCount)
            assertEquals(0, scopedClient.activeCount)
        } finally {
            first.close()
            second.close()
        }
    }

    private fun responseClient(body: String): HttpClient = HttpClient(
        MockEngine {
            respond(body)
        },
    )

    private suspend fun ScopedHttpClientHttpStack.readBody(): String =
        request("https://example.com/image", null, null) { response ->
            response.content().readUtf8AndClose()
        }
}

private suspend fun HttpStack.Content.readUtf8AndClose(): String {
    val bytes = mutableListOf<Byte>()
    val buffer = ByteArray(4)
    try {
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            repeat(count) { index -> bytes += buffer[index] }
        }
    } finally {
        close()
    }
    return ByteArray(bytes.size) { bytes[it] }.decodeToString()
}

@OptIn(UnsafeScopedHttpClientApi::class)
private class TrackingScopedHttpClient(
    initialClient: HttpClient,
) : ScopedHttpClient() {
    var currentClient: HttpClient = initialClient
    var borrowCount: Int = 0
        private set
    var returnCount: Int = 0
        private set
    var activeCount: Int = 0
        private set
    val borrowedClients = mutableListOf<HttpClient>()

    override fun borrow(): Ticket {
        val borrowedClient = currentClient
        borrowCount++
        activeCount++
        borrowedClients += borrowedClient
        return object : Ticket {
            override val client: HttpClient = borrowedClient
        }
    }

    override fun returnClient(ticket: Ticket) {
        check(activeCount > 0) { "A client ticket was returned more than once" }
        returnCount++
        activeCount--
    }
}

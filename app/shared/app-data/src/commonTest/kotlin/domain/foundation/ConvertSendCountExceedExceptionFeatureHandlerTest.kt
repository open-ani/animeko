/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("SSBasedInspection")

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConvertSendCountExceedExceptionFeatureHandlerTest {

    /**
     *  Helper to build a client with/without our feature.
     *  If [installFeature] is true, we install [ConvertSendCountExceedExceptionFeature] = true.
     *
     *  We'll also configure the [HttpSend] plugin with [maxSendCount] to provoke `SendCountExceedException`.
     */
    private fun buildTestClient(
        installFeature: Boolean,
        maxSendCount: Int = 2, // default so we can quickly cause exceed
        respondBlock: suspend (callCount: Int) -> Pair<HttpStatusCode, String>
    ): HttpClient {
        var callCounter = 0

        val mockEngine = MockEngine { _ ->
            callCounter++
            val (status, content) = respondBlock(callCounter)
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        // Create bare client
        val client = HttpClient(mockEngine) {
            install(HttpSend) {
                this.maxSendCount = maxSendCount
            }
        }

        if (installFeature) {
            // Our feature is stored in the companion object "ConvertSendCountExceedExceptionFeatureHandler"
            // We want to set `true` for it
            ConvertSendCountExceedExceptionFeatureHandler.applyToClient(
                client,
                value = true,
            )
        }

        return client
    }

    @Test
    fun `without feature - SendCountExceedException is thrown as-is`() = runTest {
        // We won't install the feature => we expect the original SendCountExceedException if request fails beyond sendCount
        val client = buildTestClient(installFeature = false, maxSendCount = 2) { callCount ->
            // Always fail to provoke repeated attempts
            HttpStatusCode.Found to "fail #$callCount"
        }

        val ex = assertFailsWith<SendCountExceedException> {
            client.get("https://test.com/alwaysFail")
        }
        // Basic assertion
        assertEquals(
            "Max send count 2 exceeded. Consider increasing the property maxSendCount if more is required.",
            ex.message,
        )
    }

    @Test
    fun `with feature on - SendCountExceedException is converted to IOException`() = runTest {
        val client = buildTestClient(installFeature = true, maxSendCount = 2) { callCount ->
            // Always fail
            HttpStatusCode.Found to "fail #$callCount"
        }

        val ex = assertFailsWith<IOException> {
            client.get("https://test.com/alwaysFail")
        }
        // Confirm the cause is SendCountExceedException
        assertIs<SendCountExceedException>(
            ex.findCause<SendCountExceedException>(),
            "Expected cause to be SendCountExceedException",
        )
    }

    @Test
    fun `with feature on - successful request does NOT throw`() = runTest {
        var failCount = 0
        // We'll fail the first time, succeed the second => won't exceed send count
        val client = buildTestClient(installFeature = true, maxSendCount = 2) { callCount ->
            if (callCount == 1) {
                failCount++
                HttpStatusCode.Found to "fail #$callCount"
            } else {
                HttpStatusCode.OK to "success on retry"
            }
        }

        val response = client.request("https://test.com/flakyEndpoint")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("success on retry", response.bodyAsText())
        // We confirm that we got the correct behavior
        assertEquals(1, failCount, "Should fail exactly once, then succeed")
    }

    @Test
    fun `with feature off - normal success`() = runTest {
        val client = buildTestClient(installFeature = false, maxSendCount = 2) { _ ->
            HttpStatusCode.OK to "ok"
        }
        val response = client.get("https://test.com/works")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }
}

private inline fun <reified E : Throwable> Throwable.findCause(): E? {
    var current: Throwable? = this
    while (current != null) {
        if (current is E) {
            return current
        }
        current = current.cause
    }
    return null
}

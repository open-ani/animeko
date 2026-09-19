/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.InMemorySessionStore
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.RateLimiter
import io.github.nihildigit.pikpak.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Clock

class CloudFileLifecycleTest {
    private class Drive(val accountName: String) {
        val events = CopyOnWriteArrayList<String>()
        val deleted = CopyOnWriteArrayList<String>()
        val listingStarted = CompletableDeferred<Unit>()
        val listingAllowed = CompletableDeferred<Unit>()
        val bytes = byteArrayOf(10, 20, 30, 40)
        val http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/v1/shield/captcha/init") -> json("""{"captcha_token":"CAP"}""")
                path.endsWith("/v1/auth/signin") -> json(
                    """{"access_token":"AT","refresh_token":"RT","sub":"UID","expires_in":3600}""",
                )
                path.endsWith("/files:batchDelete") -> {
                    val ids = Json.parseToJsonElement((request.body as TextContent).text)
                        .jsonObject.getValue("ids").jsonArray.map { it.jsonPrimitive.content }
                    deleted.addAll(ids)
                    events += "delete"
                    json("{}")
                }
                path.endsWith("/drive/v1/files") && request.method == HttpMethod.Get -> {
                    if (request.url.parameters["parent_id"].isNullOrEmpty()) {
                        json("""{"files":[{"kind":"drive#folder","id":"$accountName-folder","name":"Animeko-Temp"}]}""")
                    } else {
                        assertEquals("$accountName-folder", request.url.parameters["parent_id"])
                        listingStarted.complete(Unit)
                        listingAllowed.await()
                        events += "listed"
                        json("""{"files":[
                            {"kind":"drive#file","id":"$accountName-old","created_time":"2000-01-01T00:00:00Z"},
                            {"kind":"drive#file","id":"$accountName-active","created_time":"${Clock.System.now()}"},
                            {"kind":"drive#file","id":"$accountName-unknown"},
                            {"kind":"drive#folder","id":"$accountName-subfolder","created_time":"2000-01-01T00:00:00Z"}
                        ]}""")
                    }
                }
                path.endsWith("/drive/v1/files") && request.method == HttpMethod.Post -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("$accountName-folder", body.getValue("parent_id").jsonPrimitive.content)
                    events += "create"
                    json("""{"file":{"id":"$accountName-minted","kind":"drive#file","size":"4","phase":"PHASE_TYPE_COMPLETE"}}""")
                }
                path.endsWith("/drive/v1/files/$accountName-minted") -> json(
                    """{"id":"$accountName-minted","kind":"drive#file","size":"4",
                        "links":{"application/octet-stream":{"url":"https://cdn.test/$accountName","expire":""}}}""",
                )
                request.url.host == "cdn.test" -> {
                    events += "read"
                    respond(bytes, HttpStatusCode.PartialContent, headersOf(
                        HttpHeaders.ContentRange to listOf("bytes 0-3/4"),
                        HttpHeaders.ContentLength to listOf("4"),
                    ))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        })
        val client = PikPakClient(
            account = "$accountName@example.com", password = "test", sessionStore = InMemorySessionStore(),
            httpClient = http, cdnHttpClient = http,
            rateLimiter = RateLimiter.unlimited(), retryPolicy = RetryPolicy.None,
        )
    }

    @Test
    fun `minting does not wait for the sweep and an account switch replaces handles and directory IDs`() = runBlocking {
        val a = Drive("a")
        val b = Drive("b")
        val cleanupDispatcher = StandardTestDispatcher()
        val cleanupScope = CoroutineScope(cleanupDispatcher)
        var account = PikPakAccount(a.client, this)
        val cloud = CloudFile(
            gcid = "A".repeat(40), size = 4, name = "episode.mkv", accountProvider = { account },
            connectionBudget = 1, scope = cleanupScope,
        )
        try {
            withTimeout(15_000) {
                val first = async { cloud.readBytes(0, 4) }
                // The listing is held back, and the read must not be held with it: the sweep is not
                // on the first-playback path.
                a.listingStarted.await()
                assertContentEquals(a.bytes, first.await())
                assertEquals(listOf("create", "read"), a.events)
                a.listingAllowed.complete(Unit)
                while (a.deleted.isEmpty()) delay(1)
                assertEquals(listOf("a-old"), a.deleted)

                account = PikPakAccount(b.client, this@runBlocking)
                b.listingAllowed.complete(Unit)
                assertContentEquals(b.bytes, cloud.readBytes(0, 4))
                while (a.deleted.size < 2 || b.deleted.size < 2) {
                    cleanupDispatcher.scheduler.runCurrent()
                    delay(1)
                }
                assertEquals(listOf("a-old", "a-minted"), a.deleted)
                assertEquals(listOf("b-old", "b-minted"), b.deleted)
                assertEquals(1, a.events.count { it == "read" })
                assertEquals(1, b.events.count { it == "read" })
            }
        } finally {
            a.listingAllowed.complete(Unit)
            b.listingAllowed.complete(Unit)
            cleanupScope.cancel()
            a.client.close()
            b.client.close()
            a.http.close()
            b.http.close()
        }
    }

    private companion object {
        fun MockRequestHandleScope.json(body: String) = respond(
            body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
}

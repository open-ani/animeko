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

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ServerListFeatureHandlerTest {

    // Helper: Basic config that we pass to `ServerListFeatureHandler.applyToClient`.
    private fun defaultConfig(
        hostMatches: Set<String> = setOf(ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST),
    ) = ServerListFeatureConfig(
        aniServerRules = ServerListFeatureConfig.AniServerRule(
            hostMatches = hostMatches,
        ),
    )

    /**
     * Common method to build a test HttpClient that has HttpSend installed and the
     * ServerListFeatureHandler's intercept logic applied.
     */
    private fun buildTestClient(
        aniServerUrlsFlow: MutableStateFlow<List<Url>>,
        featureConfig: ServerListFeatureConfig,
        respondBlock: suspend (url: Url) -> Pair<HttpStatusCode, String>
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            // We'll look at request.url to decide how to respond.
            val (status, body) = respondBlock(request.url)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        // Build HttpClient with the mock engine
        val client = HttpClient(mockEngine) {
            // Ensure we can retry multiple times if needed
            install(HttpSend) {
                maxSendCount = 10
            }
        }

        // Apply the new feature
        val handler = ServerListFeatureHandler(
            aniServerUrls = aniServerUrlsFlow,
        )
        handler.applyToClient(client, featureConfig)
        return client
    }

    @Test
    fun `does not intercept - host not in rule`() = runTest {
        val aniServerUrls = MutableStateFlow(listOf(Url("https://ani-server-1.com")))
        val config = defaultConfig(
            hostMatches = setOf("some-other-magic"), // we won't match the actual host
        )

        // We only expect one request to "non-matching.com" with no rewriting
        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { url ->
            // We expect the request's host to remain "non-matching.com"
            if (url.host == "non-matching.com") {
                HttpStatusCode.OK to "unchanged host"
            } else {
                fail("Expected host not to be changed, but got: ${url.host}")
            }
        }

        val response = client.request("https://non-matching.com/test")
        assertEquals(HttpStatusCode.OK, response.status, "Expected success without rewriting")
        assertEquals("unchanged host", response.bodyAsText())
    }

    @Test
    fun `single server - successful on first try`() = runTest {
        val aniServerUrls = MutableStateFlow(listOf(Url("https://ani-server-1.com")))
        val config = defaultConfig()

        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { url ->
            // We only have one server in the flow: "ani-server-1.com"
            // If the request host is "ani-server-1.com", we respond with 200
            when (url.host) {
                "ani-server-1.com" -> HttpStatusCode.OK to "OK from first server"
                else -> fail("Host should have been rewritten to ani-server-1.com but got: ${url.host}")
            }
        }

        val response = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK from first server", response.bodyAsText())
    }

    @Test
    fun `multiple servers - retries next if previous fails`() = runTest {
        val aniServerUrls = MutableStateFlow(
            listOf(
                Url("https://fail1.com"),
                Url("https://fail2.com"),
                Url("https://ok.com"),
            ),
        )
        val config = defaultConfig()

        var fail1Requests = 0
        var fail2Requests = 0
        var okRequests = 0

        val client = buildTestClient(aniServerUrls, config) { url ->
            when (url.host) {
                "fail1.com" -> {
                    assertEquals(listOf("someApi"), url.segments)
                    fail1Requests++
                    HttpStatusCode.InternalServerError to "fail1"
                }

                "fail2.com" -> {
                    assertEquals(listOf("someApi"), url.segments)
                    fail2Requests++
                    // Let's simulate a 400 for fail2
                    HttpStatusCode.BadRequest to "fail2"
                }

                "ok.com" -> {
                    assertEquals(listOf("someApi"), url.segments)
                    okRequests++
                    HttpStatusCode.OK to "success from ok.com"
                }

                else -> {
                    fail("Unexpected host: ${url.host}")
                }
            }
        }

        val response = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/someApi")
        assertEquals(HttpStatusCode.OK, response.status, "Expected to eventually succeed on the third server")
        assertEquals("success from ok.com", response.bodyAsText())

        // Verify that we actually retried in the correct order
        assertEquals(1, fail1Requests, "First server tried exactly once")
        assertEquals(1, fail2Requests, "Second server tried exactly once")
        assertEquals(1, okRequests, "Third server tried once - success, so we stop further retries")
    }

    @Test
    fun `all servers fail - return last call if available`() = runTest {
        val aniServerUrls = MutableStateFlow(
            listOf(
                Url("https://fail1.com"),
                Url("https://fail2.com"),
            ),
        )
        val config = defaultConfig()

        val client = buildTestClient(aniServerUrls, config) { url ->
            when (url.host) {
                "fail1.com" -> HttpStatusCode.ServiceUnavailable to "fail1"
                "fail2.com" -> HttpStatusCode.GatewayTimeout to "fail2"
                else -> fail("Unexpected host: ${url.host}")
            }
        }

        val response = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/failAll")
        // Because none returned 100..399, the code picks the "lastCall" to return.
        // The last call has status GatewayTimeout
        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        assertEquals("fail2", response.bodyAsText(), "Expected body from the last call")
    }

    @Test
    fun `empty server list - throws immediately`() = runTest {
        val aniServerUrls = MutableStateFlow<List<Url>>(emptyList())
        val config = defaultConfig()

        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { _ ->
            // We should never get here because the code calls
            // `error("No server URL to try for ani server request")` first
            fail("Should not attempt any request if the server list is empty")
        }

        val ex = assertFailsWith<IllegalStateException> {
            client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/api")
        }
        assertTrue(ex.message.orEmpty().contains("No server URL to try for ani server request"))
    }

    @Test
    fun `host matches check - partial match is allowed if it starts with`() = runTest {
        // By default the code checks `rule.hostMatches.none { request.url.host.startsWith(it) }`
        // So "abc.magic_ani_server" also matches if rule.hostMatches contains "abc." or "a"
        val aniServerUrls = MutableStateFlow(listOf(Url("https://faked.com")))
        val config = defaultConfig(
            hostMatches = setOf("abc."),
        )
        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { url ->
            if (url.host == "faked.com") {
                HttpStatusCode.OK to "ok"
            } else {
                fail("Expected rewriting to faked.com, got ${url.host}")
            }
        }

        // "abc.magic_ani_server.com" starts with "abc."
        val response = client.get("https://abc.magic_ani_server.com")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `cancellation exceptions are rethrown - no fallback`() = runTest {
        val aniServerUrls = MutableStateFlow(listOf(Url("https://server1.com"), Url("https://server2.com")))
        val config = defaultConfig()

        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { url ->
            // Force a CancellationException for the first server
            if (url.host == "server1.com") {
                throw CancellationException("User canceled")
            }
            // If the code wrongly swallows cancellation, it would proceed to server2.
            // But we expect the code to re-throw cancellation.
            fail("We should never proceed to server2 if the first server throws CancellationException.")
        }

        val ex = assertFailsWith<CancellationException> {
            client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/someApi")
        }
        assertEquals("User canceled", ex.message)
    }

    @Suppress("AuthLeak")
    @Test
    fun `replaceUrl - verifies correct replacement in URLBuilder`() {
        val originalUrl = Url("http://user:pass@oldhost:1234/some/path?query=1#fragment")
        val newServer = Url("https://newhost:443/") // no path => we want to preserve path from original
        val builder = io.ktor.http.URLBuilder(originalUrl)

        // Actually call the tested function
        ServerListFeatureHandler.replaceUrl(builder, newServer)

        val replaced = builder.build()
        assertEquals("https", replaced.protocol.name)
        assertEquals("newhost", replaced.host)
        assertEquals(443, replaced.port)
        assertEquals(null, replaced.user)
        assertEquals(null, replaced.password)
        // Path and query remain from the builder
        assertEquals("/some/path", replaced.encodedPath)
        assertEquals("query=1", replaced.fullPath.substringAfter('?'))

        assertEquals("https://newhost/some/path?query=1#fragment", replaced.toString())
    }

    @Test
    fun `sticky selection - rotates starting server based on last success`() = runTest {
        val aniServerUrls = MutableStateFlow(
            listOf(
                Url("https://server1.com"),
                Url("https://server2.com"),
                Url("https://server3.com"),
            ),
        )
        val config = defaultConfig()

        // Track attempts per request path to check the first tried host of each request
        val attemptsByPath = mutableMapOf<String, MutableList<String>>()

        // Phase controls which host succeeds to simulate different successful servers over time
        var phase = 1

        val client = buildTestClient(
            aniServerUrlsFlow = aniServerUrls,
            featureConfig = config,
        ) { url ->
            val path = url.fullPath
            attemptsByPath.getOrPut(path) { mutableListOf() }.add(url.host)

            when (phase) {
                1 -> when (url.host) {
                    "server1.com" -> HttpStatusCode.ServiceUnavailable to "f1"
                    "server2.com" -> {
                        phase = 2
                        HttpStatusCode.OK to "ok2"
                    }

                    else -> HttpStatusCode.InternalServerError to "f"
                }

                2 -> when (url.host) {
                    "server2.com" -> HttpStatusCode.ServiceUnavailable to "f2"
                    "server3.com" -> {
                        phase = 3
                        HttpStatusCode.OK to "ok3"
                    }

                    else -> HttpStatusCode.InternalServerError to "f"
                }

                else -> when (url.host) {
                    "server3.com" -> HttpStatusCode.ServiceUnavailable to "f3"
                    "server1.com" -> {
                        phase = 4
                        HttpStatusCode.OK to "ok1"
                    }

                    else -> HttpStatusCode.InternalServerError to "f"
                }
            }
        }

        val r1 = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/req1")
        assertEquals(HttpStatusCode.OK, r1.status)
        assertEquals("ok2", r1.bodyAsText())

        val r2 = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/req2")
        assertEquals(HttpStatusCode.OK, r2.status)
        assertEquals("ok3", r2.bodyAsText())

        val r3 = client.get("https://${ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST}/req3")
        assertEquals(HttpStatusCode.OK, r3.status)
        assertEquals("ok1", r3.bodyAsText())

        // Verify the first tried host for each request follows sticky order:
        // - req1 starts with server1 (default index 0)
        // - req2 starts with server2 (last success)
        // - req3 starts with server3 (updated last success)
        assertEquals("server1.com", attemptsByPath["/req1"]?.firstOrNull())
        assertEquals("server2.com", attemptsByPath["/req2"]?.firstOrNull())
        assertEquals("server3.com", attemptsByPath["/req3"]?.firstOrNull())
    }
}

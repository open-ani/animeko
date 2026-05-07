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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Canary against Ktor ABI drift between pikpak-kotlin and animeko.
 *
 * The SDK is published with ktor-* as compileOnly, so its bytecode still
 * encodes Ktor companion-object field accesses (HttpMethod.Post,
 * ContentType.Application.Json, HttpStatusCode.OK, HttpHeaders.ContentType,
 * ...) against whatever Ktor version the SDK was compiled against. If that
 * compile-time version's bytecode layout diverges from the Ktor version
 * animeko actually pins (see gradle/libs.versions.toml `ktor`), the first
 * field access inside the SDK's request path fails with IllegalAccessError
 * at class link time — long before any real network I/O could mask it.
 *
 * This test wires a MockEngine so no network call happens, then drives a
 * full successful login() through the SDK. login() internally references
 * HttpMethod.Post in [internal/AuthApi.signInLocked] / [refreshAccessTokenLocked]
 * and HttpStatusCode / ContentType / HttpHeaders in the mock-response
 * construction path — exercising the primary ABI surface in one sitting.
 *
 * A passing run says nothing about the SDK's live behaviour; that is still
 * [PikPakLiveSmokeTest]'s job. A failing run specifically means the SDK
 * jar and animeko's pinned Ktor are incompatible and a bump is required
 * on one side or the other.
 */
class PikPakKtorAbiCompatTest {

    @Test
    fun `SDK signin path links against animeko's Ktor without IllegalAccessError`() = runBlocking {
        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/v1/shield/captcha/init") ->
                    respondJson("""{"captcha_token":"CAP"}""")
                req.url.encodedPath.endsWith("/v1/auth/signin") ->
                    respondJson(
                        """{"access_token":"AT","refresh_token":"RT","sub":"UID","expires_in":3600}""",
                    )
                else -> respondJson("""{}""")
            }
        }
        val client = PikPakClient(
            account = "canary@example.com",
            password = "canary",
            sessionStore = InMemorySessionStore(),
            httpClient = HttpClient(engine),
        )
        try {
            client.login()
        } finally {
            client.close()
        }
        // Keep the @Test method's JVM signature `()V`; a value-returning
        // last statement would match `()Session` and JUnit 5 silently
        // ignores methods whose return type is not Unit/void.
        Unit
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}

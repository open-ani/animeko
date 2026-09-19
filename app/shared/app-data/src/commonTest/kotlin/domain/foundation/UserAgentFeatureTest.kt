/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeScopedHttpClientApi::class, TestOnly::class)

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.test.DisabledOnAndroid
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserAgentFeatureTest {
    private fun clientWithMock(
        capture: MutableList<List<String>>,
        config: HttpClient.() -> Unit = {},
        install: Boolean,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            capture.add(request.headers.getAll(HttpHeaders.UserAgent).orEmpty())
            respondOk()
        },
    ) {
        if (install) {
            install(UserAgent) { agent = "plugin-agent" }
        }
    }.apply(config)

    @Test
    fun `UserAgent plugin appends a second value`() = runTest {
        val captured = mutableListOf<List<String>>()
        clientWithMock(captured, install = true).use { client ->
            client.get("https://example.com") { header(HttpHeaders.UserAgent, "caller-agent") }
        }
        assertEquals(listOf("caller-agent", "plugin-agent"), captured.single())
    }

    @Test
    fun `without the plugin only the caller value is sent`() = runTest {
        val captured = mutableListOf<List<String>>()
        clientWithMock(captured, install = false).use { client ->
            client.get("https://example.com") { header(HttpHeaders.UserAgent, "caller-agent") }
        }
        assertEquals(listOf("caller-agent"), captured.single())
    }

    @DisabledOnAndroid
    @Test
    fun `NONE yields a client without the UserAgent plugin`() = runTest {
        withProvider { provider ->
            val ticket = provider
                .get(setOf(UserAgentFeature.withValue(ScopedHttpClientUserAgent.NONE)))
                .borrow()
            assertNull(ticket.client.pluginOrNull(UserAgent))
        }
    }

    @DisabledOnAndroid
    @Test
    fun `ANI still installs the UserAgent plugin`() = runTest {
        withProvider { provider ->
            val ticket = provider
                .get(setOf(UserAgentFeature.withValue(ScopedHttpClientUserAgent.ANI)))
                .borrow()
            assertNotNull(ticket.client.pluginOrNull(UserAgent))
        }
    }

    private suspend fun TestScope.withProvider(block: suspend (DefaultHttpClientProvider) -> Unit) {
        val provider = DefaultHttpClientProvider(
            proxyProvider = object : ProxyProvider {
                override val proxy: Flow<ProxyConfig?> = MutableStateFlow(null)
            },
            backgroundScope = this,
            featureHandlers = listOf(UserAgentFeatureHandler),
        )
        try {
            block(provider)
        } finally {
            provider.forceReleaseAll()
        }
    }
}

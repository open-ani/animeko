/*
 * Copyright (C) 2026 OpenAni and contributors.
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
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.foundation.AniInstallIdFeatureHandler.Companion.HEADER_INSTALL_ID
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig.Companion.MAGIC_ANI_SERVER
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.client.models.AniReportInstallRequest
import me.him188.ani.test.DisabledOnAndroid
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AniInstallIdFeatureHandlerTest {
    private val installId = "3f2b0c1e-8d4a-4b6f-9a7e-0c5d2e1f3a4b"
    private val requests = mutableListOf<HttpRequestData>()
    private val engine = MockEngine { request ->
        requests += request
        respondOk()
    }

    private fun client(enabled: Boolean = true, id: String? = installId): HttpClient {
        val handler = AniInstallIdFeatureHandler { id }
        return HttpClient(engine) { handler.applyToConfig(this, enabled) }
    }

    @Test
    fun `adds install id to Ani server requests`() = runTest {
        client().get("${MAGIC_ANI_SERVER}v2/users/me")

        assertEquals(installId, requests.single().headers[HEADER_INSTALL_ID])
    }

    @Test
    fun `does not send install id to other hosts`() = runTest {
        client().get("https://example.com/v2/users/me")

        assertNull(requests.single().headers[HEADER_INSTALL_ID])
    }

    @Test
    fun `omits header before the id is loaded`() = runTest {
        client(id = null).get("${MAGIC_ANI_SERVER}v2/users/me")

        assertNull(requests.single().headers[HEADER_INSTALL_ID])
    }

    @Test
    fun `disabled feature adds no header`() = runTest {
        client(enabled = false).get("${MAGIC_ANI_SERVER}v2/users/me")

        assertNull(requests.single().headers[HEADER_INSTALL_ID])
    }

    @Test
    fun `header survives replacing the magic host with the real server`() = runTest {
        val handler = AniInstallIdFeatureHandler { installId }
        val client = HttpClient(engine) {
            install(HttpSend)
            handler.applyToConfig(this, true)
        }
        ServerListFeatureHandler(MutableStateFlow(listOf(Url("https://api.example.test"))))
            .applyToClient(client, ServerListFeatureConfig.Default)

        client.get("${MAGIC_ANI_SERVER}v2/users/me")

        val request = requests.single()
        assertEquals("api.example.test", request.url.host)
        assertEquals(installId, request.headers[HEADER_INSTALL_ID])
    }

    @Test
    fun `install report request carries the install id`() = runTest {
        val handler = AniInstallIdFeatureHandler { installId }
        val api = UserProfileAniApi(
            baseUrl = MAGIC_ANI_SERVER,
            httpClientEngine = engine,
            httpClientConfig = { handler.applyToConfig(it, true) },
        )

        api.reportInstall(
            userAgent = "test",
            aniReportInstallRequest = AniReportInstallRequest(
                firstLaunchAt = "2026-01-01T00:00:00Z",
                localEpisodesBeforeLogin = 0,
                hadPreviousLogin = false,
            ),
        )

        val request = requests.single()
        assertEquals("/v2/users/me/install-report", request.url.encodedPath)
        assertEquals(installId, request.headers[HEADER_INSTALL_ID])
    }

    @DisabledOnAndroid
    @Test
    fun `only clients requesting the feature install the plugin`() = runTest {
        val handler = AniInstallIdFeatureHandler { installId }
        withProvider(handler) { provider ->
            val aniClient = provider.get(useAniInstallId = true).borrow().client
            val otherClient = provider.get(userAgent = ScopedHttpClientUserAgent.BROWSER).borrow().client

            assertNotNull(aniClient.pluginOrNull(handler.plugin))
            assertNull(otherClient.pluginOrNull(handler.plugin))
        }
    }

    private suspend fun TestScope.withProvider(
        handler: AniInstallIdFeatureHandler,
        block: suspend (DefaultHttpClientProvider) -> Unit,
    ) {
        val provider = DefaultHttpClientProvider(
            proxyProvider = object : ProxyProvider {
                override val proxy: Flow<ProxyConfig?> = MutableStateFlow(null)
            },
            backgroundScope = this,
            featureHandlers = listOf(UserAgentFeatureHandler, handler),
        )
        try {
            block(provider)
        } finally {
            provider.forceReleaseAll()
        }
    }
}

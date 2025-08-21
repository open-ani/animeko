/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeScopedHttpClientApi::class)

package me.him188.ani.app.domain.foundation

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.test.DisabledOnAndroid
import me.him188.ani.test.TestContainer
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@DisabledOnAndroid // Need Android permission but we don't have such foundational support
@Suppress("CanSealedSubClassBeObject")
sealed class DefaultHttpClientProviderTest {
    @TestContainer
    class SingleFeature : DefaultHttpClientProviderTest() {
        override fun TestScope.createProvider(proxyProvider: FakeProxyProvider): DefaultHttpClientProvider {
            return DefaultHttpClientProvider(
                proxyProvider = proxyProvider,
                backgroundScope = this,
                featureHandlers = listOf(UserAgentFeatureHandler),
            ).apply {
                backgroundScope.coroutineContext.job.invokeOnCompletion {
                    launch(NonCancellable) {
                        forceReleaseAll()
                    }
                }
            }
        }
    }

    @TestContainer
    class HasUnsetFeatures : DefaultHttpClientProviderTest() {
        override fun TestScope.createProvider(proxyProvider: FakeProxyProvider): DefaultHttpClientProvider {
            return DefaultHttpClientProvider(
                proxyProvider = proxyProvider,
                backgroundScope = this,
                featureHandlers = listOf(
                    UserAgentFeatureHandler,
                ),
            ).apply {
                backgroundScope.coroutineContext.job.invokeOnCompletion {
                    launch(NonCancellable) {
                        forceReleaseAll()
                    }
                }
            }
        }
    }

    /**
     * A fake [ProxyProvider] that you can manually control by setting [proxyState].
     */
    protected class FakeProxyProvider : ProxyProvider {
        private val _proxy = MutableStateFlow<ProxyConfig?>(null)
        override val proxy: Flow<ProxyConfig?> = _proxy

        fun emit(newValue: ProxyConfig?) {
            _proxy.value = newValue
        }
    }

    protected abstract fun TestScope.createProvider(
        proxyProvider: FakeProxyProvider,
    ): DefaultHttpClientProvider

    private suspend fun DefaultHttpClientProvider.startProxyListening() {
        startProxyListening(
            sequence {
                for (userAgent in ScopedHttpClientUserAgent.entries) {
                    HoldingInstanceMatrix(
                        setOf(
                            UserAgentFeature.withValue(userAgent),
                        ),
                    )
                }
            },
        )
    }

    @Test
    fun `test get with same user agent reuses same client`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(testProxyProvider)

        val client1 = provider.get(ScopedHttpClientUserAgent.ANI).borrow()
        val client2 = provider.get(ScopedHttpClientUserAgent.ANI).borrow()

        // They should be the same underlying reference because of the pool reuse
        assertEquals(client1, client2, "Expected equal HttpClient instance for the same user agent")

        // Clean up
        provider.get(ScopedHttpClientUserAgent.ANI).returnClient(client1)
        provider.get(ScopedHttpClientUserAgent.ANI).returnClient(client2)
        provider.forceReleaseAll()
    }

    @Test
    fun `test get with different user agent returns different client`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        val aniClient = provider.get(ScopedHttpClientUserAgent.ANI).borrow()
        val browserClient = provider.get(ScopedHttpClientUserAgent.BROWSER).borrow()

        // They should not be the same reference
        assertNotSame(
            aniClient,
            browserClient,
            "Expected different HttpClient instances for different user agents",
        )

        // Clean up
        provider.get(ScopedHttpClientUserAgent.ANI).returnClient(aniClient)
        provider.get(ScopedHttpClientUserAgent.BROWSER).returnClient(browserClient)
        provider.forceReleaseAll()
    }

    @Test
    fun `test startProxyListening only once`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        // Initially false
        assertFalse(provider.getProxyListeningStarted())

        // Call once
        provider.startProxyListening()
        assertTrue(provider.getProxyListeningStarted(), "Expected proxyListeningStarted to be true after first call")

        // Call again
        assertFailsWith<IllegalStateException> {
            provider.startProxyListening()
        }
        assertTrue(
            provider.getProxyListeningStarted(),
            "Expected proxyListeningStarted to remain true and do nothing on second call",
        )
        provider.forceReleaseAll()
    }

    // this test is manually written
    @Test
    fun `test startProxyListening suspends and reads the first proxy`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val proxyConfig = ProxyConfig(url = "http://localhost:8080")
        testProxyProvider.emit(proxyConfig)
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        // Initially false
        assertFalse(provider.getProxyListeningStarted())

        // Call once
        provider.startProxyListening()
        // no runCurrent, so background coroutine will not run.
        assertEquals(
            proxyConfig,
            provider.getCurrentProxyConfig(),
            "Expected to read the first proxy config when the function returns",
        )
        provider.forceReleaseAll()
    }

    @Test
    fun `test proxy listening updates clients when flow emits`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        // Start listening
        provider.startProxyListening()
        assertEquals(null, provider.getCurrentProxyConfig())

        // Borrow a client so that it is subscribed to the proxy flow
        val aniWrapper = provider.get(ScopedHttpClientUserAgent.ANI)
        val aniClientBefore = aniWrapper.borrow()

        // Now let's emit a new proxy config; the existing borrowed client
        // will get its scope cancelled once the new config arrives and cause a new client to be created
        val newConfig = ProxyConfig(url = "http://localhost:9999")
        testProxyProvider.emit(newConfig)
        runCurrent()
        assertEquals(newConfig, provider.getCurrentProxyConfig())

        // Borrow again. This should be a new instance (because the old one is effectively replaced).
        val aniClientAfter = aniWrapper.borrow()

        // We want to confirm that the old instance differs from the new
        // (meaning the code actually re-borrowed or replaced the existing reference).
        assertNotSame(
            aniClientBefore,
            aniClientAfter,
            "Expected a new HttpClient instance once the proxy config changes",
        )

        // Clean up
        aniWrapper.returnClient(aniClientBefore)
        aniWrapper.returnClient(aniClientAfter)
        provider.forceReleaseAll()
    }

    @Test
    fun `test repeated proxy changes`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        // Start listening so that it subscribes to the proxy flow
        provider.startProxyListening()

        val wrapper = provider.get(ScopedHttpClientUserAgent.ANI)
        val firstClient = wrapper.borrow()

        // Emit first change
        testProxyProvider.emit(ProxyConfig(url = "http://first-change:8080"))
        runCurrent()
        val secondClient = wrapper.borrow()

        // The first client should have been replaced
        assertNotSame(
            firstClient,
            secondClient,
            "Expected new client after the first proxy update",
        )

        // Emit second change
        testProxyProvider.emit(ProxyConfig(url = "http://second-change:9090"))
        runCurrent()
        val thirdClient = wrapper.borrow()

        assertNotSame(
            secondClient,
            thirdClient,
            "Expected new client after the second proxy update",
        )

        // Clean up
        wrapper.returnClient(firstClient)
        wrapper.returnClient(secondClient)
        wrapper.returnClient(thirdClient)
        provider.forceReleaseAll()
    }
}

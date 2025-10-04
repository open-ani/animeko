/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.ktor.registerLogging
import me.him188.ani.utils.ktor.setProxy
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 用户提供在 APP 生命周期中持久存在的 [HttpClient] 实例, 以减少实例数量.
 */
sealed class HttpClientProvider {
    /**
     * 用于监听 [HttpClientProvider] 的配置变化的 flow.
     *
     * 此 flow emit 的值仅作为配置变化的通知, 不应当被使用.
     *
     * 当此 flow emit 后, [ScopedHttpClient.borrow] 一定可以拿到一个新实例.
     */
    abstract val configurationFlow: Flow<*>

    /**
     * 获取一个可复用的 [ScopedHttpClient]；它会根据 [features] 从对象池中借用或新建。
     *
     * 常见用法：
     * ```kotlin
     * val result = httpClientProvider.get(setOf(UserAgentFeature.withValue(ANI))).use {
     *     get("https://example.com")
     * }
     * ```
     *
     * 一般也可以使用扩展方法, 直接用参数名提供目前支持的特性类型.
     * ```kotlin
     * val result = httpClientProvider.get(userAgent = ANI).use {
     *     get("https://example.com")
     * }
     * ```
     *
     * 建议将 [get] 到的实例缓存在一个变量中, 以避免频繁检查 feature 是否变化.
     *
     * @param features 请求的特性列表. 对于未指定的特性, 会使用默认值.
     *
     * @see get
     */
    abstract fun get(
        features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    ): ScopedHttpClient

}

/**
 * 获取一个可复用的 [ScopedHttpClient]；它会根据 [userAgent] 和当前 [ProxyConfig] 组合从对象池中借用或新建。
 *
 * 常见用法：
 * ```kotlin
 * val result = httpClientProvider.get(userAgent = HttpClientUserAgent.ANI).use {
 *     get("https://example.com")
 * }
 * ```
 */
fun HttpClientProvider.get(
    userAgent: ScopedHttpClientUserAgent = ScopedHttpClientUserAgent.ANI,
    serverListConfig: ServerListFeatureConfig = ServerListFeatureConfig.Default,
    useAniToken: Boolean = false,
): ScopedHttpClient = get(
    setOf(
        UserAgentFeature.withValue(userAgent),
        ServerListFeature.withValue(serverListConfig),
        ConvertSendCountExceedExceptionFeature.withValue(true),
        UseAniTokenFeature.withValue(useAniToken),
        VersionExpiryFeature.withValue(true),
    ),
)

/**
 * 默认的 [HttpClientProvider] 实现，用于在应用生命周期内维护可重用的 [HttpClient] 实例，并根据代理配置自动切换。
 *
 * 此类会维护一个对象池 ([ReuseObjectPool])，针对不同 [ScopedHttpClientUserAgent] 和当前代理配置的组合进行复用。
 * 同时支持对代理设置 ([ProxyProvider]) 的监听，一旦检测到代理变更，会自动使新实例生效并回收旧实例。
 *
 * @property proxyProvider 外部提供的代理配置源，当代理更新时会触发内部自动重建或刷新相应的 [HttpClient]。
 * @property backgroundScope 运行代理监听等后台协程的作用域。
 */
class DefaultHttpClientProvider(
    private val proxyProvider: ProxyProvider,
    private val backgroundScope: CoroutineScope,
    featureHandlers: List<ScopedHttpClientFeatureHandler<*>> = listOf(UserAgentFeatureHandler),
) : HttpClientProvider() {
    // must have stable `equals`
    private data class Matrix(
        val features: Set<ScopedHttpClientFeatureKeyValue<*>>, // map value type is union of the feature's value type and NOT_REQUESTED 
        val proxyConfig: ProxyConfig?,
    )

    data class HoldingInstanceMatrix(
        val features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    )

    @Suppress("UNCHECKED_CAST") // for convenience
    private val featureHandlers: Map<ScopedHttpClientFeatureKey<Any?>, ScopedHttpClientFeatureHandler<Any?>> =
        featureHandlers.associateBy { it.key } as Map<ScopedHttpClientFeatureKey<Any?>, ScopedHttpClientFeatureHandler<Any?>>

    private val clientLogger = logger<HttpClientProvider>()

    private val pool = ReuseObjectPool<Matrix, HttpClient>(
        newInstance = { createClient(it.features, it.proxyConfig) },
        onRelease = { it.close() },
    )

    private val proxyListeningStarted = atomic(false)
    private var proxyListeningJob: Job? = null

    /**
     * 当前的代理配置, 只会在 [startProxyListening] 里赋值.
     */
    private val currentProxyConfig = MutableStateFlow<ProxyConfig?>(null)
    override val configurationFlow: Flow<*> get() = currentProxyConfig

    @TestOnly
    fun getProxyListeningStarted(): Boolean = proxyListeningStarted.value

    @TestOnly
    fun getCurrentProxyConfig(): ProxyConfig? = currentProxyConfig.value

    private fun createClient(
        features: Set<ScopedHttpClientFeatureKeyValue<*>>,
        proxyConfig: ProxyConfig?,
    ): HttpClient {
        return createDefaultHttpClient {
            for (feature in features) {
                val handler = featureHandlers[feature.key]
                    ?: error("No handler for feature ${feature.key}")
                val value = feature.value
                if (value != FEATURE_NOT_SET) {
                    handler.applyToConfig(this, value)
                }
            }
            proxy(proxyConfig?.toClientProxyConfig())
        }.apply {
            registerLogging(clientLogger)

            for (feature in features) {
                val handler = featureHandlers[feature.key]
                    ?: error("No handler for feature ${feature.key}")
                val value = feature.value
                if (value != FEATURE_NOT_SET) {
                    handler.applyToClient(this, value)
                }
            }
        }
    }

    /**
     * 启动代理监听，在协程中持续订阅 [proxyProvider] 提供的代理流：
     * 1. 第一次调用会挂起，直到拿到首个代理配置。
     *    调用方 (APP 初始化时) 需要阻塞并且等待此函数返回, 否则可能会导致有异步请求不经过代理而失败.
     * 2. 只能调用一次，若重复调用将抛出异常。
     * 3. 若获取期间发生异常，会取消内部协程并向上抛出。
     *
     * 当代理发生变化时，新的 [HttpClient] 会被创建并应用；原有实例在引用计数降为 0 后被回收。
     *
     * @throws IllegalStateException 如果已经被调用过一次。
     */
    suspend fun startProxyListening(
        holdReferences: Sequence<HoldingInstanceMatrix>,
    ) {
        if (!proxyListeningStarted.compareAndSet(expect = false, update = true)) {
            error("Proxy listening already started")
        }

        val flowScope =
            backgroundScope.coroutineContext.childScope(CoroutineName("HttpClientProvider.ProxyListening"))
        try {
            val proxyConfigFlow =
                proxyProvider.proxy.stateIn(flowScope) // suspends until the first proxy is ready
            val firstValueReady = CompletableDeferred<Unit>()

            flowScope.launch {
                proxyConfigFlow.collectLatest {
                    // When this function ends, we should have a proxy set to currentProxyConfig.
                    currentProxyConfig.value = it
                    firstValueReady.complete(Unit)

                    coroutineScope {
                        // We hold references to all permutations of matrix params.
                        for ((features) in holdReferences) {
                            launch {
                                // `get` here always create a new instance with the latest proxy config, because 
                                get(features).use {
                                    this.engineConfig.setProxy(it?.toClientProxyConfig())
                                    awaitCancellation() // hold the instance until the scope is cancelled (i.e. until next proxy)
                                }
                            }
                        }
                    }
                }
            }
            firstValueReady.await()
        } catch (e: Throwable) {
            // In the very unlikely case of an exception, we cancel the scope to avoid memory leak.
            flowScope.cancel(
                CancellationException(
                    "Failed to start proxy listening, cancelling premature scope",
                    e,
                ),
            )
            throw e
            // proxyListeningStarted is still true - further calls to startProxyListening will fail.
        }
        proxyListeningJob = flowScope.coroutineContext.job
    }

    override fun get(features: Set<ScopedHttpClientFeatureKeyValue<*>>): ScopedHttpClient {
        return ScopedHttpClientImpl(features.extendWithNotSet())
    }

    private fun Set<ScopedHttpClientFeatureKeyValue<*>>.extendWithNotSet(): Set<ScopedHttpClientFeatureKeyValue<*>> {
        return featureHandlers.keys.mapTo(mutableSetOf()) { key ->
            this.find { it.key == key } ?: ScopedHttpClientFeatureKeyValue.createNotSet(key)
        }
    }

    private inner class ScopedHttpClientImpl(
        private val features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    ) : ScopedHttpClient() {
        @UnsafeScopedHttpClientApi
        override fun borrow(): Ticket {
            val myMatrix = Matrix(features, currentProxyConfig.value)
            return TicketImpl(myMatrix, pool.borrow(myMatrix))
        }

        @UnsafeScopedHttpClientApi
        override fun returnClient(ticket: Ticket) {
            check(ticket is TicketImpl) { "Ticket must be an instance of TicketImpl. Do not implement the Ticket interface. Do not use delegation (`by`) keyword for this type." }
            return pool.release(ticket.matrix, ticket.client)
        }

        override fun toString(): String = "WrapperHttpClientImpl(features=$features)"
    }

    /**
     * @see ScopedHttpClientImpl.borrow
     */
    @UnsafeScopedHttpClientApi
    private data class TicketImpl(
        val matrix: Matrix,
        override val client: HttpClient,
    ) : ScopedHttpClient.Ticket // `data` class to generate `toString`

    /**
     * Releases all background jobs to unblock test scope when test finished.
     */
    @TestOnly
    suspend fun forceReleaseAll() {
        // Wait for the coroutines to return clients. 
        // If we `forceReleaseAll` before the coroutines return the clients, [ReuseObjectPool.release] will fail because the matrix don't exist in the pool.
        proxyListeningJob?.cancelAndJoin()
        proxyListeningJob = null
        @OptIn(TestOnly::class)
        pool.forceReleaseAll()
    }
}


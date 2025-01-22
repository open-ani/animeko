/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.platform.SystemProxyDetector
import me.him188.ani.utils.ktor.setProxy
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.time.Duration.Companion.hours

interface ProxyProvider {
    val proxy: Flow<ProxyConfig?>
}

suspend fun ProxyProvider.collectProxyTo(client: HttpClient) {
    proxy.collect {
        client.engineConfig.setProxy(it?.toClientProxyConfig())
    }
}

data object NoProxyProvider : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> = flowOf(null)
}

data class ConstantProxyProvider(
    val value: ProxyConfig?,
) : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> = flowOf(value)
}

class SystemProxyProvider(
    backgroundScope: CoroutineScope,
) : ProxyProvider {
    private val logger = logger<SystemProxyDetector>()

    override val proxy: Flow<ProxyConfig?> = flow {
        while (currentCoroutineContext().isActive) {
            val defaultProxy = SystemProxyDetector.instance.detect()

            if (defaultProxy == null) {
                emit(null)
            } else {
                emit(ProxyConfig(url = defaultProxy.url.toString()))
            }

            delay(1.hours)
        }
    }.distinctUntilChanged()
        .onEach {
            logger.info { "Detected system proxy: $it" }
        }
        .shareIn(backgroundScope, started = SharingStarted.WhileSubscribed(), replay = 1)
}


class SettingsBasedProxyProvider(
    private val settingsRepository: SettingsRepository,
    backgroundScope: CoroutineScope,
) : ProxyProvider by ProxySettingsFlowProxyProvider(
    settingsRepository.proxySettings.flow, backgroundScope,
)

class ProxySettingsFlowProxyProvider(
    private val flow: Flow<ProxySettings>,
    backgroundScope: CoroutineScope,
) : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> by lazy {
        flow.map { it.default }
            .distinctUntilChanged()
            .transformLatest { settings ->
                coroutineScope {
                    val provider = when (settings.mode) {
                        ProxyMode.DISABLED -> NoProxyProvider
                        ProxyMode.SYSTEM -> SystemProxyProvider(this)
                        ProxyMode.CUSTOM -> ConstantProxyProvider(settings.customConfig)
                    }

                    emitAll(provider.proxy)
                }
            }
            .shareIn(backgroundScope, started = SharingStarted.WhileSubscribed(), replay = 1)
    }
}
/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.session.AniApiProvider
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.restartable

/**
 * You should call [testRunnerLoop] to start the test runner loop and make functionality.
 */
class ProxyTester(
    clientProvider: HttpClientProvider,
    flowScope: CoroutineScope
) {
    private val proxyTestRunning = FlowRunning()
    private val proxyTestRestarter = FlowRestarter()

    private val connectionTester = clientProvider.configurationFlow.map {
        val client = clientProvider.get(
            setOf(ServerListFeature.withValue(ServerListFeatureConfig.Default)),
        )

        ServiceConnectionTesters.createDefault(
            bangumiClient = BangumiClientImpl(client),
            aniClient = AniApiProvider(client).trendsApi,
        )
    }
        .shareIn(
            flowScope,
            SharingStarted.WhileSubscribed(),
            replay = 1
        )

    val testResult = connectionTester.flatMapLatest { it.results }
    val testRunning = proxyTestRunning.isRunning

    suspend fun testRunnerLoop() {
        connectionTester
            .restartable(restarter = proxyTestRestarter)
            .collectLatest { tester ->
                proxyTestRunning.withRunning { tester.testAll() }
            }
    }

    fun restartTest() {
        proxyTestRestarter.restart()
    }
}


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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.trace.ErrorReport
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.restartable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
private val isFirstTestResult = AtomicBoolean(true)

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
            replay = 1,
        )

    val testResult = connectionTester.flatMapLatest { it.results }
        .onEach {
            checkResultAndReport(it)
        }

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

@OptIn(ExperimentalAtomicApi::class)
private fun checkResultAndReport(results: ServiceConnectionTester.Results) {
    if (results.anyFailed() && results.allCompleted() // 有失败的测试
        && isFirstTestResult.compareAndSet(expectedValue = true, newValue = false) // 只上报一次
    ) {
        // 上报未知错误
        for ((service, state) in results.idToStateMap) {
            if (state is ServiceConnectionTester.TestState.Error) {
                // unknown error, report
                ErrorReport.captureException(
                    ServiceTestUnknownErrorException(
                        message = "Service '$service' test failed with unknown exception",
                        cause = state.e,
                    ),
                )
                break // 只上报第一个
            }
        }

        // 上报网络检查失败
        reportNetworkCheckFailed(results)
    }
}

private fun reportNetworkCheckFailed(results: ServiceConnectionTester.Results) {
    Analytics.recordEvent(
        AnalyticsEvent.NetworkCheckFailed,
        results.idToStateMap.map { (id, state) ->
            "network_check_$id" to stateToString(state)
        }.toMap(),
    )
}

private fun stateToString(state: ServiceConnectionTester.TestState): String = when (state) {
    is ServiceConnectionTester.TestState.Error -> "error"
    ServiceConnectionTester.TestState.Failed -> "failed"
    ServiceConnectionTester.TestState.Idle -> "idle"
    is ServiceConnectionTester.TestState.Success -> "success"
    ServiceConnectionTester.TestState.Testing -> "testing"
}

// Named exception for better error reporting
private class ServiceTestUnknownErrorException(override val message: String?, override val cause: Throwable?) :
    Exception()

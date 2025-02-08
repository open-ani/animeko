/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.settings.ServiceConnectionTester.Service
import me.him188.ani.app.domain.settings.ServiceConnectionTester.TestState
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.measureTimedValue

/**
 * Orchestrates the concurrent testing of multiple [Service] instances.
 *
 * Each [Service] is tested asynchronously when [testAll] is called. The state of each service
 * transitions through [TestState] according to the outcome of its [Service.test] function.
 *
 * - If [testAll] is called again while a previous test run is still in progress,
 *   the existing tasks are canceled and set to [TestState.Idle], and new tasks begin.
 * - If the caller's coroutine (that invokes [testAll]) is canceled, all testing coroutines
 *   are also canceled, and their states revert to [TestState.Idle].
 * - [stopAll] can be invoked manually to cancel any ongoing tests and reset all states to [TestState.Idle].
 *
 * This class is **thread-safe** and can be called from multiple coroutines/threads concurrently.
 *
 * @param defaultDispatcher coroutine dispatcher to run the tests ([Service.test]) and results aggregation.
 *
 * @see ServiceConnectionTesters.createDefault
 */
class ServiceConnectionTester(
    services: List<Service>,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) {
    private val services = services.map { ServiceImpl(it) }

    /**
     * A [Flow] of [Results], which contains the current [TestState] of all [Service]s being tested.
     */
    val results: Flow<Results> =
        combine(this.services.map { service -> service.state.map { service.service to it } }) { states ->
            Results(states.toMap(LinkedHashMap())) // retain order
        }.shareIn(
            CoroutineScope(defaultDispatcher), // note: we can't use backgroundScope here because backgroundScope may have a Job, which is not accepted by shareIn.
            started = SharingStarted.WhileSubscribed(), replay = 0,
        )

    private val singleTaskExecutor = SingleTaskExecutor(defaultDispatcher)

    /**
     * Start testing all services and suspend until all services are tested.
     *
     * Lifecycle of the testing task is bounded by this function.
     * That is, is this function is cancelled, all testing coroutines are also cancelled.
     * Calling this function the second time will cancel the previous call.
     */
    suspend fun testAll() {
        singleTaskExecutor.invoke {
            for (service in services) {
                launch {
                    service.test()
                }
            }
        }
    }

    /**
     * Stop all testing.
     *
     * This cancels all testing coroutines and results running services' states to [TestState.Idle],
     * but does not clear the completed states.
     */
    fun stopAll() {
        singleTaskExecutor.cancelCurrent()
    }

    class Service(
        /**
         * 给调用方识别的 ID. [ServiceConnectionTester] 不会使用此 ID.
         */
        val id: String,
        /**
         * Test if this service is available.
         *
         * This function is not allowed to throw exceptions, otherwise it will become [TestState.Error] and is considered a bug.
         */
        val test: suspend () -> Boolean,
    )

    sealed class TestState {
        // also initial state
        data object Idle : TestState()

        data object Testing : TestState()
        data class Success(
            val time: Duration,
        ) : TestState()

        /**
         * Indicates a normal failure, e.g., HTTP status code is not 200.
         */
        data object Failed : TestState()

        /**
         * Indicates an unexpected error, e.g., an exception is thrown.
         * This should be considered a bug.
         */
        data class Error(
            val e: Throwable,
        ) : TestState()
    }

    class Results internal constructor(
        internal val states: Map<Service, TestState>,
    ) {
        val idToStateMap: Map<String, TestState> by lazy { states.mapKeys { it.key.id } }

        fun findStateById(id: String): TestState? = states.keys.find { it.id == id }?.let { states[it] }
    }

    private class ServiceImpl(
        val service: Service,
    ) {
        private val _state: MutableStateFlow<TestState> = MutableStateFlow(TestState.Idle)
        val state: StateFlow<TestState> = _state.asStateFlow()
        private val lock = Mutex()

        /**
         * Test the service.
         *
         * This function must be called by only one coroutine at a time, otherwise it throws.
         *
         * If the coroutine is cancelled, the state is re-set to [TestState.Idle] and the [CancellationException] is propagated.
         */
        suspend fun test() {
            // Note that we set `owner=this` (which is always the same), 
            // so that the lock basically ensures the function is always called by a single coroutine at a time.
            // This is a strong assertion to ensure the `testAll` algorithm works correctly.
            lock.withLock(owner = this) {
                _state.value = TestState.Testing
                try {
                    val (res, t) = measureTimedValue { service.test() }
                    _state.value = if (res) TestState.Success(t) else TestState.Failed
                } catch (e: CancellationException) {
                    _state.value = TestState.Idle
                    throw e
                } catch (e: Throwable) {
                    _state.value = TestState.Error(e)
                }
            }
        }

        fun resetToIdle() {
            _state.value = TestState.Idle
        }
    }
}


object ServiceConnectionTesters {
    const val ID_BANGUMI = "BANGUMI"
    const val ID_BANGUMI_NEXT = "BANGUMI_NEXT"
    const val ID_ANI = "ANI"

    fun createDefault(
        bangumiClient: BangumiClient,
        aniClient: ApiInvoker<TrendsAniApi>,
        defaultDispatcher: CoroutineContext = Dispatchers.Default,
    ): ServiceConnectionTester {
        return ServiceConnectionTester(
            listOf(
                Service(ID_BANGUMI) {
                    bangumiClient.testConnectionMaster() == ConnectionStatus.SUCCESS
                },
                Service(ID_BANGUMI_NEXT) {
                    bangumiClient.testConnectionNext() == ConnectionStatus.SUCCESS
                },
                Service(ID_ANI) {
                    runCatching {
                        // Note, we may have `expectSuccess = true` so on failure it will throw an exception.
                        aniClient.invoke {
                            getTrends().response.status.isSuccess()
                        }
                    }.getOrElse { false }
                },
            ),
            defaultDispatcher,
        )

    }
}

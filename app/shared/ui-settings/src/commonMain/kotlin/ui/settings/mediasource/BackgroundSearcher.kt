/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package me.him188.ani.app.ui.settings.mediasource

import androidx.annotation.UiThread
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.settings.mediasource.BackgroundSearcher.RestartSearchScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 使用测试数据 [TestData] 运行测试 [TestResult].
 * @see DefaultBackgroundSearcher
 *
 * @param TestData 测试数据, 将所有[搜索][restartSearchImpl]时需要的数据都封装在这个类中, 以便 debounce
 */
@Stable
abstract class BackgroundSearcher<TestData, TestResult>(
    backgroundScope: CoroutineScope,
) {
    /**
     * 当前的测试数据. 测试数据的变工
     */
    private val testDataFlow: MutableStateFlow<TestData?> = MutableStateFlow(null)

    private val _searchResultFlow: MutableStateFlow<TestResult?> = MutableStateFlow(null)
    val searchResultFlow: StateFlow<TestResult?> = _searchResultFlow.asStateFlow()

    private val searchTasker = MonoTasker(backgroundScope)

    /**
     * 是否有查询正在后台进行中. 也差不多就是 [restartSearchImpl] 是否正在执行.
     */
    val isSearching get() = searchTasker.isRunning

    interface RestartSearchScope<TestResult> {
        /**
         * 一个没有作用的接口, 用来确保让 [restartSearchScopeImpl] 的实现调用 [launchRequestInBackground]
         */
        abstract class OK internal constructor()

        /**
         * 立即完成, 而不启动后台协程.
         */
        fun complete(result: TestResult): OK

        /**
         * 在 [searchTasker] 启动协程, 执行 [request].
         * @param request 保证在后台线程执行
         *
         * @see launchCollectedInBackground
         */
        fun launchRequestInBackground(request: suspend () -> TestResult): OK
    }

    private val restartSearchScopeImpl = object : RestartSearchScope<TestResult>, RestartSearchScope.OK() {
        override fun complete(result: TestResult): RestartSearchScope.OK {
            _searchResultFlow.value = result
            return this
        }

        override fun launchRequestInBackground(request: suspend () -> TestResult): RestartSearchScope.OK {
            searchTasker.launch {
                // background scope
                val res = request()
                _searchResultFlow.value = res
            }
            return this
        }
    }

    /**
     * 清空
     */
    @UiThread
    fun restartSearch(testData: TestData) {
        testDataFlow.value = testData
        _searchResultFlow.value = null // ui scope
        restartSearchScopeImpl.restartSearchImpl(testData)
    }

    @UiThread
    fun cancelSearch() {
        searchTasker.cancel()
    }

    /**
     * 当执行搜索时调用.
     *
     * Sample implementation:
     * ```
     * val query = RssSearchQuery(
     *     subjectName = testData.keyword,
     *     episodeSort = EpisodeSort(sort),
     * )
     * // 因为这是在 UI 线程内, 可以更新 UI 状态清除正在查看详情的 item
     * // 但注意不要读取涉及搜索参数的状态 - 这会跳过 debounce. 需要将所有参数都封装为 TestData 才能 debounce.
     * viewingItem = null
     * return launchRequestInBackground {
     *     // 后台线程内执行搜索
     *     doSearch(testData, query)
     * }
     * ```
     */
    @UiThread
    protected abstract fun RestartSearchScope<TestResult>.restartSearchImpl(testData: TestData): RestartSearchScope.OK

    @UiThread
    fun restartCurrentSearch() {
        testDataFlow.value?.let {
            restartSearch(it)
        }
    }
}

@UiThread
suspend fun <TestData, TestResult> BackgroundSearcher<TestData, TestResult>.observeTestDataChanges(testDataState: State<TestData>) {
    try {
        snapshotFlow { testDataState.value }
            .distinctUntilChanged()
            .debounce(0.5.seconds)
            .collect {
                restartSearch(it)
            }
    } catch (e: CancellationException) {
        cancelSearch()
        throw e
    }
}

/**
 * @see search 当测试数据变化时重新搜索调用
 */
fun <TestData, TestResult> BackgroundSearcher(
    backgroundScope: CoroutineScope,
    @UiThread search: RestartSearchScope<TestResult>.(testData: TestData) -> RestartSearchScope.OK,
) = DefaultBackgroundSearcher(backgroundScope, search)

@Stable
class DefaultBackgroundSearcher<TestData, TestResult>(
    backgroundScope: CoroutineScope,
    val restartSearchImpl: RestartSearchScope<TestResult>.(testData: TestData) -> RestartSearchScope.OK,
) : BackgroundSearcher<TestData, TestResult>(backgroundScope) {
    override fun RestartSearchScope<TestResult>.restartSearchImpl(testData: TestData): RestartSearchScope.OK =
        restartSearchImpl.invoke(this, testData)
}


/**
 * 在 [BackgroundSearcher.searchTasker] 启动协程, 执行 [block], 每 emit 的值都会被收集到 list 中, 然后由 flow 更新.
 * @param block 保证在后台线程执行
 *
 * @sample me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodeState.searcher
 */
inline fun <T, TestResult> RestartSearchScope<TestResult>.launchCollectedInBackground(
    updateState: (flow: StateFlow<PersistentList<T>>) -> TestResult,
    crossinline block: suspend SafeResultCollector<T>.(flow: StateFlow<PersistentList<T>>) -> TestResult,
): RestartSearchScope.OK {
    val flow = MutableStateFlow(persistentListOf<T>())
    val result = flow.asStateFlow()
    complete(updateState(result))

    return launchRequestInBackground {
        block(SafeResultCollectorImpl(flow), flow)
    }
}

// single class. 否则每次 inline 都会多一个 class.
@PublishedApi
internal class SafeResultCollectorImpl<T>(
    private val flow: MutableStateFlow<PersistentList<T>>,
) : SafeResultCollector<T>() {
    override fun emitImpl(value: T) {
        flow.value = flow.value.add(value)
    }
}

abstract class SafeResultCollector<T>
@PublishedApi internal constructor() {
    private val lock = SynchronizedObject()

    fun collect(value: T) { // 与 flow 相比, 这个不需要 suspend, 而且这个是线程安全的
        synchronized(lock) { // light-weight lock
            emitImpl(value)
        }
    }

    abstract fun emitImpl(value: T)
}

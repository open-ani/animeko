/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.testFramework

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorEvents
import me.him188.ani.app.domain.media.selector.PreferWebSourceEvent
import me.him188.ani.app.domain.media.selector.SelectEvent
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * [MediaSelectorEvents] 四条事件流中的一个事件, 携带全局序号 [index] (按 emit 顺序).
 *
 * 每个事件都记录 emit 时刻的 [MediaSelector.selected], 用于钉住副作用之间的相对顺序
 * (例如 SEL-02 要求 `selected.value = candidate` 发生在 onBeforeSelect 之后, onChangePreference 之前).
 */
sealed class RecordedMediaSelectorEvent {
    abstract val index: Int

    /**
     * emit 时刻的 [MediaSelector.selected] 值.
     */
    abstract val selectedAtEmit: Media?

    data class OnBeforeSelect(
        override val index: Int,
        val event: SelectEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnSelect(
        override val index: Int,
        val event: SelectEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnChangePreference(
        override val index: Int,
        val preference: MediaPreference,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnPreferWebSource(
        override val index: Int,
        val event: PreferWebSourceEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()
}

class CollectedMediaSelectorEvents {
    private val lock = Mutex()
    private val mutableRecords: MutableList<RecordedMediaSelectorEvent> = mutableListOf()

    /**
     * 已收集到的事件快照.
     *
     * 只应在 [collectEvents] 返回之后读取: 那时全部收集协程都已经被 join, 与写入之间存在 happens-before.
     * 见 [collectEvents] 的 "线程边界" 一节.
     */
    val records: List<RecordedMediaSelectorEvent> get() = mutableRecords.toList()

    /**
     * 在锁内追加一条记录. [create] 收到的是这条记录的全局序号.
     *
     * 用锁而不是裸 [ArrayList] 是因为写入可能发生在工作线程上, 见 [collectEvents] 的 "线程边界" 一节.
     * 无竞争时 [Mutex.withLock] 走 fast path 不挂起, 因此不破坏 "在 emit 调用栈内同步消费" 的性质.
     */
    internal suspend fun add(create: (index: Int) -> RecordedMediaSelectorEvent) {
        lock.withLock {
            mutableRecords.add(create(mutableRecords.size))
        }
    }

    val onBeforeSelect: List<RecordedMediaSelectorEvent.OnBeforeSelect>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnBeforeSelect>()
    val onSelect: List<RecordedMediaSelectorEvent.OnSelect>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnSelect>()
    val onChangePreference: List<RecordedMediaSelectorEvent.OnChangePreference>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnChangePreference>()
    val onPreferWebSource: List<RecordedMediaSelectorEvent.OnPreferWebSource>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnPreferWebSource>()

    fun assertOrder(vararg expected: KClass<out RecordedMediaSelectorEvent>) {
        val actual = records
        assertEquals(expected.toList(), actual.map { it::class }, "Actual records: $actual")
    }

    fun expectNoEvents() {
        assertEquals(emptyList(), records)
    }
}

/**
 * 以 [CoroutineStart.UNDISPATCHED] + [Dispatchers.Unconfined] 订阅 [MediaSelector.events] 的四条流,
 * 在 [block] 执行期间把事件按 emit 顺序收集到带全局序号的单一列表.
 *
 * ## 同步消费的前提条件
 *
 * 事件流是 replay=0 + extraBufferCapacity=1 + DROP_OLDEST (EVT-01), 因此 "不丢事件" 与
 * "[RecordedMediaSelectorEvent.selectedAtEmit] 观察到 emit 时刻的状态" 这两条结论**不是无条件的**,
 * 它们依赖收集器在 emit 的调用栈内**同步**消费. 而 [Dispatchers.Unconfined] 只有在
 * **emit 所在线程上没有活跃的 unconfined event loop** 时才会就地 resume:
 * 其 resume 逻辑是 `if (eventLoop.isUnconfinedLoopActive) dispatchUnconfined() else 就地执行`.
 *
 * 当前全部 emit 都来自 `DefaultMediaSelector.selectImpl` / `MediaPreferenceItem.prefer` 的调用栈
 * (调用方是测试协程或 `withContext(flowCoroutineContext)`), 前提成立. 一旦将来从 Unconfined 协程内触发 emit,
 * 或把这里改成 Channel 式收集, extraBufferCapacity=1 + DROP_OLDEST 会吞掉较早的事件,
 * [RecordedMediaSelectorEvent.selectedAtEmit] 也会读到已经被更新过的值 —— 届时本工具的断言会静默失真.
 *
 * ## 线程边界
 *
 * `MediaPreferenceItem.prefer` / `removePreference` 走 `withContext(flowCoroutineContext)`,
 * 而 `enableCaching = false` 时 flowCoroutineContext 是真实的 [Dispatchers.Default] 线程池
 * (见 [SimpleMediaSelectorTestSuite]), 所以记录的写入可能发生在工作线程上.
 * 因此 [CollectedMediaSelectorEvents] 内部用锁保护写入;
 * 读取则应在本函数返回之后进行 —— 此时 `cancellableCoroutineScope` 已经 join 掉全部收集协程.
 *
 * ## [block] 结束后只 yield 一次
 *
 * [block] 返回后只 `yield()` 一次, 这只够让**已经排在队列里**的续体跑完.
 * 若被测代码把 emit 放在后台协程里 (例如自动选择), 那么 "没有更多事件" 一类的断言会假绿.
 * 这类场景必须在 [block] 内部自行把调度器推进到事件确定已经产生 (`advanceUntilIdle` 等) 再返回.
 */
suspend fun MediaSelector.collectEvents(
    block: suspend () -> Unit,
): CollectedMediaSelectorEvents {
    val collected = CollectedMediaSelectorEvents()
    cancellableCoroutineScope {
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onBeforeSelect.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnBeforeSelect(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onSelect.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnSelect(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onChangePreference.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnChangePreference(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onPreferWebSource.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnPreferWebSource(index, it, selected.value) }
            }
        }
        try {
            block()
            yield()
        } finally {
            cancelScope()
        }
    }
    return collected
}

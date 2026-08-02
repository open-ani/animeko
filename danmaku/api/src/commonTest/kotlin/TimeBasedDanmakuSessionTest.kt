/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * @see DanmakuSessionAlgorithmTest
 */
internal class TimeBasedDanmakuSessionTest {
    suspend fun create(
        sequence: List<DanmakuInfo>,
    ): DanmakuCollection = TimeBasedDanmakuSession.create(
        flowOf(sequence),
        coroutineContext = currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext,
    )

    @Test
    fun empty() = runTest {
        create(emptyList())
    }

    @Test
    fun `before all`() = runTest {
        val instance = create(
            listOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
            ),
        )
        val list = instance.at(
            flowOf(0.seconds),
            { 1f },
            danmakuRegexFilterList = flowOf(emptyList()),
        ).events.toList()
        assertEquals(0, list.size)
    }

    /**
     * 弹幕列表迟于播放进度到达时 (每次 DanmakuSession 重建都会如此), 应当重新装填屏幕,
     * 而不是把当前时间之前的所有弹幕逐条当作新弹幕发出.
     */
    @Test
    fun `list arriving after progress repopulates`() = runTest {
        val listFlow = MutableSharedFlow<List<DanmakuInfo>>(replay = 1)
        listFlow.emit(emptyList())
        val session = TimeBasedDanmakuSession.create(
            listFlow,
            coroutineContext = currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext,
        ).at(
            MutableStateFlow(60.seconds),
            { 1f },
            danmakuRegexFilterList = MutableStateFlow(emptyList()),
        )

        val events = mutableListOf<DanmakuEvent>()
        val job = launch { session.events.collect { events.add(it) } }

        // 列表还没到, 先清空屏幕
        advanceTimeBy(100)
        assertEquals(emptyList(), assertIs<DanmakuEvent.Repopulate>(events.last()).list)
        events.clear()

        listFlow.emit((1..50).map { dummyDanmaku(it.toDouble()) })
        advanceTimeBy(100)

        // 只装填 repopulateDistance (20s) 以内的弹幕, 而不是 50 条 Add
        assertEquals(
            (41..50).map { it * 1000L },
            assertIs<DanmakuEvent.Repopulate>(events.single()).list.map { it.playTimeMillis },
        )
        job.cancel()
    }

    @Test
    fun `test filter`() = runTest {
        val danmakuList = listOf(
            dummyDanmaku(1.0, "1"),
            dummyDanmaku(2.0, "2"),
            dummyDanmaku(3.0, "3"),
        )
        val filteredList = TimeBasedDanmakuSession.filterList(danmakuList, listOf(".*"))
        assertEquals(emptyList(), filteredList)
    }

    private fun dummyDanmaku(timeSecs: Double, text: String = "$timeSecs") =
        dummyDanmaku((timeSecs * 1000).toLong(), text)

    private fun dummyDanmaku(timeMillis: Long, text: String = "$timeMillis") =
        DanmakuInfo(text, DanmakuServiceId("dummy"), text, DanmakuContent(timeMillis, 0, text, DanmakuLocation.NORMAL))
}
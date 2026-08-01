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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
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
            filterSpec = flowOf(DanmakuFilterSpec.Empty),
        ).events.toList()
        assertEquals(0, list.size)
    }

    @Test
    fun `test filter`() = runTest {
        val danmakuList = listOf(
            dummyDanmaku(1.0, "1"),
            dummyDanmaku(2.0, "2"),
            dummyDanmaku(3.0, "3"),
        )
        val filteredList = TimeBasedDanmakuSession.filterList(
            danmakuList,
            DanmakuFilterSpec(regexPatterns = listOf(".*")),
        )
        assertEquals(emptyList(), filteredList)
    }

    @Test
    fun `regex filter is converted to the same script as the danmaku`() {
        val danmakuList = listOf(
            dummyDanmaku(1.0, "剧透警告"), // 加载时已经转成简体
            dummyDanmaku(2.0, "无关弹幕"),
        )
        // 用繁体写的过滤器也应该拦得住简体弹幕
        val filtered = TimeBasedDanmakuSession.filterList(
            danmakuList,
            DanmakuFilterSpec(
                regexPatterns = listOf("劇透"),
                zhConversion = ZhConversion.TO_SIMPLIFIED,
            ),
        )
        assertEquals(listOf("无关弹幕"), filtered.map { it.text })

        // 不开简繁转换时就拦不住
        assertEquals(
            listOf("剧透警告", "无关弹幕"),
            TimeBasedDanmakuSession.filterList(
                danmakuList,
                DanmakuFilterSpec(regexPatterns = listOf("劇透")),
            ).map { it.text },
        )
    }

    private fun dummyDanmaku(timeSecs: Double, text: String = "$timeSecs") =
        dummyDanmaku((timeSecs * 1000).toLong(), text)

    private fun dummyDanmaku(timeMillis: Long, text: String = "$timeMillis") =
        DanmakuInfo(text, DanmakuServiceId("dummy"), text, DanmakuContent(timeMillis, 0, text, DanmakuLocation.NORMAL))
}
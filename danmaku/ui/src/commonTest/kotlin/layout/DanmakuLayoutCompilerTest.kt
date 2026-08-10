/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui.layout

import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DanmakuLayoutCompilerTest {
    private val params = DanmakuLayoutParams(
        trackWidthPx = 1000,
        floatingTrackCount = 3,
        topTrackCount = 2,
        bottomTrackCount = 2,
        baseSpeedPxPerSecond = 100f,
        playbackSpeed = 1f,
        safeSeparationPx = 10f,
        baseSpeedTextWidthPx = 100,
        speedMultiplier = 1f, // 宽度不影响速度, 便于手算
        speedFluctuation = 0f, // 无随机波动, 便于手算
        fixedDanmakuDurationMillis = 5000,
    )

    private fun danmaku(
        id: String,
        timeMillis: Long,
        location: DanmakuLocation = DanmakuLocation.NORMAL,
    ) = DanmakuPresentation(
        DanmakuInfo(
            id, DanmakuServiceId("test"), "sender",
            DanmakuContent(timeMillis, 0xffffff, "text-$id", location),
        ),
        isSelf = false,
    )

    private fun compile(
        list: List<DanmakuPresentation>,
        params: DanmakuLayoutParams = this.params,
        width: (DanmakuPresentation) -> Int = { 100 },
        previous: CompiledDanmakuLayout? = null,
        freezeBeforeMillis: Long = Long.MIN_VALUE,
    ) = DanmakuLayoutCompiler.compile(list, params, width, previous, freezeBeforeMillis)

    @Test
    fun `single floating danmaku placement`() {
        val layout = compile(listOf(danmaku("a", 10_000)))

        assertEquals(1, layout.floating.size)
        val placed = layout.floating.single()
        assertEquals(0, placed.trackIndex)
        assertEquals(10_000, placed.enterTimeMillis)
        assertEquals(100f, placed.speedPxPerVideoSecond)
        // (1000 + 100 + 10) / 100 px/s = 11.1s
        assertEquals(21_100, placed.exitTimeMillis)
        assertEquals(0f, placed.distanceXAt(10_000))
        assertEquals(100f, placed.distanceXAt(11_000))
        assertEquals(0, layout.droppedCount)
    }

    @Test
    fun `same track requires spacing`() {
        // (100 + 10) / 100 px/s = 1100ms 后前一条才完全进入轨道
        val layout = compile(
            listOf(danmaku("a", 0), danmaku("b", 1000), danmaku("c", 1200)),
        )

        assertEquals(listOf(0, 1, 0), layout.floating.map { it.trackIndex })
    }

    @Test
    fun `saturated tracks drop danmaku`() {
        val layout = compile(
            listOf(danmaku("a", 0), danmaku("b", 100), danmaku("c", 200)),
            params = params.copy(floatingTrackCount = 1),
        )

        assertEquals(1, layout.floating.size)
        assertEquals("a", layout.floating.single().presentation.danmaku.id)
        assertEquals(2, layout.droppedCount)
    }

    @Test
    fun `compilation is deterministic and fluctuation varies by id`() {
        val list = List(20) { danmaku("d$it", it * 2000L) }
        val realParams = params.copy(speedMultiplier = 1.14f, speedFluctuation = 0.0875f)

        val layout1 = compile(list, realParams)
        val layout2 = compile(list, realParams)

        assertEquals(layout1.floating.size, layout2.floating.size)
        layout1.floating.zip(layout2.floating).forEach { (a, b) ->
            assertEquals(a.presentation.danmaku.id, b.presentation.danmaku.id)
            assertEquals(a.trackIndex, b.trackIndex)
            assertEquals(a.enterTimeMillis, b.enterTimeMillis)
            assertEquals(a.speedPxPerVideoSecond, b.speedPxPerVideoSecond)
        }
        // 随机波动由 id 哈希决定, 不同弹幕速度应当不同
        assertTrue(layout1.floating.map { it.speedPxPerVideoSecond }.distinct().size > 1)
    }

    @Test
    fun `wider danmaku moves faster`() {
        val layout = compile(
            listOf(danmaku("a", 0)),
            params = params.copy(speedMultiplier = 2f),
            width = { 200 }, // 2 倍基准宽度 -> 2 倍速度
        )

        assertEquals(200f, layout.floating.single().speedPxPerVideoSecond)
    }

    @Test
    fun `playback speed scales video-axis speed and fixed duration`() {
        val layout = compile(
            listOf(danmaku("a", 10_000), danmaku("t", 10_000, DanmakuLocation.TOP)),
            params = params.copy(playbackSpeed = 2f),
        )

        val floating = layout.floating.single()
        // 墙钟速度 100px/s, 2 倍速下每视频秒只前进 50px
        assertEquals(50f, floating.speedPxPerVideoSecond)
        assertEquals(10_000 + 22_200, floating.exitTimeMillis)

        val fixed = layout.top.single()
        // 墙钟显示 5s = 视频时间 10s
        assertEquals(10_000L, fixed.endTimeMillis - fixed.enterTimeMillis)
    }

    @Test
    fun `freeze keeps on-screen placements identical and lays out the rest around them`() {
        val a = danmaku("a", 0)
        val b = danmaku("b", 1000)
        val c = danmaku("c", 2000)
        val layout1 = compile(listOf(a, b, c))
        assertEquals(listOf(0, 1, 0), layout1.floating.map { it.trackIndex })

        // c 被移除, d 是新增的历史弹幕, e 是新增的未来弹幕
        val d = danmaku("d", 500)
        val e = danmaku("e", 30_000)
        val layout2 = compile(
            listOf(a, d, b, e),
            previous = layout1,
            freezeBeforeMillis = 2500,
        )

        val byId = layout2.floating.associateBy { it.presentation.danmaku.id }
        // 冻结的弹幕逐条复用原对象, 布局完全不变
        assertSame(layout1.floating[0], byId.getValue("a"))
        assertSame(layout1.floating[1], byId.getValue("b"))
        assertTrue("c" !in byId)
        // d 与轨道 0 的 a 和轨道 1 的冻结弹幕 b 都冲突, 只能去轨道 2
        assertEquals(2, byId.getValue("d").trackIndex)
        assertEquals(0, byId.getValue("e").trackIndex)
        assertEquals(0, layout2.droppedCount)
    }

    @Test
    fun `frozen placement beyond new track count is re-laid out`() {
        val list = listOf(danmaku("a", 0), danmaku("b", 100), danmaku("c", 200))
        val layout1 = compile(list)
        assertEquals(2, layout1.floating.first { it.presentation.danmaku.id == "c" }.trackIndex)

        val layout2 = compile(
            listOf(danmaku("c", 200)),
            params = params.copy(floatingTrackCount = 1),
            previous = layout1,
            freezeBeforeMillis = 10_000,
        )

        assertEquals(0, layout2.floating.single().trackIndex)
    }

    @Test
    fun `fixed danmaku assignment and drop`() {
        val layout = compile(
            listOf(
                danmaku("f1", 0, DanmakuLocation.TOP),
                danmaku("f2", 1000, DanmakuLocation.TOP),
                danmaku("f3", 2000, DanmakuLocation.TOP),
                danmaku("f4", 6000, DanmakuLocation.TOP),
                danmaku("bot", 0, DanmakuLocation.BOTTOM),
            ),
        )

        val byId = layout.top.associateBy { it.presentation.danmaku.id }
        assertEquals(0, byId.getValue("f1").trackIndex)
        assertEquals(1, byId.getValue("f2").trackIndex)
        assertTrue("f3" !in byId)
        assertEquals(0, byId.getValue("f4").trackIndex)
        assertEquals(1, layout.droppedCount)

        assertEquals(0, layout.bottom.single().trackIndex)
        assertEquals(5000L, layout.bottom.single().endTimeMillis - layout.bottom.single().enterTimeMillis)
    }

    @Test
    fun `fuzz - non-overlap invariant holds for all tracks`() {
        val random = Random(42)
        val list = List(500) { i ->
            val location = when (random.nextInt(10)) {
                0 -> DanmakuLocation.TOP
                1 -> DanmakuLocation.BOTTOM
                else -> DanmakuLocation.NORMAL
            }
            danmaku("d$i", random.nextLong(0, 120_000), location)
        }
        val realParams = params.copy(
            floatingTrackCount = 6,
            speedMultiplier = 1.14f,
            speedFluctuation = 0.0875f,
        )

        val layout = compile(
            list, realParams,
            width = { 50 + (it.danmaku.id.removePrefix("d").toInt() * 37) % 350 },
        )
        assertTrue(layout.floating.isNotEmpty())

        // 浮动弹幕: 同轨道任意相邻两条满足两条碰撞规则
        layout.floating.groupBy { it.trackIndex }.forEach { (track, danmakus) ->
            val sorted = danmakus.sortedBy { it.enterTimeMillis }
            sorted.zipWithNext().forEach { (prev, next) ->
                val fullyEnteredMillis =
                    (prev.widthPx + realParams.safeSeparationPx) / prev.speedPxPerVideoSecond * 1000.0
                assertTrue(
                    (next.enterTimeMillis - prev.enterTimeMillis) >= fullyEnteredMillis,
                    "track $track: ${next.presentation.danmaku.id} spawns before " +
                            "${prev.presentation.danmaku.id} fully entered",
                )
                val nextLeftArrivalMillis = next.enterTimeMillis +
                        realParams.trackWidthPx / next.speedPxPerVideoSecond * 1000.0
                assertTrue(
                    prev.exitTimeMillis <= nextLeftArrivalMillis,
                    "track $track: ${next.presentation.danmaku.id} catches up with " +
                            "${prev.presentation.danmaku.id}",
                )
            }
        }

        // 固定弹幕: 同轨道时间区间不重叠
        for (fixedList in listOf(layout.top, layout.bottom)) {
            fixedList.groupBy { it.trackIndex }.forEach { (track, danmakus) ->
                danmakus.sortedBy { it.enterTimeMillis }.zipWithNext().forEach { (prev, next) ->
                    assertTrue(
                        next.enterTimeMillis >= prev.endTimeMillis,
                        "fixed track $track: overlapping intervals",
                    )
                }
            }
        }
    }

    @Test
    fun `results are sorted by enter time`() {
        val random = Random(7)
        val list = List(200) { i -> danmaku("d$i", random.nextLong(0, 60_000)) }

        val layout = compile(list)

        assertEquals(
            layout.floating.map { it.enterTimeMillis }.sorted(),
            layout.floating.map { it.enterTimeMillis },
        )
    }
}

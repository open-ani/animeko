/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class DanmakuSessionAlgorithmTest {

    private fun create(
        sequence: Sequence<Danmaku>,
        repopulateThreshold: Duration = 3.seconds,
        repopulateDistance: Duration = 2.seconds,
    ): DanmakuSessionAlgorithm {
        return DanmakuSessionAlgorithm(
            DanmakuSessionFlowState(
                sequence.toList(),
                repopulateThreshold = repopulateThreshold,
                repopulateDistance = { repopulateDistance },
            ),
        )
    }

    private fun DanmakuSessionAlgorithm.at(time: Duration): DanmakuSessionAlgorithm {
        this.state.curTimeShared = time
        return this
    }

    private fun DanmakuSessionAlgorithm.tickCollect(maxSize: Int = Int.MAX_VALUE): List<Danmaku> {
        val list = mutableListOf<Danmaku>()
        tick {
            if (list.size >= maxSize) {
                return@tick false
            }
            assertIs<DanmakuEvent.Add>(it)
            list.add(it.danmaku)
            true
        }
        return list
    }

    private fun DanmakuSessionAlgorithm.tickCollectTime(maxSize: Int = Int.MAX_VALUE): List<Double> =
        tickCollect(maxSize).map { it.playTimeMillis / 1000.0 }

    private fun DanmakuSessionAlgorithm.tickRepopulate(): DanmakuEvent.Repopulate {
        val list = mutableListOf<DanmakuEvent.Repopulate>()
        tick {
            assertIs<DanmakuEvent.Repopulate>(it)
            if (list.isNotEmpty()) {
                fail("Expected one DanmakuEvent.Repopulate, but got more than one. Existing: $list, new: $it")
            }
            list.add(it)
            true
        }
        return list.firstOrNull() ?: fail("Expected one DanmakuEvent.Repopulate, but got none.")
    }

    private fun DanmakuSessionAlgorithm.tickRepopulateTime(): List<Double> {
        return tickRepopulate().list.toList().map { it.playTimeMillis / 1000.0 }
    }


    private fun dummyDanmaku(timeSeconds: Double, text: String = "$timeSeconds") =
        Danmaku(
            text,
            "dummy",
            (timeSeconds * 1000L).toLong(),
            text,
            DanmakuLocation.NORMAL,
            text,
            0
        )

    private fun dummyDanmaku(timeSeconds: Long, text: String = "$timeSeconds") =
        Danmaku(text, "dummy", timeSeconds * 1000, text, DanmakuLocation.NORMAL, text, 0)


    @Test
    fun empty() = runTest {
        create(emptySequence())
    }

    ///////////////////////////////////////////////////////////////////////////
    // simple match
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `time before all matches 0`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        val list = instance.tickCollect()
        assertEquals(0, list.size)
        assertEquals(emptyList(), instance.tickCollect()) // duplicated tick
    }

    @Test
    fun `match one`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        val list = instance.at(1.2.seconds).tickRepopulateTime()
        assertEquals(listOf(1.0), list)
        assertEquals(emptyList(), instance.tickCollect()) // duplicated tick
    }

    @Test
    fun `match one equal`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        val list = instance.at(1.seconds).tickRepopulateTime()
        assertEquals(1, list.size)
        assertEquals(emptyList(), instance.tickCollect()) // duplicated tick
    }

    @Test
    fun `match two at the same time`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        val list = instance.at(1.seconds).tickRepopulateTime()
        assertEquals(listOf(1.0, 1.0), list)
        assertEquals(emptyList(), instance.tickCollect()) // duplicated tick
    }

    @Test
    fun `match two at different times`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        val list = instance.at(2.seconds).tickRepopulateTime()
        assertEquals(listOf(1.0, 2.0), list)

        assertEquals(emptyList(), instance.tickCollect()) // duplicated tick
    }


    ///////////////////////////////////////////////////////////////////////////
    // repopulateThreshold
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `triggers repopulateThreshold`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
        assertEquals(listOf(2.0, 3.0), instance.at(3.seconds).tickRepopulateTime())
    }

    @Test
    fun `does not trigger repopulateThreshold`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
        assertEquals(listOf(1.0, 2.0), instance.at(2.9.seconds).tickCollectTime())
    }

    ///////////////////////////////////////////////////////////////////////////
    // playing
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `progress forward`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
//        assertEquals(listOf(), instance.at(0.5.seconds).tickCollectTime())
        assertEquals(listOf(1.0), instance.at(1.seconds).tickCollectTime())
        assertEquals(listOf(), instance.at(1.1.seconds).tickCollectTime())
        assertEquals(listOf(2.0), instance.at(2.1.seconds).tickCollectTime())
        assertEquals(listOf(3.0, 4.0), instance.at(4.seconds).tickCollectTime())
        assertEquals(listOf(), instance.tickCollectTime())
    }

    @Test
    fun `progress forward with full buffer then re-tick`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.at(1.seconds).tickCollectTime(0))
        // time not changed
        assertEquals(listOf(1.0), instance.tickCollectTime(1))
        assertEquals(listOf(), instance.tickCollectTime())
    }

    @Test
    fun `progress forward with full buffer then forward`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.at(1.seconds).tickCollectTime(0))
        // time not changed
        assertEquals(listOf(1.0, 2.0), instance.at(2.seconds).tickCollectTime())
        assertEquals(listOf(), instance.tickCollectTime())
    }

    ///////////////////////////////////////////////////////////////////////////
    // seek forward
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `seek forward retrieve at most repopulateDistance seconds`() = runTest {
        // 快进时, 只看得到历史 repopulateDistance 秒的弹幕

        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(0.seconds).tickRepopulateTime())
        assertEquals(listOf(4.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.tickCollectTime())
    }

    @Test
    fun `seek forward overflow`() = runTest {
        // 快进时, 只看得到历史 3 秒的弹幕

        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(1.0), instance.at(1.seconds).tickRepopulateTime())
        assertEquals(listOf(5.0), instance.at(6.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.tickCollectTime())
    }

    ///////////////////////////////////////////////////////////////////////////
    // seek back
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `repopulate on start`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.at(4.seconds).tickCollectTime())
    }

    @Test
    fun `seek back - has intersection`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.at(4.seconds).tickCollectTime())
    }

    @Test
    fun `seek back - no intersection`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
                dummyDanmaku(8.0),
                dummyDanmaku(9.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(
            listOf(7.0, 8.0),
            instance.at(8.seconds).tickRepopulateTime()
        ) // 初次收集 repopulate
        assertEquals(listOf(3.0, 4.0), instance.at(4.5.seconds).tickRepopulateTime())
    }

    @Test
    fun `seek back - no exact match`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(0.0),
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(0.0, 1.0), instance.at(1.9.seconds).tickRepopulateTime())
    }

    @Test
    fun `seek back - no exact match within 3s`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(0.0),
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(), instance.at(2.1.seconds).tickCollectTime())
    }

    @Test
    fun `seek back then seek forward - no intersection`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(1.0, 2.0), instance.at(2.seconds).tickRepopulateTime())
        assertEquals(listOf(6.0, 7.0), instance.at(7.seconds).tickRepopulateTime())
    }

    @Test
    fun `seek back then seek forward - same time`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(1.0, 2.0), instance.at(2.seconds).tickRepopulateTime())
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
    }

    @Test
    fun `seek back then seek forward - has intersection`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(4.0, 5.0), instance.at(5.seconds).tickRepopulateTime())
        assertEquals(listOf(1.0, 2.0), instance.at(2.seconds).tickRepopulateTime())
        assertEquals(listOf(3.0), instance.at(3.seconds).tickCollectTime())
    }

    ///////////////////////////////////////////////////////////////////////////
    // invalid time ranges
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `seek to huge time collects empty`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at(999999.seconds).tickRepopulateTime())
    }

    @Test
    fun `seek to negative collects empty`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
                dummyDanmaku(3.0),
                dummyDanmaku(4.0),
                dummyDanmaku(5.0),
                dummyDanmaku(6.0),
                dummyDanmaku(7.0),
            ),
            repopulateThreshold = 3.seconds,
            repopulateDistance = 2.seconds,
        )
        assertEquals(listOf(), instance.at((-1).seconds).tickRepopulateTime())
    }

    @Test
    fun `random seek and collect`() = runTest {
        val random = Random(167213521)
        repeat(10000) {
            val time = random.nextDouble(0.0, 7.0)
            create(
                sequenceOf(
                    dummyDanmaku(1.0),
                    dummyDanmaku(2.0),
                    dummyDanmaku(3.0),
                    dummyDanmaku(4.0),
                    dummyDanmaku(5.0),
                    dummyDanmaku(6.0),
                    dummyDanmaku(7.0),
                ),
                repopulateThreshold = random.nextInt().seconds,
                repopulateDistance = random.nextInt().seconds,
            ).at(time.seconds).tickRepopulateTime()
        }
    }
}


/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DanmakuRepopulatorTest {

    @Test
    fun `repopulate with empty list - onSend should not be called`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 1_000_000_000L) // some arbitrary currentFrameTimeNanos
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(
            currentFrameTimeNanosState = longState,
        ) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // Act
        repopulator.repopulate(emptyList(), currentPlayMillis = 10_000)

        // Assert
        assertTrue(calls.isEmpty(), "onSend should not be invoked for an empty list")
    }

    @Test
    fun `repopulate with all-floating single element`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 100_000_000L) // currentFrameTimeNanos = 100ms
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        val singleDanmaku = DanmakuPresentation(
            danmaku = Danmaku(
                id = "1",
                providerId = "provider1",
                playTimeMillis = 5_000, // 5s
                senderId = "sender1",
                location = DanmakuLocation.NORMAL,
                text = "Hello Floating",
                color = 0xFFFFFF,
            ),
            isSelf = false,
        )

        // Act
        repopulator.repopulate(listOf(singleDanmaku), currentPlayMillis = 5_000)

        // Assert
        // Since currentPlayMillis == playTimeMillis, placeFrameTimeNanos should be exactly currentFrameTimeNanos
        assertEquals(1, calls.size)
        val (presentation, placeTime) = calls.first()
        assertEquals(singleDanmaku, presentation)
        // 5_000 - 5_000 = 0 => placeFrameTimeNanos = 100_000_000 - 0 = 100_000_000
        assertEquals(100_000_000L, placeTime)
    }

    @Test
    fun `repopulate with all-fixed single element`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 200_000_000L) // 200ms
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        val singleDanmaku = DanmakuPresentation(
            danmaku = Danmaku(
                id = "2",
                providerId = "provider2",
                playTimeMillis = 10_000, // 10s
                senderId = "sender2",
                location = DanmakuLocation.TOP, // fixed location
                text = "Hello Fixed",
                color = 0x000000,
            ),
            isSelf = true,
        )

        // Act
        repopulator.repopulate(listOf(singleDanmaku), currentPlayMillis = 10_000)

        // Assert
        assertEquals(1, calls.size)
        val (presentation, placeTime) = calls.first()
        assertEquals(singleDanmaku, presentation)
        // 10_000 - 10_000 = 0 => placeFrameTimeNanos = 200_000_000 - 0 = 200_000_000
        assertEquals(200_000_000L, placeTime)
    }

    @Test
    fun `repopulate with multiple floating danmakus - verify ascending order of playTimeMillis`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 1_500_000_000L) // 1.5s
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // Three floating danmakus, sorted by playTimeMillis: 8000, 9000, 10000
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("3", "provider3", 8_000, "sender3", DanmakuLocation.NORMAL, "d1", 0xFF0000),
                isSelf = false,
            ),
            DanmakuPresentation(
                Danmaku("4", "provider4", 9_000, "sender4", DanmakuLocation.NORMAL, "d2", 0x00FF00),
                isSelf = false,
            ),
            DanmakuPresentation(
                Danmaku("5", "provider5", 10_000, "sender5", DanmakuLocation.NORMAL, "d3", 0x0000FF),
                isSelf = false,
            ),
        )

        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 10_000)

        // Assert
        // Because the first two are negative when we shift them, only the last (ID = "5") is >= 0
        assertEquals(
            2, calls.size,
            "Only the Danmaku with playTimeMillis>=8500 should be placed, since earlier ones yield negative placeFrameTimeNanos",
        )
        assertEquals(listOf("4", "5"), calls.map { it.first.danmaku.id })
        assertEquals(listOf(500_000_000L, 1_500_000_000L), calls.map { it.second })
    }

    @Test
    fun `repopulate with multiple fixed danmakus - verify descending order of playTimeMillis`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 400_000_000L) // 400ms
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("6", "provider6", 12_000, "sender6", DanmakuLocation.TOP, "f1", 0xFFFF00),
                isSelf = false,
            ),
            DanmakuPresentation(
                Danmaku("7", "provider7", 13_000, "sender7", DanmakuLocation.BOTTOM, "f2", 0xFF00FF),
                isSelf = false,
            ),
            DanmakuPresentation(
                Danmaku("8", "provider8", 14_000, "sender8", DanmakuLocation.TOP, "f3", 0x00FFFF),
                isSelf = false,
            ),
        )

        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 14_000)

        // Assert
        // All are "fixed" (TOP or BOTTOM). 
        // The lastDanmakuTimeMillis = 14_000
        // lastDanmakuPlaceFrameTimeNanos = 400_000_000 - ((14_000 - 14_000) * 1_000_000) = 400_000_000
        // Then we place them in reverse order of the list's sorted order (which is ascending).
        // But note, the code does .asReversed() on the filtered fixed danmakus after sorting. 
        // Let's first confirm the input list is sorted by playTimeMillis: 12_000, 13_000, 14_000
        // => reversed = (14_000, 13_000, 12_000)
        // The place times:
        //   For 8 (14_000): placeFrameTime = 400_000_000 - (14_000 - 14_000)*1_000_000 = 400_000_000
        //   For 7 (13_000): placeFrameTime = 400_000_000 - (14_000 - 13_000)*1_000_000
        //                                  = 400_000_000 - 1_000_000_000
        //                                  = -600_000_000 (skip)
        //   For 6 (12_000): would be even more negative => skip
        //
        // So we expect exactly one call for the last one (id="8"), placeFrameTime=400_000_000
        assertEquals(1, calls.size)
        assertEquals("8", calls[0].first.danmaku.id)
        assertEquals(400_000_000L, calls[0].second)
    }

    @Test
    fun `repopulate with mixed floating and fixed danmakus`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 500_000_000L) // 500ms
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // Mixed: 3 floating (NORMAL) and 2 fixed (TOP/BOTTOM), sorted ascending by playTimeMillis
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("9", "p9", 2_000, "s9", DanmakuLocation.NORMAL, "floating1", 0x111111),
                false,
            ),
            DanmakuPresentation(
                Danmaku("10", "p10", 4_000, "s10", DanmakuLocation.TOP, "fixed1", 0x222222),
                false,
            ),
            DanmakuPresentation(
                Danmaku("11", "p11", 6_000, "s11", DanmakuLocation.NORMAL, "floating2", 0x333333),
                false,
            ),
            DanmakuPresentation(
                Danmaku("12", "p12", 7_000, "s12", DanmakuLocation.BOTTOM, "fixed2", 0x444444),
                false,
            ),
            DanmakuPresentation(
                Danmaku("13", "p13", 8_000, "s13", DanmakuLocation.NORMAL, "floating3", 0x555555),
                false,
            ),
        )

        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 8_000)

        // Assert
        // The first two floating (2k, 6k) compute negative times and are skipped;
        // The two fixed (4k, 7k) also yield negative times => skipped;
        // Only the floating with playTimeMillis=8000 is >= 0 => placed
        assertEquals(
            1, calls.size,
            "Only the floating Danmaku with playTimeMillis=8000 should appear",
        )
        assertEquals(listOf("13"), calls.map { it.first.danmaku.id })
        assertEquals(listOf(500_000_000L), calls.map { it.second })
    }

    @Test
    fun `repopulate - verifying negative placeFrameTimeNanos are skipped`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 1_000_000_000L) // 1 second in nanos
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // We'll create a single floating that would produce a negative time, 
        // and a single fixed that would produce a negative time, 
        // and one that is borderline >= 0
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("neg1", "provN1", 5000, "sN1", DanmakuLocation.NORMAL, "Will be negative", 0xCCCCCC),
                false,
            ),
            DanmakuPresentation(
                Danmaku("neg2", "provN2", 6000, "sN2", DanmakuLocation.TOP, "Will also be negative", 0xDDDDDD),
                false,
            ),
            DanmakuPresentation(
                Danmaku("pos1", "provP1", 10_000, "sP1", DanmakuLocation.NORMAL, "Should appear", 0xEEEEEE),
                false,
            ),
        )

        // currentPlayMillis = 10_000, meaning the last-floating's timestamp is matched to the right edge
        // The repopulator will compute negative place times for 5,000 and 6,000 depending on the logic.
        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 10_000)

        // Assert
        // We only expect "pos1" to appear
        assertEquals(1, calls.size)
        assertEquals("pos1", calls[0].first.danmaku.id)
    }

    @Test
    fun `repopulate - verifying isSelf does not impact time calculations - only a flag`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 2_000_000_000L) // 2s
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // Two identical times, one isSelf=true, the other isSelf=false
        val d1 = DanmakuPresentation(
            Danmaku("self1", "pSelf", 15_000, "senderSelf", DanmakuLocation.NORMAL, "I am self", 0xAAAAAA),
            isSelf = true,
        )
        val d2 = DanmakuPresentation(
            Danmaku("self2", "pSelf", 15_000, "senderSelf", DanmakuLocation.NORMAL, "I am not self", 0xBBBBBB),
            isSelf = false,
        )

        // Act
        repopulator.repopulate(listOf(d1, d2), currentPlayMillis = 15_000)

        // Assert
        // Both have same playTimeMillis => same placeFrameTimeNanos => 2_000_000_000
        // The floating list has firstDanmakuTime=15_000, so 
        //   firstDanmakuPlaceFrame= 2_000_000_000 - ((15_000 - 15_000) * 1_000_000) = 2_000_000_000
        // d1 => placeFrame=2_000_000_000
        // d2 => placeFrame=2_000_000_000 + (15_000 - 15_000)*1_000_000=2_000_000_000
        // => both calls, each with the same placeFrameTimeNanos
        assertEquals(2, calls.size)
        assertTrue(calls.any { it.first.id == "self1" && it.second == 2_000_000_000L })
        assertTrue(calls.any { it.first.id == "self2" && it.second == 2_000_000_000L })
    }

    @Test
    fun `repopulate - currentFrameTimeNanos is zero`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 0L)
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // Single floating, single fixed
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("f1", "provF1", 5_000, "senderF1", DanmakuLocation.NORMAL, "FloatingZero", 0x123456),
                false,
            ),
            DanmakuPresentation(
                Danmaku("fix1", "provFix1", 5_000, "senderFix1", DanmakuLocation.BOTTOM, "FixedZero", 0xABCDEF),
                false,
            ),
        )

        // currentPlayMillis matches 5_000
        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 5_000)

        // Assert
        // For the floating one (5_000):
        //   firstDanmakuPlaceFrameTimeNanos = 0 - (5_000 - 5_000)*1_000_000 = 0
        //   => placeFrameTimeNanos = 0 (>= 0 => included)
        // For the fixed one (5_000):
        //   lastDanmakuTimeMillis = 5_000
        //   lastDanmakuPlaceFrameTimeNanos = 0 - (5_000 - 5_000)*1_000_000 = 0
        //   => placeFrameTimeNanos = 0 (>= 0 => included)
        //
        // We expect 2 calls, each with placeFrameTimeNanos=0
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.second == 0L }, "Both place times should be zero")

        val (fDanmakuIds, fTimes) = calls.unzip()
        assertEquals(setOf("f1", "fix1"), fDanmakuIds.map { it.danmaku.id }.toSet())
        assertEquals(listOf(0L, 0L), fTimes)
    }

    @Test
    fun `repopulate - currentFrameTimeNanos is extremely large`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = Long.MAX_VALUE - 1_000_000_000L)
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // We choose playTimeMillis near the middle so that the final place times won't overflow but can be huge
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("large1", "provL1", 100_000, "senderL1", DanmakuLocation.NORMAL, "LargeTimeFloating", 0xFFFFFF),
                false,
            ),
            DanmakuPresentation(
                Danmaku("large2", "provL2", 100_000, "senderL2", DanmakuLocation.TOP, "LargeTimeFixed", 0xFFFF00),
                false,
            ),
        )

        // Act
        repopulator.repopulate(danmakuList, currentPlayMillis = 100_000)

        // Assert
        // Both have playTimeMillis = 100,000, same as currentPlayMillis => no offset => placeFrameTimeNanos = longState.value
        assertEquals(2, calls.size)
        val placeTimes = calls.map { it.second }.toSet()
        assertTrue(placeTimes.size == 1, "Both place times should be identical")
        // Check that the place time is exactly the original large `longState.value`
        assertEquals(longState.value, placeTimes.first())
    }

    @Test
    fun `repopulate - currentFrameTimeNanos is small and skipping negative`() = runTest {
        // Arrange
        val longState = mutableLongStateOf(value = 1_000_000L) // 1 millisecond in nanos
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        // We'll place 3 floating, 2 fixed at times that may skip
        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku("s1", "pS1", 500, "sndS1", DanmakuLocation.NORMAL, "FloatEarly", 0xFF0000),
                false,
            ),
            DanmakuPresentation(
                Danmaku("s2", "pS2", 700, "sndS2", DanmakuLocation.TOP, "FixedEarly", 0x00FF00),
                false,
            ),
            DanmakuPresentation(
                Danmaku("s3", "pS3", 1_000, "sndS3", DanmakuLocation.NORMAL, "FloatMid", 0x0000FF),
                false,
            ),
            DanmakuPresentation(
                Danmaku("s4", "pS4", 1_200, "sndS4", DanmakuLocation.BOTTOM, "FixedLater", 0xAAAAAA),
                false,
            ),
            DanmakuPresentation(
                Danmaku("s5", "pS5", 1_300, "sndS5", DanmakuLocation.NORMAL, "FloatLatest", 0xBBBBBB),
                false,
            ),
        )

        // We'll set currentPlayMillis = 1_300 so the last-floating's timestamp is right edge
        repopulator.repopulate(danmakuList, currentPlayMillis = 1_300)

        // Let's see the expected results:
        //  Floating (500, 1_000, 1_300) => sorted ascending => first(500) => offset = 1_300 - 500 = 800 => placeFrameTime for first = 1_000_000 - (800*1_000_000) = 1_000_000 - 800_000_000 = -799_000_000 => skip
        //    second(1_000): = -799_000_000 + (1_000 - 500)*1_000_000 = -799_000_000 + 500_000_000 = -299_000_000 => skip
        //    third(1_300): = -299_000_000 + (1_300 - 1_000)*1_000_000 = -299_000_000 + 300_000_000 = 1_000_000 => send
        //
        //  Fixed (700, 1_200) => sorted ascending => reversed => (1_200, 700)
        //    lastDanmakuTimeMillis = 1_200
        //    lastDanmakuPlaceFrameTimeNanos = 1_000_000 - ((1_300 - 1_200)*1_000_000) = 1_000_000 - (100*1_000_000) = 1_000_000 - 100_000_000 = -99_000_000
        //    For 1_200: placeFrameTime = -99_000_000 - (1_200 - 1_200)*1_000_000 = -99_000_000 => skip
        //    For 700:  placeFrameTime = -99_000_000 - (1_200 - 700)*1_000_000 = -99_000_000 - (500*1_000_000) = -599_000_000 => skip
        //
        // => Only s5 gets placed with placeFrameTimeNanos=1_000_000
        // Act & Assert
        assertEquals(1, calls.size, "Only the floating danmaku with playTime=1300 is non-negative")
        val (presentations, times) = calls.unzip()
        assertEquals("s5", presentations.single().danmaku.id)
        assertEquals(1_000_000L, times.single())
    }

    @Test
    fun `repopulate - currentFrameTimeNanos is negative - unusual case`() = runTest {
        // Though unlikely in normal usage, let's see behavior if currentFrameTimeNanos is negative
        val longState = mutableLongStateOf(value = -1_000_000_000L) // -1s in nanos
        val calls = mutableListOf<Pair<DanmakuPresentation, Long>>()
        val repopulator = DanmakuRepopulator(longState) { danmakuPresentation, placeFrameTimeNanos ->
            calls += danmakuPresentation to placeFrameTimeNanos
        }

        val danmakuList = listOf(
            DanmakuPresentation(
                Danmaku(
                    "negCaseFloat",
                    "pNCF",
                    2_000,
                    "sndNCF",
                    DanmakuLocation.NORMAL,
                    "Should still skip or place",
                    0xABCDEF,
                ),
                false,
            ),
            DanmakuPresentation(
                Danmaku(
                    "negCaseFix",
                    "pNCX",
                    2_000,
                    "sndNCX",
                    DanmakuLocation.TOP,
                    "Fixed might also skip or place",
                    0x123456,
                ),
                false,
            ),
        )

        // Using currentPlayMillis = 2_000 => the offset is 0 for playTime=2_000
        // => Floating: first(2_000) => firstDanmakuTimeMillis=2_000
        //    firstDanmakuPlaceFrameTimeNanos= -1_000_000_000 - (2_000 - 2_000)*1_000_000 = -1_000_000_000
        // => placeFrameTimeNanos = -1,000,000,000 ( < 0 => skip )
        //
        // => Fixed: last(2_000)
        //    lastDanmakuTimeMillis=2_000
        //    lastDanmakuPlaceFrameTimeNanos= -1_000_000_000 - ((2_000 - 2_000)*1_000_000) = -1_000_000_000 ( skip )
        //
        // => No calls expected
        repopulator.repopulate(danmakuList, currentPlayMillis = 2_000)

        // Expect no results
        assertTrue(calls.isEmpty())
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType.ED
import me.him188.ani.datasources.api.EpisodeType.MAD
import me.him188.ani.datasources.api.EpisodeType.OAD
import me.him188.ani.datasources.api.EpisodeType.OP
import me.him188.ani.datasources.api.EpisodeType.OVA
import me.him188.ani.datasources.api.EpisodeType.PV
import me.him188.ani.datasources.api.EpisodeType.SP
import me.him188.ani.test.DynamicTestsResult
import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.dynamicTest
import me.him188.ani.test.permutedSequence
import me.him188.ani.test.runDynamicTests
import me.him188.ani.utils.serialization.BigNum
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import me.him188.ani.datasources.api.EpisodeSort as ep
import me.him188.ani.datasources.api.EpisodeSort.Normal as normal
import me.him188.ani.datasources.api.EpisodeSort.Special as special

@TestContainer
class EpisodeSortTest {
    @TestFactory
    fun `parse string`(): DynamicTestsResult {
        return runDynamicTests(
            listOf(
                // Actual to Expected
                "1" to normal(1f),
                "0" to normal(0f),
                "01" to normal(1f),
                "1.0" to normal(1f),
                "1.5" to normal(1.5f),
                "2.5" to normal(2.5f),
                "0.5" to normal(0.5f),
                "05.5" to normal(5.5f),
                "30.5" to normal(30.5f),
                "-1" to EpisodeSort.Unknown("-1"),
                "1.1" to EpisodeSort.Unknown("1.1"),
                "SP1" to special(SP, 1f),
                "SP02" to special(SP, 2f),
                "SP1.5" to special(SP, 1.5f),
                "MAD1" to special(MAD, 1f),
                "MAD02" to special(MAD, 2f),
                "OVA04" to special(OVA, 4f),
                "OAD08" to special(OAD, 8f),
                "OAD" to special(OAD, null),
                "OVA" to special(OVA, null),
                "SP" to special(SP, null),
                "SP0" to special(SP, 0f),
                "S" to EpisodeSort.Unknown("S"),
                "SP-1" to EpisodeSort.Unknown("SP-1"),
                "SP1.1" to EpisodeSort.Unknown("SP1.1"),
                "SPAB" to EpisodeSort.Unknown("SP"),
                "SPSP" to EpisodeSort.Unknown("SP"),
                "SPAB" to EpisodeSort.Unknown("SP"),
            ).map {
                dynamicTest(it.first) {
                    val (raw, expected) = it
                    val actual = ep(raw)
                    assertEquals(expected, actual)
                    assertEquals(expected.hashCode(), actual.hashCode())
                }
            },
        )
    }


    @TestFactory
    fun `parse BigNum and EpisodeType`(): DynamicTestsResult {
        return runDynamicTests(
            listOf(
                // Actual to Expected
                Pair<BigNum, EpisodeType?>(BigNum("-1"), SP) to EpisodeSort.Unknown("-1"),
                Pair<BigNum, EpisodeType?>(BigNum("1"), SP) to special(SP, 1f),
                Pair<BigNum, EpisodeType?>(BigNum("2"), OP) to special(OP, 2f),
                Pair<BigNum, EpisodeType?>(BigNum("3"), ED) to special(ED, 3f),
                Pair<BigNum, EpisodeType?>(BigNum("4"), PV) to special(PV, 4f),
                Pair<BigNum, EpisodeType?>(BigNum("5"), MAD) to special(MAD, 5f),
                Pair<BigNum, EpisodeType?>(BigNum("6"), OVA) to special(OVA, 6f),
                Pair<BigNum, EpisodeType?>(BigNum("7"), OAD) to special(OAD, 7f),
                Pair<BigNum, EpisodeType?>(BigNum("7.5"), SP) to special(SP, 7.5f),
                Pair<BigNum, EpisodeType?>(BigNum("3"), SP) to special(SP, 3f),
                Pair<BigNum, EpisodeType?>(BigNum("1.1"), SP) to EpisodeSort.Unknown("1.1"),
                Pair<BigNum, EpisodeType?>(BigNum("8"), null) to EpisodeSort.Unknown("8"),
            ).map {
                dynamicTest(it.first.toString()) {
                    val (raw, expected) = it
                    val actual = ep(raw.first, raw.second)
                    assertEquals(expected, actual)
                    assertEquals(expected.hashCode(), actual.hashCode())
                }
            },
        )
    }

    @TestFactory
    fun `parse BigNum`(): DynamicTestsResult {
        return runDynamicTests(
            listOf(
                // Actual to Expected
                "-1" to EpisodeSort.Unknown("-1"),
                "1" to normal(1f),
                "1.1" to EpisodeSort.Unknown("1.1"),
                "2.5" to normal(2.5f),
                "3" to normal(3f),
            ).map {
                dynamicTest(it.first) {
                    val (raw, expected) = it
                    val actual = ep(BigNum(raw))
                    assertEquals(expected, actual)
                    assertEquals(expected.hashCode(), actual.hashCode())
                }
            },
        )
    }

    @TestFactory
    fun `to string`(): DynamicTestsResult {
        return runDynamicTests(
            listOf(
                // Actual to Expected
                normal(1f) to "01",
                normal(0f) to "00",
                normal(1f) to "01",
                normal(1.5f) to "1.5",
                normal(2.5f) to "2.5",
                normal(0.5f) to "0.5",
                normal(5.5f) to "5.5",
                normal(30.5f) to "30.5",
                special(SP, -1f) to "SP-1",
                special(MAD, 1.5f) to "MAD1.5",
                EpisodeSort.Unknown("S") to "S",
            ).map {
                dynamicTest(it.second) {
                    val (raw, expected) = it
                    val actual = raw.toString()
                    assertEquals(expected, actual)
                }
            },
        )
    }

    @TestFactory
    fun equals() = runDynamicTests(
        listOf(
            normal(1f) to normal(1f),
            normal(1.5f) to normal(1.5f),
            normal(0f) to normal(0f),
            special(SP, 1f) to special(SP, 1f),
            special(MAD, 2f) to special(MAD, 2f),
            EpisodeSort.Unknown("S") to EpisodeSort.Unknown("S"),
        ).map {
            dynamicTest(it.toString()) {
                val (a, b) = it
                assertEquals(a, b)
                assertEquals(b, a)
                assertEquals(b.hashCode(), a.hashCode())
            }
        },
    )

    @TestFactory
    fun `not equals`() = runDynamicTests(
        listOf(
            normal(1f) to normal(2f),
            normal(1f) to special(SP, 1f),
            special(PV, 1.5f) to special(PV, 1f),
            special(MAD, 1.0f) to special(PV, 1f),
            special(MAD, 1.0f) to EpisodeSort.Unknown("1"),
            EpisodeSort.Unknown("1 ") to EpisodeSort.Unknown("1"),
        ).map {
            dynamicTest(it.toString()) {
                val (a, b) = it
                assertNotEquals(a, b)
                assertNotEquals(b, a)
                assertNotEquals(b.hashCode(), a.hashCode()) // our extra constraint
            }
        },
    )

    @TestFactory
    fun compare() = runDynamicTests(
        listOf(
            // Original to Sorted Ascending
            "int" to listOf(ep(1), ep(2), ep(3)),
            "float" to listOf(ep(1), ep("1.5"), ep("2"), ep(3)),
            "sp" to listOf(ep("SP1"), ep("SP2")),
            "int with sp" to listOf(ep(1), ep(3), ep("SP1")),
            "float with sp" to listOf(ep("1.5"), ep(3), ep("SP1")),
            "Normal lt Special" to listOf(ep(3), ep("SP2"), ep("SP3")),
            "Special with same type" to listOf(ep("SP1"), ep("SP1.5"), ep("SP3")),
            "Special with different type" to listOf(ep("OP5"), ep("ED2"), ep("PV3")),
            "Special lt Unknown" to listOf(ep("SP2"), ep("SP3"), ep("1.2")),
        ).flatMap { (name, sorted) ->
            sorted.permutedSequence().map { unsorted ->
                dynamicTest("$name: $unsorted => $sorted") {
                    assertEquals(sorted, unsorted.sorted())
                }
            }
        },
    )

    @TestFactory
    fun toFloats() = runDynamicTests(
        listOf(
            2f to "02".toFloat(),
            2f to "2".toFloat(),
        ).map {
            dynamicTest(it.toString()) {
                val (a, b) = it
                assertEquals(a, b)
            }
        },
    )

    @TestFactory
    fun testCompareTo() = runDynamicTests(
        listOf(
            // Actual to Expected

            // Normal and Normal
            Pair(ep("1"), ep("2")) to -1, // lt
            Pair(ep("1"), ep("1")) to 0, // eq
            Pair(ep("2"), ep("1")) to 1, // gt
            // Normal and Special
            Pair(ep("2"), ep("SP1")) to -1, // lt
            // Normal and Unknown
            Pair(ep("3"), ep("1.1")) to -1, // lt

            // Special and Normal
            Pair(ep("SP1"), ep("2")) to 1, // gt
            // Special and Special
            Pair(ep("SP1"), ep("SP2")) to -1, // lt
            Pair(ep("SP1"), ep(BigNum("1"), SP)) to 0, // eq
            Pair(ep("SP2"), ep("SP1")) to 1, // gt
            Pair(ep(BigNum(1), SP), ep(BigNum(1), OP)) to -1, // lt
            Pair(ep(BigNum(1), OP), ep(BigNum(1), ED)) to -1, // lt
            Pair(ep(BigNum(1), ED), ep(BigNum(1), PV)) to -1, // lt
            Pair(ep(BigNum(1), OAD), ep(BigNum(1), OVA)) to 1, // gt
            Pair(ep(BigNum(1), OVA), ep(BigNum(1), MAD)) to 1, // gt
            Pair(ep(BigNum(1), MAD), ep(BigNum(1), PV)) to 1, // gt
            Pair(ep(BigNum(1), MAD), ep(BigNum(2), MAD)) to -1, // lt
            Pair(ep(BigNum(1), MAD), ep(BigNum(1), MAD)) to 0, // eq
            Pair(ep(BigNum(2), MAD), ep(BigNum(1), MAD)) to 1, // gt
            // Special and Unknown
            Pair(ep("SP3"), ep("1.1")) to -1, // lt

            // Unknown and Normal
            Pair(ep("1.1"), ep("3")) to 1, // gt
            // Unknown and Special
            Pair(ep("1.1"), ep("SP3")) to 1, // gt
            // Unknown and Unknown
            Pair(ep("1.1"), ep("SP3.2")) to "1.1".compareTo("SP3.2"), // raw compare to

        ).map {
            dynamicTest(it.toString()) {
                val (a, b) = it
                if (b < -1) { // lt
                    assertTrue(a.first < a.second)
                } else if (b > 1) { // gt
                    assertTrue(a.first > a.second)
                } else { // -1 0 1
                    assertEquals(b, a.first.compareTo(a.second))
                }
            }
        },
    )


}

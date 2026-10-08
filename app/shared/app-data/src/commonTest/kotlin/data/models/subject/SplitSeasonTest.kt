/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.data.models.subject.SplitSeasonTestData.AncientMagusBride
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.Apothecary
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.AttackOnTitan
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.EightySix
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.Frieren
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.JujutsuKaisen
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.MushokuTensei
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.ReZero
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.SpyFamily
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestContainer
class SplitSeasonTest {
    private fun partIds(mainline: List<SplitSeason.Candidate>, selfId: Int): List<Int>? =
        SplitSeason.compute(mainline, selfId)?.parts?.map { it.subjectId }

    @Test
    fun `ReZero groups each season but not the whole series`() {
        assertNull(partIds(ReZero, 140001))
        assertEquals(listOf(278826, 316247), partIds(ReZero, 278826))
        assertEquals(listOf(278826, 316247), partIds(ReZero, 316247))
        assertEquals(listOf(425998, 510728), partIds(ReZero, 425998))
        assertEquals(listOf(547888, 633836), partIds(ReZero, 633836))
    }

    @Test
    fun `ReZero S2 part 2 has season number counted from 26`() {
        val season = SplitSeason.compute(ReZero, 316247)!!
        assertEquals(1, season.selfIndex)
        assertEquals(26, season.firstSort)
        assertEquals(14, season.seasonNumberOf(39))
        assertEquals(25, season.seasonNumberOf(50))
        assertEquals(listOf("后半部分", "後半クール", "后半"), season.self.markers)
        assertEquals(emptyList(), season.parts[0].markers)
        assertContains(season.baseNames, "Re：从零开始的异世界生活 第二季")
        assertContains(season.baseNames, "Re0 第二季")
    }

    @Test
    fun `ReZero S4 parts are named by arc`() {
        val season = SplitSeason.compute(ReZero, 633836)!!
        assertEquals(12, season.seasonNumberOf(78))
        assertEquals(listOf("丧失篇", "喪失編", "喪失篇"), season.parts[0].markers)
        assertEquals(listOf("夺还篇", "奪還編", "奪還篇"), season.parts[1].markers)
        assertContains(season.baseNames, "Re：从零开始的异世界生活 第四季")
        assertContains(season.baseNames, "Re:ゼロから始める異世界生活 4th season")
    }

    @Test
    fun `MushokuTensei groups S1 and S2 separately and skips S3 part 2 without episodes`() {
        assertEquals(listOf(277554, 325585), partIds(MushokuTensei, 325585))
        assertEquals(listOf(373247, 444557), partIds(MushokuTensei, 444557))
        assertNull(partIds(MushokuTensei, 501963))
        assertNull(partIds(MushokuTensei, 708197))
    }

    @Test
    fun `MushokuTensei S2 prologue at sort 0 does not take a season number`() {
        val season = SplitSeason.compute(MushokuTensei, 444557)!!
        assertEquals(0, season.firstSort)
        assertEquals(13, season.seasonNumberOf(13))
        assertEquals(12, season.seasonNumberOf(12))
    }

    @Test
    fun `MushokuTensei S1 part 2 season number equals sort`() {
        val season = SplitSeason.compute(MushokuTensei, 325585)!!
        assertEquals(12, season.seasonNumberOf(12))
        assertEquals(listOf("第2部分", "第2クール", "Part 2", "後半", "后半"), season.self.markers)
    }

    @Test
    fun `AttackOnTitan groups S3 and the final season but not the one-episode finales`() {
        assertEquals(listOf(217300, 263750), partIds(AttackOnTitan, 263750))
        assertEquals(listOf(285666, 331752), partIds(AttackOnTitan, 331752))
        assertEquals(17, SplitSeason.compute(AttackOnTitan, 331752)!!.seasonNumberOf(76))
        assertNull(partIds(AttackOnTitan, 376739))
        assertNull(partIds(AttackOnTitan, 415779))
    }

    @Test
    fun `SpyFamily groups only the first season`() {
        assertEquals(listOf(329906, 373267), partIds(SpyFamily, 329906))
        assertEquals(listOf(329906, 373267), partIds(SpyFamily, 373267))
        assertNull(partIds(SpyFamily, 411427))
        assertNull(partIds(SpyFamily, 498378))
    }

    @Test
    fun `AncientMagusBride S2 restarts sort at 1`() {
        assertNull(partIds(AncientMagusBride, 210864))
        val season = SplitSeason.compute(AncientMagusBride, 442523)!!
        assertEquals(listOf(399820, 442523), season.parts.map { it.subjectId })
        assertEquals(1, season.firstSort)
        assertEquals(13, season.seasonNumberOf(13))
    }

    @Test
    fun `EightySix is one split season`() {
        val season = SplitSeason.compute(EightySix, 331887)!!
        assertEquals(1, season.selfIndex)
        assertEquals(12, season.seasonNumberOf(12))
        assertEquals(listOf("第2部分", "第2クール", "Part 2"), season.self.markers)
    }

    @Test
    fun `continuous sort alone does not make a split season`() {
        assertNull(partIds(Frieren, 400602))
        assertNull(partIds(Frieren, 515759))
        assertNull(partIds(JujutsuKaisen, 369304))
        assertNull(partIds(JujutsuKaisen, 472741))
        assertNull(partIds(Apothecary, 486347))
    }

    @Test
    fun `Apothecary S3 is split`() {
        assertEquals(listOf(568244, 599893), partIds(Apothecary, 599893))
        assertNull(partIds(Apothecary, 420628))
    }

    @Test
    fun `subject missing from mainline is not a split season`() {
        assertNull(partIds(ReZero, 999999))
        assertNull(partIds(emptyList(), 316247))
    }

    @Test
    fun `stripMarker removes part markers but keeps season markers`() {
        fun strip(name: String) = SplitSeason.stripMarker(name).let { it.base to it.marker }

        assertEquals("无职转生～到了异世界就拿出真本事～" to "第2部分", strip("无职转生～到了异世界就拿出真本事～ 第2部分"))
        assertEquals("無職転生Ⅱ ～異世界行ったら本気だす～" to "第2クール", strip("無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール"))
        assertEquals("Re:ゼロから始める異世界生活 2nd season" to "後半クール", strip("Re:ゼロから始める異世界生活 2nd season 後半クール"))
        assertEquals("Re0 第二季" to "后半", strip("Re0 第二季后半"))
        assertEquals("進撃の巨人 The Final Season" to "Part.2", strip("進撃の巨人 The Final Season Part.2"))
        assertEquals("SPY×FAMILY" to "Part 2", strip("SPY×FAMILY Part 2"))
        assertEquals("Re：从零开始的异世界生活 第三季" to "袭击篇", strip("Re：从零开始的异世界生活 第三季 袭击篇"))
        assertEquals("呪術廻戦 死滅回游" to "前編", strip("呪術廻戦 死滅回游 前編"))
        assertEquals("进击的巨人 最终季 完结篇" to "前篇", strip("进击的巨人 最终季 完结篇 前篇"))

        assertEquals("葬送のフリーレン 第2期" to null, strip("葬送のフリーレン 第2期"))
        assertEquals("间谍过家家 第二季" to null, strip("间谍过家家 第二季"))
        assertEquals("SPY×FAMILY Season 2" to null, strip("SPY×FAMILY Season 2"))
        assertEquals("Re:Zero kara Hajimeru Isekai Seikatsu (2021)" to null, strip("Re:Zero kara Hajimeru Isekai Seikatsu (2021)"))
        assertEquals("魔法使いの嫁 SEASON2" to null, strip("魔法使いの嫁 SEASON2"))
        assertEquals("前篇" to null, strip("前篇"))
    }
}

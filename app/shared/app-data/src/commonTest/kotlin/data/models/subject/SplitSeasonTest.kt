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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@TestContainer
class SplitSeasonTest {
    @Test
    fun `fixtures cover split and non-split subjects`() {
        assertNull(ReZero.splitSeason(140001))
        assertEquals(listOf(278826, 316247), ReZero.splitSeason(316247)!!.parts.map { it.subjectId })
        assertEquals(listOf(547888, 633836), ReZero.splitSeason(633836)!!.parts.map { it.subjectId })
        assertNull(MushokuTensei.splitSeason(501963))
        assertNull(AttackOnTitan.splitSeason(376739))
        assertNull(SpyFamily.splitSeason(411427))
        assertNull(AncientMagusBride.splitSeason(210864))
        assertNull(Frieren.splitSeason(515759))
        assertNull(JujutsuKaisen.splitSeason(472741))
        assertNull(Apothecary.splitSeason(486347))
        assertEquals(listOf(568244, 599893), Apothecary.splitSeason(599893)!!.parts.map { it.subjectId })
    }

    @Test
    fun `selfIndex follows the requesting subject`() {
        assertEquals(0, ReZero.splitSeason(278826)!!.selfIndex)
        assertEquals(1, ReZero.splitSeason(316247)!!.selfIndex)
        assertEquals(316247, ReZero.splitSeason(316247)!!.self.subjectId)
    }

    @Test
    fun `ReZero S2 part 2 has season number counted from 26`() {
        val season = ReZero.splitSeason(316247)!!
        assertEquals(26, season.firstSort)
        assertEquals(14, season.seasonNumberOf(39))
        assertEquals(25, season.seasonNumberOf(50))
        assertEquals(13, season.previousPartsEpisodeCount)
        assertEquals(listOf("后半部分", "後半クール", "后半"), season.self.markers)
        assertContains(season.baseNames, "Re：从零开始的异世界生活 第二季")
        assertContains(season.baseNames, "Re0 第二季")
    }

    @Test
    fun `ReZero S4 parts are named by arc`() {
        val season = ReZero.splitSeason(633836)!!
        assertEquals(12, season.seasonNumberOf(78))
        assertEquals(listOf("丧失篇", "喪失編", "喪失篇"), season.parts[0].markers)
        assertEquals(listOf("夺还篇", "奪還編", "奪還篇"), season.parts[1].markers)
    }

    @Test
    fun `MushokuTensei S2 prologue at sort 0 does not take a season number`() {
        val season = MushokuTensei.splitSeason(444557)!!
        assertEquals(0, season.firstSort)
        assertEquals(13, season.seasonNumberOf(13))
        assertEquals(12, season.seasonNumberOf(12))
        assertEquals(13, season.previousPartsEpisodeCount)
        assertEquals(1, season.previousPartsExtraEntryCount)
    }

    @Test
    fun `MushokuTensei S1 part 2 season number equals sort`() {
        val season = MushokuTensei.splitSeason(325585)!!
        assertEquals(12, season.seasonNumberOf(12))
        assertEquals(0, season.previousPartsExtraEntryCount)
    }

    @Test
    fun `AncientMagusBride S2 restarts sort at 1`() {
        val season = AncientMagusBride.splitSeason(442523)!!
        assertEquals(1, season.firstSort)
        assertEquals(13, season.seasonNumberOf(13))
    }

    @Test
    fun `EightySix special after part 1 is an extra entry on merged pages`() {
        val season = EightySix.splitSeason(331887)!!
        assertEquals(1, season.parts[0].inlineSpecialCount)
        assertEquals(3, season.parts[1].inlineSpecialCount)
        assertEquals(1, season.previousPartsExtraEntryCount)
        assertEquals(0, SpyFamily.splitSeason(373267)!!.previousPartsExtraEntryCount)
    }

    @Test
    fun `a split season needs at least two parts`() {
        val part = ReZero.splitSeason(316247)!!.parts.first()
        assertFailsWith<IllegalArgumentException> { SplitSeason(listOf(part), selfIndex = 0, baseNames = emptyList()) }
        assertFailsWith<IllegalArgumentException> { SplitSeason(listOf(part, part), selfIndex = 2, baseNames = emptyList()) }
    }

    @Test
    fun `seasonNumbersOf reads season markers in page names`() {
        assertEquals(setOf(2), SplitSeason.seasonNumbersOf("无职转生 第二季 ～到了异世界就拿出真本事～"))
        assertEquals(setOf(2), SplitSeason.seasonNumbersOf("無職転生Ⅱ ～異世界行ったら本気だす～"))
        assertEquals(setOf(2), SplitSeason.seasonNumbersOf("Re:ゼロから始める異世界生活 2nd season 後半クール"))
        assertEquals(setOf(3), SplitSeason.seasonNumbersOf("rezero s3"))
        assertEquals(setOf(2), SplitSeason.seasonNumbersOf("魔法使いの嫁 SEASON2"))
        assertEquals(setOf(2), SplitSeason.seasonNumbersOf("葬送のフリーレン 第２期"))
        assertEquals(setOf(4), SplitSeason.seasonNumbersOf("Re:ZERO -Starting Life in Another World- Season 4"))
        assertEquals(emptySet(), SplitSeason.seasonNumbersOf("无职转生～到了异世界就拿出真本事～ 第2部分"))
        assertEquals(emptySet(), SplitSeason.seasonNumbersOf("86―エイティシックス― 第2クール"))
        assertEquals(emptySet(), SplitSeason.seasonNumbersOf("SPY×FAMILY"))
    }

    @Test
    fun `genericMarkers name the first two parts in several languages`() {
        assertContains(SplitSeason.genericMarkers(0), "前半")
        assertContains(SplitSeason.genericMarkers(1), "Part 2")
        assertContains(SplitSeason.genericMarkers(1), "后半")
        assertEquals(listOf("第3部分", "Part 3", "第3クール"), SplitSeason.genericMarkers(2))
    }
}

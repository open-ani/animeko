/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.domain.mediasource.MediaListFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeasonPartInfoTest {
    private fun subject(id: Int, vararg names: String, episodes: Int = 12) = SeriesMainSubject(id, names.toList(), episodes)

    private val ajin = listOf(
        subject(1, "亚人", "亜人", episodes = 13),
        subject(2, "亚人 第2部分", "亜人 第2クール", episodes = 13),
    )

    @Test
    fun `second part has the offset and the names of the first part as merged entry names`() {
        val part = assertNotNull(SeasonPartInfo.compute(ajin, 2))
        assertEquals(1, part.partIndex)
        assertEquals(2, part.partCount)
        assertEquals(13, part.episodeOffset)
        assertEquals(13, part.episodeCount)
        assertEquals(setOf("亚人", "亜人"), part.mergedEntryNames)
        assertTrue("亚人 Part.2" in part.ownSynthesizedNames)
        assertTrue("亚人 第2部分" in part.ownSynthesizedNames)
        assertTrue("亚人 第二季" in part.ownSynthesizedNames)
        assertTrue("亚人 后半" in part.ownSynthesizedNames)
        assertTrue("亜人 第二部分" in part.ownSynthesizedNames)
        assertNull(part.ownSeasonNumber)
    }

    @Test
    fun `first part knows the names the site may give to the second part`() {
        val part = assertNotNull(SeasonPartInfo.compute(ajin, 1))
        assertEquals(0, part.partIndex)
        assertEquals(0, part.episodeOffset)
        assertEquals(emptySet(), part.mergedEntryNames)
        assertTrue("亚人 Part.1" in part.ownSynthesizedNames)
        assertTrue("亚人 前半" in part.ownSynthesizedNames)
        assertTrue("亚人 第二季" in part.otherPartSynthesizedNames)
        assertTrue("亚人 Part.2" in part.otherPartSynthesizedNames)
        assertEquals(setOf("亚人 第2部分", "亜人 第2クール"), part.laterPartNames)
    }

    @Test
    fun `parts sharing an explicit season number form a group whose base is the season prefix`() {
        val mainline = listOf(
            subject(1, "Re：从零开始的异世界生活", episodes = 25),
            subject(2, "Re：从零开始的异世界生活 第二季", episodes = 13),
            subject(3, "Re：从零开始的异世界生活 第二季 后半部分", episodes = 12),
            subject(4, "Re：从零开始的异世界生活 第三季 袭击篇", episodes = 8),
            subject(5, "Re：从零开始的异世界生活 第三季 反击篇", episodes = 8),
        )
        val counterattack = assertNotNull(SeasonPartInfo.compute(mainline, 5))
        assertEquals(1, counterattack.partIndex)
        assertEquals(8, counterattack.episodeOffset)
        assertTrue("Re：从零开始的异世界生活 第三季" in counterattack.mergedEntryNames)
        assertTrue("Re：从零开始的异世界生活 第三季 袭击篇" in counterattack.mergedEntryNames)
        assertTrue("Re：从零开始的异世界生活 第三季 Part.2" in counterattack.ownSynthesizedNames)
        assertTrue("Re：从零开始的异世界生活 第三季 后半" in counterattack.ownSynthesizedNames)
        // 基础名已经带季度, 不合成「第四季」
        assertTrue(counterattack.ownSynthesizedNames.none { "第四季" in it || "第4季" in it })
        assertEquals(3, counterattack.ownSeasonNumber)

        val secondHalf = assertNotNull(SeasonPartInfo.compute(mainline, 3))
        assertEquals(13, secondHalf.episodeOffset)
        assertEquals(setOf("Re：从零开始的异世界生活 第二季"), secondHalf.mergedEntryNames)
        assertEquals(2, secondHalf.ownSeasonNumber)

        assertNull(SeasonPartInfo.compute(mainline, 1))
    }

    @Test
    fun `season alias is only synthesized when no other subject in the series has that season`() {
        val stoneWorld = listOf(
            subject(1, "石纪元", episodes = 24),
            subject(2, "石纪元 第二季 石之战争", episodes = 11),
            subject(3, "石纪元 新世界", episodes = 11),
            subject(4, "石纪元 新世界 第2部分", episodes = 11),
        )
        val part = assertNotNull(SeasonPartInfo.compute(stoneWorld, 4))
        assertTrue("石纪元 新世界 Part.2" in part.ownSynthesizedNames)
        assertFalse("石纪元 新世界 第二季" in part.ownSynthesizedNames)

        // Bangumi 给第二部分本身标的「2nd Season」不算
        val eightySix = listOf(
            subject(1, "86 -不存在的战区-", episodes = 11),
            subject(2, "86 -不存在的战区- 第2部分", "86 EIGHTY-SIX 2nd Season", episodes = 12),
        )
        val second = assertNotNull(SeasonPartInfo.compute(eightySix, 2))
        assertTrue(second.ownSynthesizedNames.any { MediaListFilters.specialEquals(it, "86 -不存在的战区- 第二季") })
        assertEquals(2, second.ownSeasonNumber)
    }

    @Test
    fun `ordinary sequels are not parts`() {
        val mainline = listOf(subject(1, "出包王女"), subject(2, "出包王女 第二季"), subject(3, "出包王女 Darkness"))
        assertNull(SeasonPartInfo.compute(mainline, 1))
        assertNull(SeasonPartInfo.compute(mainline, 2))
        assertNull(SeasonPartInfo.compute(mainline, 3))
    }

    @Test
    fun `unknown episode count disables part detection`() {
        val mainline = listOf(subject(1, "亚人", episodes = 13), subject(2, "亚人 第2部分", episodes = 0))
        assertNull(SeasonPartInfo.compute(mainline, 1))
        assertNull(SeasonPartInfo.compute(mainline, 2))
    }

    @Test
    fun `subject outside the mainline is not a part`() {
        assertNull(SeasonPartInfo.compute(ajin, 99))
    }

    @Test
    fun `three parts accumulate offsets and have no half aliases`() {
        val mainline = listOf(
            subject(1, "JOJO的奇妙冒险 石之海", episodes = 12),
            subject(2, "JOJO的奇妙冒险 石之海 第2部分", episodes = 12),
            subject(3, "JOJO的奇妙冒险 石之海 第3部分", episodes = 14),
        )
        val third = assertNotNull(SeasonPartInfo.compute(mainline, 3))
        assertEquals(2, third.partIndex)
        assertEquals(3, third.partCount)
        assertEquals(24, third.episodeOffset)
        assertEquals(14, third.episodeCount)
        assertTrue("JOJO的奇妙冒险 石之海" in third.mergedEntryNames)
        assertTrue("JOJO的奇妙冒险 石之海 第2部分" in third.mergedEntryNames)
        assertTrue("JOJO的奇妙冒险 石之海 Part.3" in third.ownSynthesizedNames)
        assertTrue(third.ownSynthesizedNames.none { "后半" in it })
    }

    @Test
    fun `season numbers`() {
        assertEquals(2, SeasonPartNames.seasonNumber("出包王女 第二季"))
        assertEquals(2, SeasonPartNames.seasonNumber("黒子のバスケ 第2期"))
        assertEquals(2, SeasonPartNames.seasonNumber("Mushoku Tensei 2nd Season"))
        assertEquals(3, SeasonPartNames.seasonNumber("Season 3"))
        assertEquals(2, SeasonPartNames.seasonNumber("无职转生Ⅱ"))
        assertEquals(2, SeasonPartNames.seasonNumber("Sword Art Online II"))
        assertNull(SeasonPartNames.seasonNumber("MIX"))
        assertNull(SeasonPartNames.seasonNumber("Vivy -Fluorite Eye's Song-"))
        assertNull(SeasonPartNames.seasonNumber("五等份的花嫁"))
        assertNull(SeasonPartNames.seasonNumber("亚人 第2部分"))
    }

    @Test
    fun `part markers`() {
        assertTrue(SeasonPartNames.hasPartMarker("间谍过家家 Part 2"))
        assertTrue(SeasonPartNames.hasPartMarker("亜人 第2クール"))
        assertTrue(SeasonPartNames.hasPartMarker("白色相簿-后半"))
        assertTrue(SeasonPartNames.hasPartMarker("进击的巨人 最终季 Part.2"))
        assertTrue(SeasonPartNames.hasPartMarker("涩谷♡八 第二部分"))
        assertFalse(SeasonPartNames.hasPartMarker("出包王女 第二季"))
        assertFalse(SeasonPartNames.hasPartMarker("Part Time Hero"))
        assertEquals("白色相簿", SeasonPartNames.stripPartMarker("白色相簿-后半"))
        assertEquals("进击的巨人 最终季", SeasonPartNames.stripPartMarker("进击的巨人 最终季 Part.2"))
        assertEquals("出包王女 第二季", SeasonPartNames.stripPartMarker("出包王女 第二季"))
    }

    @Test
    fun `roman numerals become digits only as standalone season markers`() {
        assertEquals("无职转生2", SeasonPartNames.romanNumeralsToDigits("无职转生Ⅱ"))
        assertEquals("Sword Art Online 2", SeasonPartNames.romanNumeralsToDigits("Sword Art Online II"))
        assertEquals("MIX", SeasonPartNames.romanNumeralsToDigits("MIX"))
        assertEquals("OVA", SeasonPartNames.romanNumeralsToDigits("OVA"))
    }

    @Test
    fun `same part group`() {
        assertTrue(SeasonPartNames.isSamePartGroup("亚人", "亚人 第2部分"))
        assertTrue(SeasonPartNames.isSamePartGroup("白色相簿", "白色相簿-后半"))
        assertTrue(SeasonPartNames.isSamePartGroup("Re：0 第三季 袭击篇", "Re：0 第三季 反击篇"))
        assertTrue(SeasonPartNames.isSamePartGroup("进击的巨人 最终季", "进击的巨人 最终季 Part.2"))
        assertFalse(SeasonPartNames.isSamePartGroup("出包王女", "出包王女 第二季"))
        assertFalse(SeasonPartNames.isSamePartGroup("Re：0 第二季", "Re：0 第三季 袭击篇"))
        assertFalse(SeasonPartNames.isSamePartGroup("石纪元 第二季 石之战争", "石纪元 新世界"))
    }
}

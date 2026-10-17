/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.app.data.models.subject.SplitSeasonTestData
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.MushokuTensei
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.ReZero
import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitSeasonDanmakuMatcherTest {
    /**
     * #3414: 夺还篇第 5 集在弹弹合并的第四季番剧上是第 16 话.
     */
    @Test
    fun `ReZero S4 part 2 on merged anime`() {
        val matcher = matcher(ReZero, 633836, sort = 82, ep = 5)

        assertEquals(listOf(547888), matcher.previousPartSubjectIds)
        assertEquals(setOf(16), matcher.currentNumbers("Re：从零开始的异世界生活 第四季", 1..19))
        assertEquals(setOf(16), matcher.currentNumbers("Re：从零开始的异世界生活 第四季 丧失篇", 1..19))
    }

    @Test
    fun `ReZero S4 part 2 on own anime`() {
        val matcher = matcher(ReZero, 633836, sort = 82, ep = 5)

        assertEquals(setOf(5), matcher.currentNumbers("Re：从零开始的异世界生活 第四季 夺还篇", 1..8))
        assertEquals(setOf(16), matcher.currentNumbers("Re：从零开始的异世界生活 第四季 夺还篇", 12..19))
    }

    @Test
    fun `ReZero S4 part 2 is not on anime holding only part 1`() {
        val matcher = matcher(ReZero, 633836, sort = 82, ep = 5)

        assertEquals(emptySet(), matcher.currentNumbers("Re：从零开始的异世界生活 第四季 丧失篇", 1..11))
        assertEquals(emptySet(), matcher.currentNumbers("Re：从零开始的异世界生活 第四季", 1..11))
    }

    @Test
    fun `ReZero S4 part 2 excludes anime of other seasons`() {
        val matcher = matcher(ReZero, 633836, sort = 82, ep = 5)

        assertTrue(matcher.isOtherSeason("Re：从零开始的异世界生活"))
        assertTrue(matcher.isOtherSeason("Re：从零开始的异世界生活 第三季"))
        assertFalse(matcher.isOtherSeason("Re：从零开始的异世界生活 第四季"))
        assertEquals(emptySet(), matcher.currentNumbers("Re：从零开始的异世界生活 第三季", 1..16))
    }

    /**
     * 只与本季名字相似的番剧当作合并番剧, 弹弹把它关联到当前条目时当作当前条目自己的.
     */
    @Test
    fun `MushokuTensei S1 part 2 on similarly named anime`() {
        val matcher = matcher(MushokuTensei, 325585, sort = 14, ep = 3)

        assertEquals(emptySet(), matcher.currentNumbers("Mushoku Tensei", 1..12))
        assertEquals(setOf(3), matcher.currentNumbers("Mushoku Tensei", 1..12, mappedToSelf = true))
        assertEquals(setOf(14), matcher.currentNumbers("Mushoku Tensei", 1..23))
        assertEquals(setOf(14), matcher.currentNumbers("Mushoku Tensei", 1..23, mappedToSelf = true))
    }

    @Test
    fun `no matcher for first part or non-split subject`() {
        assertNull(create(ReZero, 547888, sort = 72, ep = 6))
        assertNull(SplitSeasonDanmakuMatcher.create(null, ReZero.names(633836), EpisodeSort(82), EpisodeSort(5)))
    }

    private fun matcher(series: SplitSeasonTestData.Series, subjectId: Int, sort: Int, ep: Int): SplitSeasonDanmakuMatcher =
        assertNotNull(create(series, subjectId, sort, ep))

    private fun create(series: SplitSeasonTestData.Series, subjectId: Int, sort: Int, ep: Int): SplitSeasonDanmakuMatcher? =
        SplitSeasonDanmakuMatcher.create(series.splitSeason(subjectId), series.names(subjectId), EpisodeSort(sort), EpisodeSort(ep))

    private fun SplitSeasonDanmakuMatcher.currentNumbers(
        subjectName: String,
        numbers: IntRange,
        mappedToSelf: Boolean = false,
    ): Set<Int> {
        val page = numbers.toSet()
        return page.filterTo(HashSet()) { isCurrentEpisode(subjectName, page, it, mappedToSelf) }
    }
}

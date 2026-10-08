/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.subject.SplitSeasonTestData
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.EightySix
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.MushokuTensei
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.ReZero
import me.him188.ani.app.domain.media.selector.filter.SplitSeasonPageMatcher
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestContainer
class BrowseEpisodeIndexTest {
    private fun episodes(numbers: Iterable<Int>): List<BrowseEpisode> =
        numbers.map { BrowseEpisode(name = "第${it}集", url = "https://example.com/play/$it", episodeSort = EpisodeSort(it)) }

    private fun episode(raw: String): BrowseEpisode = BrowseEpisode(name = raw, url = "https://example.com/play/$raw", episodeSort = EpisodeSort(raw))

    /**
     * 正在观看 [series] 里条目 [selfId] 的 sort 为 [sort]、ep 为 [ep] 的一集, 打开页面 [pageName] 时预选的下标.
     */
    private fun preselect(
        series: SplitSeasonTestData.Series,
        selfId: Int,
        sort: Int,
        ep: Int,
        pageName: String,
        episodes: List<BrowseEpisode>,
    ): Int? = preselectedBrowseEpisodeIndex(
        pageName, episodes, EpisodeSort(sort), EpisodeSort(ep),
        splitSeason = series.splitSeason(selfId), subjectNames = series.names(selfId),
    )

    /**
     * 不是拆分季时预选的下标.
     */
    private fun preselect(sort: Int, ep: Int?, pageName: String, episodes: List<BrowseEpisode>): Int? =
        preselectedBrowseEpisodeIndex(pageName, episodes, EpisodeSort(sort), ep?.let { EpisodeSort(it) }, splitSeason = null, subjectNames = listOf(pageName))

    @Test
    fun `merged season page preselects the season number`() {
        assertEquals(13, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季", episodes(1..25)))
        assertEquals(14, preselect(ReZero, 316247, sort = 40, ep = 2, "Re：从零开始的异世界生活 第二季", episodes(1..25)))
    }

    @Test
    fun `own page numbered from 1 preselects ep`() {
        assertEquals(0, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 后半部分", episodes(1..12)))
    }

    @Test
    fun `own page continuing the numbering preselects the season number`() {
        assertEquals(0, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 Part.2", episodes(14..25)))
        assertEquals(0, preselect(MushokuTensei, 325585, sort = 12, ep = 1, "无职转生～到了异世界就拿出真本事～ 第2部分", episodes(12..23)))
    }

    @Test
    fun `page numbered above 1 also accepts the sort`() {
        assertEquals(0, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 后半部分", episodes(39..50)))
        assertEquals(13, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季", episodes(26..50)))
    }

    @Test
    fun `own page holding the whole season preselects the season number`() {
        assertEquals(13, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 后半部分", episodes(1..25)))
        assertEquals(0, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 后半部分", episodes(1..14)))
    }

    @Test
    fun `page only similar to the season name is treated as a merged page`() {
        assertEquals(11, preselect(EightySix, 331887, sort = 12, ep = 1, "86 不存在的战区 全集", episodes(1..25)))
        assertNull(preselect(EightySix, 331887, sort = 12, ep = 1, "86 不存在的战区 全集", episodes(1..12)))
    }

    @Test
    fun `season page without the season number does not fall back to the sort`() {
        val missing14 = episodes(1..13) + episodes(15..25) + episodes(listOf(39))
        assertNull(preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季", missing14))
        assertNull(preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季 后半部分", episodes(2..12)))
    }

    @Test
    fun `page of another season preselects only the sort`() {
        assertEquals(38, preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活", episodes(1..50)))
        assertNull(preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第三季 反击篇", episodes(1..16)))
    }

    @Test
    fun `merged page holding only the first part preselects nothing`() {
        assertEquals(11, preselect(EightySix, 331887, sort = 12, ep = 1, "86-不存在的战区-", episodes(1..25)))
        assertNull(preselect(EightySix, 331887, sort = 12, ep = 1, "86-不存在的战区-", episodes(1..11) + episode("11.5")))
        assertNull(preselect(EightySix, 331887, sort = 12, ep = 1, "86-不存在的战区-", episodes(1..11)))
    }

    @Test
    fun `page without numbered episodes preselects nothing`() {
        val unnumbered = listOf(BrowseEpisode(name = "正片", url = "https://example.com/play/full", episodeSort = null))
        assertNull(preselect(ReZero, 316247, sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季", unnumbered))
        assertNull(preselect(sort = 39, ep = 1, "Re：从零开始的异世界生活 第二季", unnumbered))
    }

    @Test
    fun `first part of a split season uses sort then ep like any subject`() {
        assertEquals(0, preselect(ReZero, 278826, sort = 26, ep = 1, "Re：从零开始的异世界生活 第二季", episodes(1..25)))
        assertEquals(0, preselect(ReZero, 278826, sort = 26, ep = 1, "Re：从零开始的异世界生活 第二季", episodes(26..38)))
    }

    @Test
    fun `without split season the sort comes first and ep is the fallback`() {
        assertEquals(50, preselect(sort = 51, ep = 1, "Re：从零开始的异世界生活", episodes(1..66)))
        assertEquals(0, preselect(sort = 51, ep = 1, "Re：从零开始的异世界生活 第三季", episodes(1..16)))
        assertNull(preselect(sort = 51, ep = null, "Re：从零开始的异世界生活 第三季", episodes(1..16)))
        assertEquals(11, preselect(sort = 12, ep = 1, "86-不存在的战区-", episodes(1..25)))
    }

    @Test
    fun `page rule needs a later part with integer sort and ep`() {
        assertNull(SplitSeasonPageMatcher.create(ReZero.splitSeason(278826)!!, ReZero.names(278826), EpisodeSort(26), EpisodeSort(1)))
        assertNull(SplitSeasonPageMatcher.create(ReZero.splitSeason(316247)!!, ReZero.names(316247), EpisodeSort("39.5"), EpisodeSort(1)))
        assertNull(SplitSeasonPageMatcher.create(ReZero.splitSeason(316247)!!, ReZero.names(316247), EpisodeSort(39), null))
        assertNull(SplitSeasonPageMatcher.create(ReZero.splitSeason(316247)!!, ReZero.names(316247), EpisodeSort(39), EpisodeSort("1.5")))
        assertNull(SplitSeasonPageMatcher.create(ReZero.splitSeason(316247)!!, listOf("", " "), EpisodeSort(39), EpisodeSort(1)))
    }
}

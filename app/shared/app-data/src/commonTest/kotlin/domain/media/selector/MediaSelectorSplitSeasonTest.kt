/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.subject.SplitSeason
import me.him188.ani.app.data.models.subject.SplitSeasonTestData
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.AttackOnTitan
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.EightySix
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.MushokuTensei
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.ReZero
import me.him188.ani.app.data.models.subject.SplitSeasonTestData.SpyFamily
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 正在观看拆分季 ([SplitSeason]) 的后半时, 站点页面的各种编号方式. 页面形态取自 2026-10 对几个常用网页源的抓取:
 *
 * - 一个条目一页, 从第 1 集编号;
 * - 整季合成一页, 从第 1 集编号, 页名是前半的名字或季名 (Re0 第二季、间谍过家家; Re0 第三季、第四季 "丧失篇&夺还篇");
 * - 后半自己一页但接着前半编号 (Re0 第二季 Part.2 = 14 至 25, 无职Ⅱ Part 2 = 13 至 24);
 * - 同一页的不同线路编号方式不同 (无职Ⅱ Part 2 一条线路 13 至 24, 另一条 1 至 12);
 * - 用后半的名字命名但装着整季 (无职 第2部分 1 至 24, "第四季 夺还篇" 1 至 19).
 */
@TestContainer
class MediaSelectorSplitSeasonTest {
    @Test
    fun `ReZero S2 part 2 on merged page named after part 1`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 39, ep = 1)
        val merged = addPage("Re：从零开始的异世界生活 第二季", 1..25)
        val partOneOnly = addPage("Re：从零开始的异世界生活 第二季", 1..13, source = "web2")

        assertPage(merged, 14 to "included EXACT SEASON")
        assertPage(partOneOnly)
    }

    @Test
    fun `ReZero S2 part 2 on own page continuing the season numbering`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 39, ep = 1)
        val continuing = addPage("Re：从零开始的异世界生活 第二季 Part.2", 14..25)
        val lowerPart = addPage("Re：从零开始的异世界生活 第二季 下部", 14..25, source = "web4")
        val upperPart = addPage("Re：从零开始的异世界生活 第二季 上部", 1..13, source = "web4")

        assertPage(continuing, 14 to "included EXACT SEASON")
        assertPage(lowerPart, 14 to "included EXACT SEASON")
        assertPage(upperPart)
    }

    @Test
    fun `ReZero S2 part 2 on own page restarting from 1`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 43, ep = 5)
        val restarting = addPage("Re：从零开始的异世界生活 第二季 Part.2", 1..12)
        val merged = addPage("Re：从零开始的异世界生活 第二季", 1..25, source = "web1")

        assertPage(restarting, 5 to "included EXACT EP")
        assertPage(merged, 18 to "included EXACT SEASON")
    }

    @Test
    fun `ReZero S2 part 2 does not take the first season page`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 39, ep = 1)
        val firstSeason = addPage("Re：从零开始的异世界生活", 1..25)

        assertPage(firstSeason, default = "excluded FromSeriesSeason")
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    @Test
    fun `ReZero S2 part 2 BT resources keep sort matching`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 39, ep = 1)
        // 完结番的 BT 单集会被另一条规则隐藏, 用合集
        val bySort = mediaApi.addMedia(
            media(episodeRange = EpisodeRange.range(EpisodeSort(39), EpisodeSort(40)), originalTitle = "[Sub] Re:Zero 2nd Season - 39-40"),
        )
        val bySeason = mediaApi.addMedia(
            media(episodeRange = EpisodeRange.range(EpisodeSort(14), EpisodeSort(15)), originalTitle = "[Sub] Re:Zero 2nd Season - 14-15"),
        )

        val candidates = selector.filteredCandidates.first()
        assertEquals("included FUZZY SORT", candidates.single { it.original === bySort }.describe())
        assertEquals("excluded EpisodeMismatch", candidates.single { it.original === bySeason }.describe())
    }

    @Test
    fun `ReZero S4 part 2 on own and sibling pages`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 633836, sort = 78, ep = 1)
        val own = addPage("Re：从零开始的异世界生活 第四季 夺还篇", 1..8)
        val sibling = addPage("Re：从零开始的异世界生活 第四季 丧失篇", 1..11)
        val ownWithExtras = addPage("Re：从零开始的异世界生活 第四季 夺还篇", 1..10, source = "other")

        assertPage(own, 1 to "included EXACT EP")
        assertPage(sibling)
        assertPage(ownWithExtras, 1 to "included EXACT EP")
    }

    @Test
    fun `ReZero S4 part 2 on merged pages`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 633836, sort = 78, ep = 1)
        val bothArcs = addPage("Re：从零开始的异世界生活 第四季 丧失篇&夺还篇", 1..19, source = "web2")
        val seasonName = addPage("Re：从零开始的异世界生活 第四季", 1..19, source = "web3", channel = "线路B")
        val seasonNamePartOneOnly = addPage("Re：从零开始的异世界生活 第四季", 1..11, source = "web3", channel = "线路C")
        val ownNameWholeSeason = addPage("Re：从零开始的异世界生活 第四季 夺还篇", 1..19, source = "web4")

        assertPage(bothArcs, 12 to "included EXACT SEASON")
        assertPage(seasonName, 12 to "included EXACT SEASON")
        assertPage(seasonNamePartOneOnly)
        assertPage(ownNameWholeSeason, 12 to "included EXACT SEASON")
    }

    @Test
    fun `ReZero S4 part 1 keeps the usual rules`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 547888, sort = 67, ep = 1)
        val own = addPage("Re：从零开始的异世界生活 第四季 丧失篇", 1..11)
        val bothArcs = addPage("Re：从零开始的异世界生活 第四季 丧失篇&夺还篇", 1..19, source = "web2")
        val partTwo = addPage("Re：从零开始的异世界生活 第四季 夺还篇", 1..8)

        assertPage(own, 1 to "included EXACT EP")
        assertPage(bothArcs, 1 to "included FUZZY EP")
        assertPage(partTwo, 1 to "excluded FromSequelSeason")
    }

    @Test
    fun `MushokuTensei S2 part 2 with prologue at sort 0`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(MushokuTensei, 444557, sort = 13, ep = 1)
        val own = addPage("无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", 1..13)
        val partOne = addPage("无职转生 第二季 ～到了异世界就拿出真本事～", 1..12, specials = listOf("OVA"))
        val partOneWithPrologue = addPage("无职转生 第二季 ～到了异世界就拿出真本事～", 0..12, source = "web4", channel = "线路J")
        val merged = addPage("无职转生 第二季 ～到了异世界就拿出真本事～", 1..24, source = "web4", channel = "线路D")
        // 序章可能占号: 只比前半多一个条目的合并页分不清第 13 个是前半最后一集还是后半第 1 集
        val mergedBarelyLonger = addPage("无职转生 第二季 ～到了异世界就拿出真本事～", 1..14, source = "web4", channel = "线路C")

        assertPage(own, 1 to "included EXACT EP")
        assertPage(partOne)
        assertPage(partOneWithPrologue)
        assertPage(merged, 13 to "included EXACT SEASON")
        assertPage(mergedBarelyLonger)
    }

    @Test
    fun `MushokuTensei S2 part 2 page whose channels number differently`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(MushokuTensei, 444557, sort = 13, ep = 1)
        val continuing = addPage("无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", 13..24, channel = "线路G")
        val restarting = addPage("无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", 1..12, channel = "线路C")

        assertPage(continuing, 13 to "included EXACT SEASON")
        assertPage(restarting, 1 to "included EXACT EP")
    }

    @Test
    fun `MushokuTensei S2 part 2 on page with romanized title`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(MushokuTensei, 444557, sort = 13, ep = 1)
        val continuing = addPage("无职转生Ⅱ 到了异世界就拿出真本事 Part 2", 13..24, source = "web2")
        val restarting = addPage("无职转生Ⅱ 到了异世界就拿出真本事 Part 2", 1..12, source = "web4")

        // 去掉 Part 2 后与季名足够相似, 罗马数字 Ⅱ 识别为第二季
        assertPage(continuing, 13 to "included FUZZY SEASON")
        assertPage(restarting, 1 to "included FUZZY EP")
    }

    @Test
    fun `MushokuTensei S2 part 2 ignores pages of other seasons`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(MushokuTensei, 444557, sort = 13, ep = 1)
        // 第一季第2部分的页没有季度编号, 去掉 Part.2 后与第二季季名只差一个 "第二季"
        val firstSeasonPartTwo = addPage("无职转生：到了异世界就拿出真本事 Part.2", 1..12)
        val firstSeasonMerged = addPage("无职转生：到了异世界就拿出真本事 Part.2", 1..24, source = "web3")
        val thirdSeason = addPage("无职转生Ⅲ 到了异世界就拿出真本事", 1..14, source = "web2")
        // 第二季前半把序章记作第 1 集, 共 13 集, 第 13 集不是第2部分的第 1 集
        val partOneWithPrologueAsOne = addPage("无职转生Ⅱ 到了异世界就拿出真本事", 1..13, source = "web4")

        assertPage(firstSeasonPartTwo, default = "excluded FromSeriesSeason")
        assertPage(firstSeasonMerged, default = "excluded FromSeriesSeason")
        assertPage(thirdSeason, default = "excluded FromSeriesSeason")
        assertPage(partOneWithPrologueAsOne)
    }

    @Test
    fun `ReZero S2 part 2 ignores pages of other seasons`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(ReZero, 316247, sort = 39, ep = 1)
        val thirdSeason = addPage("Re：从零开始的异世界生活第三季", 1..16, source = "web2")
        val fourthSeason = addPage("Re：从零开始的异世界生活 第四季", 1..19, source = "web3")
        val firstSeason = addPage("Re：从零开始的异世界生活第一季", 1..25, source = "web4")
        val withoutPrefix = addPage("从零开始的异世界生活 第二季 下部", 14..25, source = "web4")

        assertPage(thirdSeason, default = "excluded FromSeriesSeason")
        assertPage(fourthSeason, default = "excluded FromSeriesSeason")
        assertPage(firstSeason, default = "excluded FromSeriesSeason")
        assertPage(withoutPrefix, 14 to "included FUZZY SEASON")
    }

    @Test
    fun `MushokuTensei S1 part 2 on own-named page holding the whole season`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(MushokuTensei, 325585, sort = 12, ep = 1)
        val own = addPage("无职转生～到了异世界就拿出真本事～ 第2部分", 1..12, specials = listOf("OVA"))
        val wholeSeason = addPage("无职转生～到了异世界就拿出真本事～ 第2部分", 1..24, source = "web3", channel = "线路C")
        val mergedPartOneName = addPage("无职转生：到了异世界就拿出真本事", 1..23, source = "web2", specials = listOf("OVA"))

        assertPage(own, 1 to "included EXACT EP")
        assertPage(wholeSeason, 12 to "included EXACT SEASON")
        assertPage(mergedPartOneName, 12 to "included EXACT SEASON")
    }

    @Test
    fun `SpyFamily part 2 pages`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(SpyFamily, 373267, sort = 13, ep = 1)
        val merged = addPage("间谍过家家", 1..25)
        val own = addPage("间谍过家家 第2部分", 1..13)
        val partOneOnly = addPage("间谍过家家", 1..12, source = "web2")
        val continuing = addPage("间谍过家家 Part 2", 13..25, source = "web2")
        val secondSeason = addPage("间谍过家家 第二季", 1..12)
        val secondSeasonDub = addPage("间谍过家家第二季国语", 1..36, source = "web4")
        val bothSeasons = addPage("间谍过家家 第1&2季", 1..37, source = "web4")
        val dub = addPage("间谍过家家国语版", 1..25, source = "web4")

        assertPage(merged, 13 to "included EXACT SEASON")
        assertPage(own, 1 to "included EXACT EP")
        assertPage(partOneOnly)
        assertPage(continuing, 13 to "included EXACT SEASON")
        assertPage(secondSeason, default = "excluded FromSeriesSeason")
        assertPage(secondSeasonDub, default = "excluded FromSeriesSeason")
        assertPage(bothSeasons, 13 to "included FUZZY SEASON")
        assertPage(dub, 13 to "included FUZZY SEASON")
    }

    @Test
    fun `EightySix part 2 own page matches by ep even though sort 12 is on the page`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(EightySix, 331887, sort = 12, ep = 1)
        val own = addPage("86 -不存在的战区- 第2部分", 1..12, specials = listOf("7.5"))
        val merged = addPage("86 -不存在的战区-", 1..25, source = "web3")
        val namedAsSecondSeason = addPage("86 -不存在的战区-第二季", 12..23, source = "web2", specials = listOf("17.5", "18.5", "21.5"))
        // 站点把后半叫 "第二季", Bangumi 的别名也叫 2nd Season; 从 1 编号的是后半自己的页
        val namedAsSecondSeasonRestarting = addPage("86 -不存在的战区- 第二季", 1..12, source = "web4")
        // 前半 11 集之后有 11.5 集: 只有 12 个条目的页面分不清第 12 个是它还是后半第 1 集, 13 个才算合并页
        val namedAsFirstSeason = addPage("86 -不存在的战区第一季", 1..12, source = "web4")
        val mergedBarelyLonger = addPage("86 -不存在的战区-", 1..12, source = "web4", channel = "线路B")
        val mergedInProgress = addPage("86 -不存在的战区-", 1..13, source = "web4", channel = "线路C")

        assertPage(own, 1 to "included EXACT EP")
        assertPage(merged, 12 to "included EXACT SEASON")
        assertPage(namedAsSecondSeason, 12 to "included FUZZY SEASON")
        assertPage(namedAsSecondSeasonRestarting, 1 to "included FUZZY EP")
        assertPage(namedAsFirstSeason)
        assertPage(mergedBarelyLonger)
        assertPage(mergedInProgress, 12 to "included EXACT SEASON")
    }

    @Test
    fun `AttackOnTitan final season part 2`() = runSimpleMediaSelectorTestSuite {
        initSplitSeason(AttackOnTitan, 331752, sort = 76, ep = 1)
        val own = addPage("进击的巨人 最终季 Part.2", 1..12)
        val partOne = addPage("进击的巨人 最终季", 1..16)
        val merged = addPage("进击的巨人 最终季", 1..28, source = "web4", channel = "线路J")

        assertPage(own, 1 to "included EXACT EP")
        assertPage(partOne)
        assertPage(merged, 17 to "included EXACT SEASON")
    }

    private class Page(val source: String, val title: String, val channel: String, val numbers: List<Int>)

    private fun SimpleMediaSelectorTestSuite.addPage(
        title: String,
        numbers: Iterable<Int>,
        source: String = "web1",
        channel: String = "简中",
        specials: List<String> = emptyList(),
    ): Page {
        for (number in numbers) {
            mediaApi.addMedia(
                media(
                    sourceId = source,
                    alliance = channel,
                    episodeRange = EpisodeRange.single(EpisodeSort(number)),
                    kind = MediaSourceKind.WEB,
                    subjectName = title,
                    originalTitle = "$title 第${number}集",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        }
        for (special in specials) {
            mediaApi.addMedia(
                media(
                    sourceId = source,
                    alliance = channel,
                    episodeRange = EpisodeRange.single(EpisodeSort(special)),
                    kind = MediaSourceKind.WEB,
                    subjectName = title,
                    originalTitle = "$title $special",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        }
        return Page(source, title, channel, numbers.toList())
    }

    /**
     * 断言 [page] 上每一集的结果. [outcomes] 未列出的集应为 [default], 默认以集数不符排除. 特别篇不断言.
     */
    @OptIn(UnsafeOriginalMediaAccess::class)
    private suspend fun SimpleMediaSelectorTestSuite.assertPage(
        page: Page,
        vararg outcomes: Pair<Int, String>,
        default: String = "excluded EpisodeMismatch",
    ) {
        val actual = selector.filteredCandidates.first()
            .filter {
                it.original.mediaSourceId == page.source &&
                        it.original.properties.subjectName == page.title &&
                        it.original.properties.alliance == page.channel
            }
            .mapNotNull { candidate ->
                val number = (candidate.original.episodeRange!!.knownSorts.single() as? EpisodeSort.Normal)?.number
                if (number == null || number % 1f != 0f) null else number.toInt() to candidate.describe()
            }
            .toMap()
        val expected = page.numbers.associateWith { number -> outcomes.toMap()[number] ?: default }
        assertEquals(expected, actual, "${page.source} ${page.title} ${page.channel}")
    }

    private fun MaybeExcludedMedia.describe(): String = when (this) {
        is MaybeExcludedMedia.Included -> "included ${metadata.subjectMatchKind} ${metadata.episodeMatchKind}"
        is MaybeExcludedMedia.Excluded -> "excluded ${exclusionReason::class.simpleName}"
    }

    /**
     * 按 Bangumi 数据初始化 [series] 中的条目 [selfId], 正在观看 sort 为 [sort], ep 为 [ep] 的一集.
     * 系列名与续集名按 `SubjectSeriesInfo.compute` 的规则去掉当前条目自己的名字, 拆分季用服务端对该条目下发的结果.
     */
    private fun MediaSelectorTestSuite.initSplitSeason(series: SplitSeasonTestData.Series, selfId: Int, sort: Int, ep: Int) {
        val selfIndex = series.subjects.indexOfFirst { it.first == selfId }
        val own = series.names(selfId)
        initSubject(own.first()) {
            aliases(*own.drop(1).toTypedArray())
            episodeSort = EpisodeSort(sort)
            episodeEp = EpisodeSort(ep)
            seriesInfo(seasonSort = selfIndex + 1) {
                fun isOwnName(name: String) = own.any { MediaListFilters.specialEquals(it, name) }
                series(*series.subjects.flatMap { it.second }.filterNot(::isOwnName).toTypedArray())
                sequel(
                    *series.subjects.drop(selfIndex + 1).flatMap { it.second }
                        .filterNot { name -> own.any { MediaListFilters.specialContains(it, name) } }
                        .toTypedArray(),
                )
                splitSeason = series.splitSeason(selfId)
            }
        }
    }
}

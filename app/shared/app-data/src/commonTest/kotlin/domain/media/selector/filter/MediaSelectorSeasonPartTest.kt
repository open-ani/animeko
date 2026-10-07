/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeOriginalMediaAccess::class)

package me.him188.ani.app.domain.media.selector.filter

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SeasonPartInfo
import me.him188.ani.app.data.models.subject.SeriesMainSubject
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * 分部条目的过滤: 站点把 Bangumi 拆开的各部分合并、接着编号、从 1 重编, 或者用站点自己的叫法单独建条目.
 */
class MediaSelectorSeasonPartTest {
    private val algorithm = MediaSelectorFilterSortAlgorithm()

    /**
     * 一个站点条目 [name] 的第 [numbers] 集, 每集一个 WEB 资源.
     */
    private fun entry(name: String, numbers: IntRange, source: String = "site", channel: String = "线路1"): List<Media> =
        numbers.map { n ->
            createTestDefaultMedia(
                mediaId = "$source.$name.$channel.$n",
                mediaSourceId = source,
                originalUrl = "https://example.com/$name/$n",
                download = ResourceLocation.WebVideo("https://example.com/$name/$n"),
                originalTitle = "$name 第${n}集",
                publishedTime = 0,
                properties = createTestMediaProperties(subjectName = name, alliance = channel),
                episodeRange = EpisodeRange.single(EpisodeSort(n)),
                location = MediaSourceLocation.Online,
                kind = MediaSourceKind.WEB,
            )
        }

    private fun context(
        mainline: List<SeriesMainSubject>,
        subjectId: Int,
        sort: Int,
        ep: Int,
    ): MediaSelectorContext {
        val own = mainline.first { it.subjectId == subjectId }
        val others = mainline.filter { it.subjectId != subjectId }
        val siblingNames = others.flatMap { it.names }
            .filter { sibling -> own.names.none { MediaListFilters.specialEquals(it, sibling) } }
            .toSet()
        val sequelNames = mainline.dropWhile { it.subjectId != subjectId }.drop(1).flatMap { it.names }.toSet()
        return MediaSelectorContext(
            subjectFinished = true,
            mediaSourcePrecedence = emptyList(),
            subtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo = SubjectSeriesInfo(
                seasonSort = mainline.indexOfFirst { it.subjectId == subjectId } + 1,
                sequelSubjectNames = sequelNames intersect siblingNames,
                seriesSubjectNamesWithoutSelf = siblingNames,
                seasonPart = SeasonPartInfo.compute(mainline, subjectId),
            ),
            subjectInfo = SubjectInfo.Empty.copy(
                subjectId = subjectId,
                nameCn = own.names.first(),
                name = own.names.getOrElse(1) { "" },
                aliases = own.names.drop(2),
            ),
            episodeInfo = EpisodeInfo.Empty.copy(episodeId = sort, sort = EpisodeSort(sort), ep = EpisodeSort(ep)),
            mediaSourceTiers = MediaSelectorSourceTiers.Empty,
        )
    }

    private fun filter(list: List<Media>, context: MediaSelectorContext): List<MaybeExcludedMedia> =
        algorithm.filterMediaList(list, MediaPreference.Empty, MediaSelectorSettings.Default, context)

    private fun List<MaybeExcludedMedia>.includedNumbers(): List<Pair<String, Int>> =
        filterIsInstance<MaybeExcludedMedia.Included>().map {
            it.result.properties.subjectName!! to it.result.episodeRange!!.knownSorts.single().number!!.toInt()
        }

    private fun List<MaybeExcludedMedia>.single(name: String, number: Int): MaybeExcludedMedia =
        single { it.original.properties.subjectName == name && it.original.episodeRange!!.knownSorts.single() == EpisodeSort(number) }

    private val reZero = listOf(
        SeriesMainSubject(1, listOf("Re：从零开始的异世界生活", "Re:ゼロから始める異世界生活"), 25),
        SeriesMainSubject(2, listOf("Re：从零开始的异世界生活 第二季", "Re:ゼロから始める異世界生活 2nd season"), 13),
        SeriesMainSubject(3, listOf("Re：从零开始的异世界生活 第二季 后半部分", "Re:ゼロから始める異世界生活 2nd season 後半クール"), 12),
        SeriesMainSubject(4, listOf("Re：从零开始的异世界生活 第三季 袭击篇", "Re:ゼロから始める異世界生活 3rd season 襲撃編"), 8),
        SeriesMainSubject(5, listOf("Re：从零开始的异世界生活 第三季 反击篇", "Re:ゼロから始める異世界生活 3rd season 反撃編"), 8),
    )

    private val ajin = listOf(
        SeriesMainSubject(1, listOf("亚人", "亜人"), 13),
        SeriesMainSubject(2, listOf("亚人 第2部分", "亜人 第2クール"), 13),
    )

    @Test
    fun `merged entry maps the episode by offset and is an exact match`() {
        // 站点把第三季两篇合并为 1-16 集, 反击篇第 1 集是第 9 集
        val list = entry("Re：从零开始的异世界生活 第三季", 1..16)
        val result = filter(list, context(reZero, subjectId = 5, sort = 59, ep = 1))
        assertEquals(listOf("Re：从零开始的异世界生活 第三季" to 9), result.includedNumbers())
        val included = assertIs<MaybeExcludedMedia.Included>(result.single("Re：从零开始的异世界生活 第三季", 9))
        assertEquals(MatchMetadata.SubjectMatchKind.EXACT, included.metadata.subjectMatchKind)
        assertEquals(MatchMetadata.EpisodeMatchKind.EP, included.metadata.episodeMatchKind)
        assertIs<MediaExclusionReason.EpisodeMismatch>(result.single("Re：从零开始的异世界生活 第三季", 1).exclusionReason)
    }

    @Test
    fun `merged entry is not used beyond the range of the current part`() {
        // 合并条目只有第一部分的 8 集: 反击篇第 1 集对不上
        val list = entry("Re：从零开始的异世界生活 第三季", 1..8)
        assertEquals(emptyList(), filter(list, context(reZero, subjectId = 5, sort = 59, ep = 1)).includedNumbers())
    }

    @Test
    fun `first part of a merged entry is matched by ep`() {
        val list = entry("Re：从零开始的异世界生活 第三季", 1..16)
        val result = filter(list, context(reZero, subjectId = 4, sort = 51, ep = 1))
        assertEquals(listOf("Re：从零开始的异世界生活 第三季" to 1), result.includedNumbers())
        assertEquals(
            MatchMetadata.SubjectMatchKind.EXACT,
            assertIs<MaybeExcludedMedia.Included>(result.single("Re：从零开始的异世界生活 第三季", 1)).metadata.subjectMatchKind,
        )
    }

    @Test
    fun `entry continuing the numbering is matched by sort only`() {
        val eightySix = listOf(
            SeriesMainSubject(1, listOf("86 -不存在的战区-", "86―エイティシックス―"), 11),
            SeriesMainSubject(2, listOf("86 -不存在的战区- 第2部分", "86―エイティシックス― 第2クール"), 12),
        )
        val list = entry("86 不存在的战区 第2部分", 12..23)
        val result = filter(list, context(eightySix, subjectId = 2, sort = 12, ep = 1))
        assertEquals(listOf("86 不存在的战区 第2部分" to 12), result.includedNumbers())
        assertEquals(
            MatchMetadata.EpisodeMatchKind.SORT,
            assertIs<MaybeExcludedMedia.Included>(result.single("86 不存在的战区 第2部分", 12)).metadata.episodeMatchKind,
        )
    }

    @Test
    fun `entry restarting from 1 is matched by ep even when sort is also present`() {
        // 第二部分 12 集比第一部分 11 集长: 从 1 起编号的条目里同时有 sort=12 和 ep=1
        val eightySix = listOf(
            SeriesMainSubject(1, listOf("86 -不存在的战区-"), 11),
            SeriesMainSubject(2, listOf("86 -不存在的战区- 第2部分"), 12),
        )
        val list = entry("86 不存在的战区 第2部分", 1..12)
        val result = filter(list, context(eightySix, subjectId = 2, sort = 12, ep = 1))
        assertEquals(listOf("86 不存在的战区 第2部分" to 1), result.includedNumbers())
    }

    @Test
    fun `entry named after both parts with more episodes than the part is a merged entry`() {
        val season4 = listOf(
            SeriesMainSubject(1, listOf("Re：从零开始的异世界生活 第四季 丧失篇", "Re:ZERO Season 4"), 11),
            SeriesMainSubject(2, listOf("Re：从零开始的异世界生活 第四季 夺还篇", "Re:ZERO Season 4"), 8),
        )
        // 名字只与「夺还篇」模糊匹配, 但 19 集比夺还篇的 8 集多: 是合并条目
        val list = entry("Re：从零开始的异世界生活 第四季 丧失篇&夺还篇", 1..19)
        assertEquals(
            listOf("Re：从零开始的异世界生活 第四季 丧失篇&夺还篇" to 12),
            filter(list, context(season4, subjectId = 2, sort = 78, ep = 1)).includedNumbers(),
        )
        assertEquals(
            listOf("Re：从零开始的异世界生活 第四季 丧失篇&夺还篇" to 1),
            filter(list, context(season4, subjectId = 1, sort = 67, ep = 1)).includedNumbers(),
        )
        // 两部分共用的英文别名: 合并条目按偏移, 只有本部分的条目按 ep
        assertEquals(
            listOf("Re:ZERO Season 4" to 12),
            filter(entry("Re:ZERO Season 4", 1..19), context(season4, subjectId = 2, sort = 78, ep = 1)).includedNumbers(),
        )
        assertEquals(
            listOf("Re:ZERO Season 4" to 1),
            filter(entry("Re:ZERO Season 4", 1..8), context(season4, subjectId = 2, sort = 78, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `own entry with one extra episode is still a restart entry once the part has aired`() {
        // 站点在 12 集的部分里多塞了一集总集篇: 13 集够不上 12 + 12 - 1
        val nier = listOf(
            SeriesMainSubject(1, listOf("尼尔：自动人形 Ver1.1a"), 12),
            SeriesMainSubject(2, listOf("尼尔：自动人形 Ver1.1a 第2部分"), 12),
        )
        val list = entry("尼尔：自动人形 Ver1.1a 第2部分", 1..13)
        assertEquals(
            listOf("尼尔：自动人形 Ver1.1a 第2部分" to 1),
            filter(list, context(nier, subjectId = 2, sort = 13, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `own named entry holding the whole season is a merged entry even while airing`() {
        // 站点的「第二季 第2部分」条目装着整季: 第二部分播到第 3 集时它有 15 集, 够得上 12 + 3 - 1
        val slime = listOf(
            SeriesMainSubject(1, listOf("关于我转生变成史莱姆这档事 第二季"), 12),
            SeriesMainSubject(2, listOf("关于我转生变成史莱姆这档事 第二季 第2部分"), mainEpisodeCount = 12, airedEpisodeCount = 3),
        )
        val list = entry("关于我转生变成史莱姆这档事 第二季 第2部分", 1..15)
        assertEquals(
            listOf("关于我转生变成史莱姆这档事 第二季 第2部分" to 13),
            filter(list, context(slime, subjectId = 2, sort = 37, ep = 1)).includedNumbers(),
        )
        // 只有本部分的条目此时只有 3 集
        assertEquals(
            listOf("关于我转生变成史莱姆这档事 第二季 第2部分" to 1),
            filter(entry("关于我转生变成史莱姆这档事 第二季 第2部分", 1..3), context(slime, subjectId = 2, sort = 37, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `restart entry missing its first episode is not matched by sort`() {
        val fireForce = listOf(
            SeriesMainSubject(1, listOf("炎炎消防队 叁之章", "炎炎消防队 三之章"), 12),
            SeriesMainSubject(2, listOf("炎炎消防队 叁之章 第2部分"), 13),
        )
        val list = entry("炎炎消防队 三之章 Part 2", 2..13)
        assertEquals(emptyList(), filter(list, context(fireForce, subjectId = 2, sort = 13, ep = 1)).includedNumbers())
        assertEquals(
            listOf("炎炎消防队 三之章 Part 2" to 2),
            filter(list, context(fireForce, subjectId = 2, sort = 14, ep = 2)).includedNumbers(),
        )
    }

    @Test
    fun `entry whose name is similar to the first part is a merged entry`() {
        val fafner = listOf(
            SeriesMainSubject(1, listOf("苍穹之法芙娜 EXODUS"), 13),
            SeriesMainSubject(2, listOf("苍穹之法芙娜 EXODUS 第2部分", "苍穹之法芙娜 EXODUS 后半"), 13),
        )
        // 站点写成「的」, 与第一部分的名字相似但不相同
        val merged = entry("苍穹的法芙娜 EXODUS", 1..26)
        assertEquals(listOf("苍穹的法芙娜 EXODUS" to 14), filter(merged, context(fafner, subjectId = 2, sort = 14, ep = 1)).includedNumbers())
        // 只有第一部分的条目没有第 14 集
        assertEquals(emptyList(), filter(entry("苍穹的法芙娜 EXODUS", 1..13), context(fafner, subjectId = 2, sort = 14, ep = 1)).includedNumbers())
        // 只是包含基础名的不相干条目不算
        val orient = listOf(SeriesMainSubject(1, listOf("东方少年"), 12), SeriesMainSubject(2, listOf("东方少年 第2部分"), 12))
        assertEquals(emptyList(), filter(entry("东方少年之击斗战车", 1..52), context(orient, subjectId = 2, sort = 13, ep = 1)).includedNumbers())
    }

    @Test
    fun `entry more similar to another part is excluded even though it contains the base name`() {
        val orient = listOf(
            SeriesMainSubject(1, listOf("东方少年", "オリエント"), 12),
            SeriesMainSubject(2, listOf("东方少年 第2部分", "东方少年 淡路岛激斗篇"), 12),
        )
        val list = entry("东方少年 淡路岛之战篇", 1..12) + entry("东方少年", 1..12)
        assertEquals(listOf("东方少年" to 1), filter(list, context(orient, subjectId = 1, sort = 1, ep = 1)).includedNumbers())
        assertEquals(listOf("东方少年 淡路岛之战篇" to 1), filter(list, context(orient, subjectId = 2, sort = 13, ep = 1)).includedNumbers())
    }

    @Test
    fun `season number of another subject in the series excludes the entry when the part has none`() {
        val mushoku = listOf(
            SeriesMainSubject(1, listOf("无职转生～到了异世界就拿出真本事～"), 11),
            SeriesMainSubject(2, listOf("无职转生～到了异世界就拿出真本事～ 第2部分"), 12),
            SeriesMainSubject(3, listOf("无职转生 第二季 ～到了异世界就拿出真本事～"), 13),
            SeriesMainSubject(4, listOf("无职转生 第三季 ～到了异世界就拿出真本事～"), 12),
        )
        val list = entry("无职转生：到了异世界就拿出真本事 第三季", 1..2) + entry("无职转生：到了异世界就拿出真本事", 1..11)
        assertEquals(
            listOf("无职转生：到了异世界就拿出真本事" to 1),
            filter(list, context(mushoku, subjectId = 1, sort = 1, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `part inherits the season number of the first part`() {
        // 「进击的巨人 最终季」的别名「第四季」也属于它的 Part.2: 「进击的巨人第一季」与「第四季」只差一个字, 不能当成合并条目
        val finalSeason = listOf(
            SeriesMainSubject(1, listOf("进击的巨人"), 25),
            SeriesMainSubject(2, listOf("进击的巨人 最终季", "进击的巨人 第四季"), 16),
            SeriesMainSubject(3, listOf("进击的巨人 最终季 Part.2"), 12),
        )
        val list = entry("进击的巨人第一季", 1..25) + entry("进击的巨人 最终季", 1..28)
        assertEquals(
            listOf("进击的巨人 最终季" to 17),
            filter(list, context(finalSeason, subjectId = 3, sort = 76, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `names differing only by season number are not compared by similarity`() {
        val shirayuki = listOf(
            SeriesMainSubject(1, listOf("赤发白雪姬"), 12),
            SeriesMainSubject(2, listOf("赤发白雪姬 第2部分", "赤发白雪姬 第二季"), 12),
        )
        val list = entry("赤发白雪姬第一季", 1..12) + entry("赤发白雪姬第二季", 1..12)
        assertEquals(listOf("赤发白雪姬第一季" to 1), filter(list, context(shirayuki, subjectId = 1, sort = 1, ep = 1)).includedNumbers())
        assertEquals(listOf("赤发白雪姬第二季" to 1), filter(list, context(shirayuki, subjectId = 2, sort = 13, ep = 1)).includedNumbers())
    }

    @Test
    fun `decorated base name holding only the first part is a merged entry and never falls back to ep`() {
        // 名字与基础名相似, 又与「反击篇」相似: 当作合并条目, 没有第 9 集就不选, 不能按 ep 选中袭击篇的第 1 集
        val list = entry("Re：从零开始的异世界生活 第三季 国语版", 1..8)
        assertEquals(emptyList(), filter(list, context(reZero, subjectId = 5, sort = 59, ep = 1)).includedNumbers())
        assertEquals(
            listOf("Re：从零开始的异世界生活 第三季 国语版" to 9),
            filter(entry("Re：从零开始的异世界生活 第三季 国语版", 1..16), context(reZero, subjectId = 5, sort = 59, ep = 1)).includedNumbers(),
        )
    }

    @Test
    fun `entry with another part number is excluded regardless of the own names`() {
        val orient = listOf(
            SeriesMainSubject(1, listOf("东方少年", "オリエント 第1クール"), 12),
            SeriesMainSubject(2, listOf("东方少年 第2部分"), 12),
        )
        val list = entry("东方少年 Part 2", 1..12) + entry("东方少年 前半", 1..12)
        assertEquals(listOf("东方少年 前半" to 1), filter(list, context(orient, subjectId = 1, sort = 1, ep = 1)).includedNumbers())
        assertEquals(listOf("东方少年 Part 2" to 1), filter(list, context(orient, subjectId = 2, sort = 13, ep = 1)).includedNumbers())
    }

    @Test
    fun `merged entry is not matched by a sort that restarts from 1`() {
        // Bangumi 给第二部分的 sort 也从 1 起: 第一部分的条目只有 1 集时不能把 sort=1 当成第二部分的第 1 集
        val mainline = listOf(
            SeriesMainSubject(1, listOf("桃源暗鬼 ～日光・华严之泷篇～"), 12),
            SeriesMainSubject(2, listOf("桃源暗鬼 ～日光・华严之泷篇～ 第2部分"), 12),
        )
        val list = entry("桃源暗鬼 ～日光・华严之泷篇～", 1..1)
        assertEquals(emptyList(), filter(list, context(mainline, subjectId = 2, sort = 1, ep = 1)).includedNumbers())
    }

    @Test
    fun `site alias Part 2 and 第二季 are exact matches for the second part`() {
        val result = filter(
            entry("亚人 Part 2", 1..13) + entry("亚人第二季", 1..13) + entry("亚人", 1..13),
            context(ajin, subjectId = 2, sort = 14, ep = 1),
        )
        assertEquals(listOf("亚人 Part 2" to 1, "亚人第二季" to 1), result.includedNumbers())
        assertEquals(
            MatchMetadata.SubjectMatchKind.EXACT,
            assertIs<MaybeExcludedMedia.Included>(result.single("亚人第二季", 1)).metadata.subjectMatchKind,
        )
        // 第一部分的条目只有 13 集, 不含第 14 集
        assertIs<MediaExclusionReason.EpisodeMismatch>(result.single("亚人", 1).exclusionReason)
    }

    @Test
    fun `first part excludes entries named after the second part`() {
        val result = filter(
            entry("亚人 Part 2", 1..13) + entry("亚人第二季", 1..13) + entry("亚人", 1..13),
            context(ajin, subjectId = 1, sort = 1, ep = 1),
        )
        assertEquals(listOf("亚人" to 1), result.includedNumbers())
        assertEquals(MediaExclusionReason.FromSeriesSeason, result.single("亚人第二季", 1).exclusionReason)
        assertEquals(MediaExclusionReason.FromSeriesSeason, result.single("亚人 Part 2", 1).exclusionReason)
    }

    @Test
    fun `entries with a different explicit season number are excluded`() {
        // 「第三季」与「第二季 后半部分」模糊匹配相似度很高
        val result = filter(
            entry("Re：从零开始的异世界生活 第三季", 1..16) + entry("Re：从零开始的异世界生活 第二季", 1..25),
            context(reZero, subjectId = 3, sort = 39, ep = 1),
        )
        assertEquals(listOf("Re：从零开始的异世界生活 第二季" to 14), result.includedNumbers())
        assertEquals(MediaExclusionReason.FromSeriesSeason, result.single("Re：从零开始的异世界生活 第三季", 1).exclusionReason)
    }

    @Test
    fun `series wide numbering is matched by sort`() {
        val slime = listOf(
            SeriesMainSubject(1, listOf("关于我转生变成史莱姆这档事"), 24),
            SeriesMainSubject(2, listOf("关于我转生变成史莱姆这档事 第二季"), 12),
            SeriesMainSubject(3, listOf("关于我转生变成史莱姆这档事 第二季 第2部分"), 12),
        )
        val result = filter(
            entry("关于我转生变成史莱姆这档事 第二季 Part.2", 37..48),
            context(slime, subjectId = 3, sort = 37, ep = 1),
        )
        assertEquals(listOf("关于我转生变成史莱姆这档事 第二季 Part.2" to 37), result.includedNumbers())
    }

    @Test
    fun `fuzzy own name still works for a part`() {
        val result = filter(
            entry("Re：从零开始的异世界生活 第三季 反击篇 (中字)", 1..8),
            context(reZero, subjectId = 5, sort = 59, ep = 2),
        )
        assertEquals(listOf("Re：从零开始的异世界生活 第三季 反击篇 (中字)" to 2), result.includedNumbers())
        assertEquals(
            MatchMetadata.SubjectMatchKind.FUZZY,
            assertIs<MaybeExcludedMedia.Included>(result.single("Re：从零开始的异世界生活 第三季 反击篇 (中字)", 2)).metadata.subjectMatchKind,
        )
    }

    @Test
    fun `other seasons are still excluded for a part`() {
        val result = filter(
            entry("Re：从零开始的异世界生活", 1..25),
            context(reZero, subjectId = 5, sort = 59, ep = 1),
        )
        assertEquals(emptyList(), result.includedNumbers())
        assertNotNull(result.single("Re：从零开始的异世界生活", 9).exclusionReason)
    }

    @Test
    fun `subject level candidates keep only this part of a merged entry`() {
        // 批量下载按原始集号对应剧集, 合并条目里前面部分的集不能留在条目级候选里
        val list = entry("Re：从零开始的异世界生活 第三季", 1..16) + entry("Re：从零开始的异世界生活", 1..25) +
                entry("Re：从零开始的异世界生活 第三季 反击篇", 1..8)
        val result = algorithm.filterMediaListForSubject(
            list, MediaPreference.Empty, MediaSelectorSettings.Default,
            context(reZero, subjectId = 5, sort = 59, ep = 1),
        )
        assertEquals(
            (9..16).map { "Re：从零开始的异世界生活 第三季" to it } + (1..8).map { "Re：从零开始的异世界生活 第三季 反击篇" to it },
            result.includedNumbers(),
        )
    }

    @Test
    fun `special episodes of a part use the ordinary episode rule`() {
        val context = context(reZero, subjectId = 5, sort = 59, ep = 1).let {
            it.copy(episodeInfo = it.episodeInfo!!.copy(sort = EpisodeSort("SP1"), ep = null))
        }
        val withoutPart = context.copy(subjectSeriesInfo = context.subjectSeriesInfo!!.copy(seasonPart = null))
        val list = entry("Re：从零开始的异世界生活 第三季", 1..16)
        assertEquals(filter(list, withoutPart).includedNumbers(), filter(list, context).includedNumbers())
    }
}

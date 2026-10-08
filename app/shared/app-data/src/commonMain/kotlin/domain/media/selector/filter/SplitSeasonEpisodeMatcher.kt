/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.filter

import me.him188.ani.app.data.models.subject.SplitSeason
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange

/**
 * 正在观看拆分季 ([SplitSeason]) 的后半时, 按站点页面的编号方式把当前集对到页面上的某一集.
 *
 * 站点页面指一个数据源里同一条目名、同一线路的全部资源; 同一页的不同线路可能编号不同, 所以分开看.
 * 先由页名判断页面是哪一季的、是哪一段 ([PageKind]):
 *
 * - 页名带着系列里其他季的季度编号 ("第三季", "Ⅲ"), 或者本季有编号而页名没有、也不含本季特有的篇章名
 *   (系列里有没编号的另一季时): 其他季的页面, 排除. 只靠名字相似度挡不住它们, 短的季名与其他季的页名也很相似.
 * - 页名是当前条目自己的名字, 或含有当前这一段的标记 (如 "第2部分") 而不含其他段的: 自己的页面.
 * - 页名是前半的名字、季名、含有其他段的标记, 或季度编号是本季的: 合并页或其他段的页面.
 * - 都不是, 只与本季的名字相似: 当作合并页. 按 ep 匹配会在合并页上选到前半的同号集.
 *
 * 再按页面的编号接受序号: 从大于 1 的序号开始编的页接受季内序号或 Bangumi 的 sort (站点可能按官方的总话数编号);
 * 自己的页面从 1 编时用条目内序号 ep, 但条目数明显超过本段集数时页面装的是整季, 用季内序号;
 * 其他页面只认季内序号, 且条目数要超过前面各段的总集数加上它们可能占号的特别篇和序章, 否则页面只装着前半, 或多出来的只是特别篇.
 *
 * 第一段的季内序号与 ep 相同, 不需要这里处理. 非数据源页面的资源 (BT, 本地缓存) 不处理.
 */
internal class SplitSeasonEpisodeMatcher private constructor(
    private val season: SplitSeason,
    private val ownNames: List<String>,
    private val ep: Int,
    private val sort: Int,
    private val pages: Map<PageKey, PageNumbers>,
) {
    enum class PageKind {
        /**
         * 当前条目自己的页面.
         */
        OWN,

        /**
         * 同一季其他段的页面或整季的合并页.
         */
        SEASON,

        /**
         * 页名与本季的名字无关, 只是相似.
         */
        OTHER,

        /**
         * 页名带着系列里其他季的季度编号.
         */
        OTHER_SEASON,
    }

    class Result(
        val pageKind: PageKind,
        /**
         * 页名去掉分段标记后与条目名或季名完全一致.
         */
        val exact: Boolean,
        val matched: Boolean,
        val episodeMatchKind: MatchMetadata.EpisodeMatchKind,
        /**
         * 条目名匹配规则应使用的页名: 去掉分段标记后的名字 (已做过特殊字符处理), 并把本季各段的名字和季名也算作当前条目的名字.
         * 属于其他季的页面为 `null`.
         */
        val subjectNameForMatching: String?,
    )

    private data class PageKey(val mediaSourceId: String, val subjectName: String, val channel: String?)

    private class PageNumbers(val numbers: Set<Int>) {
        val min = numbers.min()
        val count = numbers.size
    }

    private class Classification(val kind: PageKind, val exact: Boolean, val nameForMatching: String?)

    private val seasonNumber = season.seasonNumberOf(sort)
    private val self = season.self
    private val siblingIndices = season.parts.indices.filter { it != season.selfIndex }
    private val ownSeasonNumbers = self.names.flatMapTo(mutableSetOf()) { SplitSeason.seasonNumbersOf(it) }
    private val siblingSeasonNumbers = siblingIndices.flatMap { season.parts[it].names }.flatMapTo(mutableSetOf()) { SplitSeason.seasonNumbersOf(it) }

    /**
     * 本季的季度编号. 第一段的名字里没有编号的季也是第一季, 即使后半的别名叫 "2nd Season".
     */
    private val groupSeasonNumbers = buildSet {
        addAll(ownSeasonNumbers)
        addAll(siblingSeasonNumbers)
        if (season.parts.first().names.none { SplitSeason.seasonNumbersOf(it).isNotEmpty() }) add(1)
    }

    /**
     * Bangumi 名字里本季特有的分段标记, 如 "夺还篇". 含有通用标记 ("第2部分", "Part 2", "后半") 的不算, 它们不能确定页面属于哪一季.
     */
    private val specificMarkers = season.parts.flatMap { it.markers }.filter { marker ->
        val normalized = normalize(marker)
        season.parts.indices.flatMap { SplitSeason.genericMarkers(it) }.none { normalized.contains(normalize(it), ignoreCase = true) }
    }
    private val ownMarkers = (self.markers + SplitSeason.genericMarkers(season.selfIndex)).distinctBy { normalize(it) }
    private val siblingMarkers = siblingIndices
        .flatMap { season.parts[it].markers + SplitSeason.genericMarkers(it) }
        .distinctBy { normalize(it) }
        .filter { marker -> ownMarkers.none { normalize(it).equals(normalize(marker), ignoreCase = true) } }
    private val seasonExactNames = (siblingIndices.flatMap { season.parts[it].names } + season.baseNames).distinct()
    private val classifications = HashMap<String, Classification>()

    /**
     * 本季的名字: 其他段的条目名和季名. 页名属于本季时, 条目名匹配规则要把它们当作当前条目的名字.
     */
    val seasonNames: Set<String> get() = seasonExactNames.toSet()

    /**
     * 返回 `null` 表示 [media] 不是站点页面上的一集, 按通常规则匹配.
     */
    fun match(media: Media): Result? {
        if (media.kind != MediaSourceKind.WEB) return null
        val subjectName = media.properties.subjectName ?: return null
        val number = media.episodeRange?.singleIntegerNumber() ?: return null
        val page = pages[PageKey(media.mediaSourceId, subjectName, media.properties.alliance)] ?: return null
        val classification = classifications.getOrPut(subjectName) { classify(subjectName) }

        val matchedKind = when {
            classification.kind == PageKind.OTHER_SEASON -> null

            page.min > 1 -> when (number) {
                seasonNumber -> MatchMetadata.EpisodeMatchKind.SEASON
                sort -> MatchMetadata.EpisodeMatchKind.SORT
                else -> null
            }

            classification.kind == PageKind.OWN && page.count <= self.episodeCount + OWN_PAGE_EXTRA_ENTRIES -> {
                if (number == ep) MatchMetadata.EpisodeMatchKind.EP else null
            }

            // 整季合成一页时当前条目的集都排在前面各段后面, 页面至少要比前面各段加起来长.
            // 前面各段的特别篇和序章也可能占号, 页面只多出这么几个条目时分不清它们是当前条目的集还是特别篇
            page.count <= season.previousPartsEpisodeCount + season.previousPartsExtraEntryCount -> null

            else -> if (number == seasonNumber) MatchMetadata.EpisodeMatchKind.SEASON else null
        }
        return Result(
            classification.kind,
            classification.exact,
            matched = matchedKind != null,
            episodeMatchKind = matchedKind ?: MatchMetadata.EpisodeMatchKind.NONE,
            subjectNameForMatching = classification.nameForMatching,
        )
    }

    private fun classify(subjectName: String): Classification {
        val normalized = normalize(subjectName)
        if (ownNames.any { MediaListFilters.specialEquals(subjectName, it) }) return Classification(PageKind.OWN, exact = true, normalized)
        if (seasonExactNames.any { MediaListFilters.specialEquals(subjectName, it) }) {
            return Classification(PageKind.SEASON, exact = true, normalized)
        }

        // 带其他季的季度编号的页面不是本季的. "第一季" 总是存在
        val pageSeasonNumbers = SplitSeason.seasonNumbersOf(subjectName)
        val inSeason = pageSeasonNumbers.any { it in groupSeasonNumbers }
        if (!inSeason && pageSeasonNumbers.any { it in season.otherSeasonNumbers || it == 1 }) {
            return Classification(PageKind.OTHER_SEASON, exact = false, null)
        }
        // 本季有编号而页名没有, 也不含本季特有的篇章名时, 页面是系列里没编号的那一季的 (通常是第一季)
        if (pageSeasonNumbers.isEmpty() && 1 !in groupSeasonNumbers && 1 in season.otherSeasonNumbers &&
            specificMarkers.none { MediaListFilters.specialContains(subjectName, it) }
        ) {
            return Classification(PageKind.OTHER_SEASON, exact = false, null)
        }

        val containedOwnMarkers = ownMarkers.filter { MediaListFilters.specialContains(subjectName, it) }
        val containedSiblingMarkers = siblingMarkers.filter { MediaListFilters.specialContains(subjectName, it) }
        val kind = when {
            containedSiblingMarkers.isNotEmpty() -> PageKind.SEASON
            containedOwnMarkers.isNotEmpty() -> PageKind.OWN
            // 站点把后半叫 "第二季" 而 Bangumi 的别名也这么叫时, 它是当前条目自己的页面
            inSeason -> if (pageSeasonNumbers.all { it in ownSeasonNumbers && it !in siblingSeasonNumbers }) PageKind.OWN else PageKind.SEASON
            else -> return Classification(PageKind.OTHER, exact = false, normalized)
        }
        val withoutMarkers = (containedOwnMarkers + containedSiblingMarkers).fold(normalized) { acc, marker ->
            acc.replace(normalize(marker), "", ignoreCase = true)
        }
        val exact = season.baseNames.any { normalize(it).equals(withoutMarkers, ignoreCase = true) }
        return Classification(kind, exact, withoutMarkers)
    }

    companion object {
        /**
         * 自己的页面上除正片外还可能有编了号的总集篇或特别篇. 条目数超过本段集数这么多时, 页面装的是整季.
         */
        private const val OWN_PAGE_EXTRA_ENTRIES = 2

        /**
         * 正在观看拆分季的后半, 并且有足够信息时创建. [list] 用于统计各页面的集号.
         */
        fun create(context: MediaSelectorContext, list: List<Media>): SplitSeasonEpisodeMatcher? {
            val season = context.subjectSeriesInfo?.splitSeason ?: return null
            if (season.selfIndex == 0) return null
            val episodeInfo = context.episodeInfo ?: return null
            val sort = episodeInfo.sort.integerOrNull() ?: return null
            val ep = episodeInfo.ep?.integerOrNull() ?: return null
            val ownNames = context.subjectInfo?.allNames?.takeIf { names -> names.any { it.isNotBlank() } } ?: return null

            val pages = HashMap<PageKey, MutableSet<Int>>()
            for (media in list) {
                if (media.kind != MediaSourceKind.WEB) continue
                val subjectName = media.properties.subjectName ?: continue
                val number = media.episodeRange?.singleIntegerNumber() ?: continue
                pages.getOrPut(PageKey(media.mediaSourceId, subjectName, media.properties.alliance)) { HashSet() }.add(number)
            }
            if (pages.isEmpty()) return null
            return SplitSeasonEpisodeMatcher(season, ownNames, ep, sort, pages.mapValues { PageNumbers(it.value) })
        }

        private fun normalize(name: String): String =
            MediaListFilters.removeSpecials(name, removeWhitespace = true, replaceNumbers = true)

        private fun EpisodeSort.integerOrNull(): Int? {
            val number = (this as? EpisodeSort.Normal)?.number ?: return null
            if (number % 1f != 0f) return null
            return number.toInt()
        }

        private fun EpisodeRange.singleIntegerNumber(): Int? {
            val sorts = knownSorts.take(2).toList()
            return sorts.singleOrNull()?.integerOrNull()
        }
    }
}

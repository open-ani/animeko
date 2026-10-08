/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.mediasource.MediaListFilters

/**
 * Bangumi 把一季拆成的几个条目, 例如 "无职转生 第2部分", "Re:0 第四季 夺还篇".
 *
 * 站点常把这几个条目合成一页从 1 连续编号, 或者后半自己一页但接着前半编号, 此时后半各集的站内序号是季内序号,
 * 既不是条目内序号 [me.him188.ani.app.data.models.episode.EpisodeInfo.ep], 也不一定是 Bangumi 的 sort.
 * 选择器用这里的信息把站点的序号对回当前剧集, 见 [me.him188.ani.app.domain.media.selector.filter.SplitSeasonEpisodeMatcher].
 *
 * 同一季的各段在 Bangumi 上 sort 连续: 后半第一集的 sort 紧接前半最后一集. 但续集的 sort 也常常接着前作 (Re:0 全系列 1 至 85),
 * 所以识别条件是 sort 连续并且名字去掉分段标记后相同. 分段标记是 "第2部分", "Part 2", "後半クール" 这类后缀,
 * 以及 "袭击篇", "夺还篇" 这类篇章名; "第二季", "2nd season" 是季度标记, 不算分段.
 */
@Serializable
data class SplitSeason(
    /**
     * 按播出顺序排列的各段, 至少两段.
     */
    val parts: List<Part>,
    /**
     * 当前条目在 [parts] 中的下标.
     */
    val selfIndex: Int,
    /**
     * 系列里其他季的季度编号, 见 [seasonNumbersOf]. 页名带这些编号的页面属于其他季.
     * 名字里都没有编号的季是第一季, 系列里有这样的其他季时此集合包含 1.
     */
    val otherSeasonNumbers: Set<Int> = emptySet(),
) {
    @Serializable
    data class Part(
        val subjectId: Int,
        /**
         * 条目的所有名字.
         */
        val names: List<String>,
        /**
         * 名字里标识这一段的后缀, 如 "第2部分", "夺还篇". 第一段的名字通常就是季名, 没有标记.
         */
        val markers: List<String>,
        /**
         * 第一集的 sort. 序章可能是 0.
         */
        val firstSort: Int,
        /**
         * 正片集数.
         */
        val episodeCount: Int,
        /**
         * 正片中间的特别篇数: sort 在本段范围内且不是整数的特别篇, 如 86 的 11.5 集.
         * 站点可能把它们编进正片序号, 合并页上它们会把后面各段的序号挤后.
         */
        val inlineSpecialCount: Int = 0,
    )

    init {
        require(parts.size >= 2) { "A split season needs at least two parts, got $parts" }
        require(selfIndex in parts.indices) { "selfIndex $selfIndex out of range of ${parts.size} parts" }
    }

    val self: Part get() = parts[selfIndex]

    /**
     * 整季第一集的 sort.
     */
    val firstSort: Int get() = parts.first().firstSort

    /**
     * 各段名字去掉分段标记后的季名. 站点的合并页常用它做标题.
     */
    val baseNames: List<String>
        get() = parts.asSequence()
            .flatMap { it.names }
            .map { stripMarker(it).base }
            .distinct()
            .toList()

    /**
     * sort 为 [sort] 的一集在整季里的序号, 从 1 数. 站点把整季合成一页或后半接着前半编号时用的就是它.
     * sort 为 0 的序章不占号: 站点要么不给序章编号, 要么按官方把它记作第 0 集.
     */
    fun seasonNumberOf(sort: Int): Int = sort - maxOf(firstSort, 1) + 1

    /**
     * 当前条目前面各段的正片集数之和. 整季合成一页时, 当前条目的集都排在它们后面.
     */
    val previousPartsEpisodeCount: Int get() = parts.take(selfIndex).sumOf { it.episodeCount }

    /**
     * 当前条目前面各段里站点可能编进正片序号的额外条目数: 正片中间的特别篇和 sort 为 0 的序章.
     * 合并页只比前面各段的集数多出不超过这么多条目时, 分不清多出来的是当前条目的集还是这些条目.
     */
    val previousPartsExtraEntryCount: Int
        get() = parts.take(selfIndex).sumOf { it.inlineSpecialCount + if (it.firstSort == 0) 1 else 0 }

    /**
     * 名字去掉分段标记的结果.
     */
    data class StrippedName(
        val base: String,
        /**
         * 去掉的标记, 名字没有分段标记时为 `null`.
         */
        val marker: String?,
    )

    /**
     * 识别系列中某个条目的信息, 由 [compute] 使用.
     */
    data class Candidate(
        val subjectId: Int,
        val names: List<String>,
        /**
         * 正片第一集的 sort, 没有正片时为 `null`.
         */
        val firstSort: Int?,
        /**
         * 正片最后一集的 sort, 没有正片时为 `null`.
         */
        val lastSort: Int?,
        val episodeCount: Int,
        /**
         * 特别篇的 sort, 用于统计 [Part.inlineSpecialCount].
         */
        val specialSorts: List<Float> = emptyList(),
    )

    companion object {
        /**
         * 分段标记. 从名字末尾匹配.
         */
        private val PART_MARKER = Regex(
            """\s*(?:第\s*[0-9０-９一二三四五六七八九十]+\s*(?:部分|クール)|[前后後上下]半(?:部分|クール)?|[前后後上下][篇編]|part\.?\s*[0-9]+|[^\s()（）&＆]{1,4}[篇編])\s*$""",
            RegexOption.IGNORE_CASE,
        )

        fun stripMarker(name: String): StrippedName {
            val match = PART_MARKER.find(name) ?: return StrippedName(name.trim(), null)
            val base = name.substring(0, match.range.first).trim()
            // 整个名字就是标记时不算
            if (base.isEmpty()) return StrippedName(name.trim(), null)
            return StrippedName(base, match.value.trim())
        }

        private val SEASON_NUMBER_PATTERNS = listOf(
            Regex("""第\s*([0-9０-９一二三四五六七八九十]+)\s*[季期]"""),
            Regex("""(?:season|シーズン)\s*([0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9]+)(?:st|nd|rd|th)\s*season""", RegexOption.IGNORE_CASE),
            Regex("""(?<![a-z0-9])s([0-9]+)(?![a-z0-9])""", RegexOption.IGNORE_CASE),
            Regex("""([ⅡⅢⅣⅤⅥⅦⅧⅨⅩ])"""),
            Regex("""(?<![A-Za-z])(II|III|IV|V|VI|VII|VIII|IX|X)(?![A-Za-z])"""),
        )

        private val NUMBER_WORDS = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
            "Ⅱ" to 2, "Ⅲ" to 3, "Ⅳ" to 4, "Ⅴ" to 5, "Ⅵ" to 6, "Ⅶ" to 7, "Ⅷ" to 8, "Ⅸ" to 9, "Ⅹ" to 10,
            "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
        )

        /**
         * 名字里的季度编号, 如 "第二季", "2nd season", "Season 2", "Ⅱ". 没有时为空.
         * "第2部分", "第2クール" 是分段标记, 不算季度.
         */
        fun seasonNumbersOf(name: String): Set<Int> = SEASON_NUMBER_PATTERNS.flatMapTo(mutableSetOf()) { pattern ->
            pattern.findAll(name).mapNotNull { match ->
                val raw = match.groupValues[1]
                NUMBER_WORDS[raw] ?: raw.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("").toIntOrNull()
            }
        }

        /**
         * 第 [index] 段 (从 0 数) 通用的分段标记, 站点给同一段起的名字不一定与 Bangumi 一致.
         */
        fun genericMarkers(index: Int): List<String> = when (index) {
            0 -> listOf("第1部分", "Part 1", "第1クール", "前半", "上半", "上部", "前篇", "前編")
            1 -> listOf("第2部分", "Part 2", "第2クール", "后半", "後半", "下半", "下部", "后篇", "後編")
            else -> listOf("第${index + 1}部分", "Part ${index + 1}", "第${index + 1}クール")
        }

        /**
         * 从按播出顺序排列的系列主线条目 [mainline] 中找出 [selfId] 所在的拆分季.
         * 条目不是拆分季的一段时返回 `null`.
         */
        fun compute(mainline: List<Candidate>, selfId: Int): SplitSeason? {
            val candidates = mainline.filter { it.firstSort != null && it.lastSort != null && it.episodeCount > 0 }
            val selfIndexInChain = candidates.indexOfFirst { it.subjectId == selfId }
            if (selfIndexInChain == -1) return null

            var start = selfIndexInChain
            while (start > 0 && isSameSeason(candidates[start - 1], candidates[start])) start--
            var end = selfIndexInChain
            while (end < candidates.lastIndex && isSameSeason(candidates[end], candidates[end + 1])) end++
            if (start == end) return null

            val parts = candidates.subList(start, end + 1).map { candidate ->
                Part(
                    subjectId = candidate.subjectId,
                    names = candidate.names,
                    markers = candidate.names.mapNotNull { stripMarker(it).marker }.distinct(),
                    firstSort = candidate.firstSort!!,
                    episodeCount = candidate.episodeCount,
                    inlineSpecialCount = candidate.inlineSpecialCount(),
                )
            }
            val partIds = parts.map { it.subjectId }.toSet()
            val otherSeasonNumbers = mainline.asSequence()
                .filter { it.subjectId !in partIds }
                .flatMapTo(mutableSetOf()) { subject -> subject.names.flatMap { seasonNumbersOf(it) }.ifEmpty { listOf(1) } }
            return SplitSeason(parts, selfIndex = selfIndexInChain - start, otherSeasonNumbers = otherSeasonNumbers)
        }

        /**
         * 正片范围内的非整数 sort 的特别篇. 整数 sort 的特别篇 (放送前特番等) 另成一套编号, 不在正片中间.
         */
        private fun Candidate.inlineSpecialCount(): Int {
            val first = firstSort ?: return 0
            val last = lastSort ?: return 0
            return specialSorts.count { it % 1f != 0f && it > first && it < last + 1 }
        }

        /**
         * [next] 是否紧接着 [previous], 属于同一季.
         */
        private fun isSameSeason(previous: Candidate, next: Candidate): Boolean {
            if (next.firstSort != previous.lastSort!! + 1) return false
            val previousNames = previous.names.map { stripMarker(it) }
            val nextNames = next.names.map { stripMarker(it) }
            return nextNames.any { nextName ->
                previousNames.any { previousName ->
                    // 两个名字完全相同 (共用的别名) 不算, 必须有一方去掉了标记
                    (nextName.marker != null || previousName.marker != null) &&
                            MediaListFilters.specialEquals(nextName.base, previousName.base)
                }
            }
        }
    }
}

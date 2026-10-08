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

/**
 * Bangumi 把一季拆成的几个条目, 例如 "无职转生 第2部分", "Re:0 第四季 夺还篇".
 *
 * 站点常把这几个条目合成一页从 1 连续编号, 或者后半自己一页但接着前半编号, 此时后半各集的站内序号是季内序号,
 * 既不是条目内序号 [me.him188.ani.app.data.models.episode.EpisodeInfo.ep], 也不一定是 Bangumi 的 sort.
 * 选择器用这里的信息把站点的序号对回当前剧集, 见 [me.him188.ani.app.domain.media.selector.filter.SplitSeasonEpisodeMatcher].
 *
 * 由服务端从系列主线识别 (sort 连续并且名字去掉分段标记后相同的相邻条目是同一季), 随条目的系列关系
 * [SubjectRelations.splitSeason] 下发, 客户端不自己识别.
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
     * 各段名字去掉分段标记后的季名. 站点的合并页常用它做标题.
     */
    val baseNames: List<String>,
    /**
     * 系列里其他季的季度编号, 见 [seasonNumbersOf]. 页名带这些编号的页面属于其他季.
     * 名字里都没有编号的季是第一季, 系列里有这样的其他季时此集合包含 1.
     */
    val otherSeasonNumbers: List<Int> = emptyList(),
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

    companion object {
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
         * 站点页名里的季度编号, 如 "第二季", "2nd season", "Season 2", "Ⅱ". 没有时为空.
         * "第2部分", "第2クール" 是分段标记, 不算季度. 与服务端对 Bangumi 条目名用的规则相同.
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
    }
}

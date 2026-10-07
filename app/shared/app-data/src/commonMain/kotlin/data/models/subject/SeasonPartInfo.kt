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
 * 系列主线上的一个条目, 用于识别分部 ([SeasonPartInfo]).
 */
data class SeriesMainSubject(
    val subjectId: Int,
    /**
     * @see SubjectInfo.allNames
     */
    val names: List<String>,
    /**
     * 正片集数, 未知时为 0.
     */
    val mainEpisodeCount: Int,
    /**
     * 已播出的正片集数. 上映日期未知时视为全部已播出.
     */
    val airedEpisodeCount: Int = mainEpisodeCount,
)

/**
 * 当前条目是 Bangumi 拆成多个条目的一季 (分部, split-cour) 中的一部分时, 与站点条目对应所需的信息.
 *
 * Bangumi 把「X」「X 第2部分」, 「Re：0 第二季」「Re：0 第二季 后半部分」, 「Re：0 第三季 袭击篇」「Re：0 第三季 反击篇」各拆为独立条目,
 * 每个条目的 `ep` 从 1 重新计. 站点则常把各部分合并进同一个条目连续编号, 或者用「Part 2」「第二季」「后半」等 Bangumi 别名里没有的叫法单独建条目.
 * 本类由 [compute] 从主线条目的名字与正片集数推导, 资源的匹配规则见 `SeasonPartMatcher`.
 */
@Serializable
data class SeasonPartInfo(
    /**
     * 本条目是第几部分, 从 0 开始.
     */
    val partIndex: Int,
    val partCount: Int,
    /**
     * 前面各部分的正片集数之和. 站点把各部分合并进同一条目时, 本条目的第 `ep` 集在该条目里是第 `episodeOffset + ep` 集.
     */
    val episodeOffset: Int,
    /**
     * 本部分的正片集数.
     */
    val episodeCount: Int,
    /**
     * 本部分已播出的正片集数. 站点条目的集数与它比较才能区分「只有本部分的条目」和「还在更新的合并条目」, 与总集数比较分不开.
     */
    val airedEpisodeCount: Int = episodeCount,
    /**
     * 站点把各部分合并进同一个条目时, 该条目会用的名字: 前面各部分的全部名字, 以及去掉分部标记后的基础名.
     * 第一部分为空, 它自己的名字就是基础名.
     */
    val mergedEntryNames: Set<String>,
    /**
     * 站点给本部分起的、Bangumi 没有收录的叫法, 由基础名与分部序号合成, 例如「X Part.2」「X 第2部分」「X 后半」;
     * 第一部分则是基础名本身与「X Part.1」「X 前半」等.
     *
     * 以精确比较为主, 分部序号与季度序号都相同时才比相似度: 「第三季 Part.2」与「第二季 Part.2」只差一个字, 相似度分不开.
     */
    val ownSynthesizedNames: Set<String>,
    /**
     * 站点给其他部分起的叫法 ([ownSynthesizedNames] 的其他部分版本). 资源的条目名与之相同, 或比与本部分的名字更相似时排除.
     */
    val otherPartSynthesizedNames: Set<String>,
    /**
     * 本部分所属季度的序号: 本部分名字里标出的, 没有则取第一部分名字里标出的 (「进击的巨人 最终季」的别名「第四季」也属于它的 Part.2), 都没有为 `null`.
     * 站点条目标了不同的季度序号时排除.
     */
    val ownSeasonNumber: Int?,
    /**
     * 后面各部分的全部名字. 它们都以本部分的名字开头 (「东方少年」与「东方少年 淡路岛激斗篇」), 站点给后面部分起的名字能通过本部分的模糊匹配,
     * 与它们更相似的条目按后面的部分算.
     */
    val laterPartNames: Set<String> = emptySet(),
) {
    companion object {
        /**
         * 识别 [subjectId] 在主线 [mainline] 上所属的分部组, 不属于任何分部组时返回 `null`.
         *
         * 分部组是主线上连续的几个条目, 后面的名字是第一个的名字加分部标记 (第2部分 / Part 2 / 后半 / 第2クール),
         * 或者两者带相同的季度序号且序号之前的部分相同 (第三季 袭击篇 / 第三季 反击篇).
         * 组内任一部分的正片集数未知时放弃, 否则算不出偏移.
         */
        fun compute(mainline: List<SeriesMainSubject>, subjectId: Int): SeasonPartInfo? {
            val group = findGroup(mainline, subjectId) ?: return null
            if (group.any { it.mainEpisodeCount <= 0 }) return null
            val partIndex = group.indexOfFirst { it.subjectId == subjectId }
            val own = group[partIndex]
            val groupIds = group.mapTo(HashSet()) { it.subjectId }

            val base = SeasonPartNames.seasonBase(group[0].primaryName, group[1].primaryName)
            val seasonsOutsideGroup = mainline.asSequence()
                .filter { it.subjectId !in groupIds }
                .flatMap { it.names }
                .mapNotNull { SeasonPartNames.seasonNumber(it) }
                .toHashSet()
            val siblingNames = mainline.asSequence()
                .filter { it.subjectId != subjectId }
                .flatMap { it.names }
                .filter { sibling -> own.names.none { MediaListFilters.specialEquals(it, sibling) } }
                .toList()

            // 基础名: 组的基础名, 以及第一部分与本部分带分部标记的名字去掉标记. 不带标记的名字 (「袭击篇」「反击篇」) 不是基础名.
            fun basesOf(part: Int): List<String> = buildList {
                add(base)
                (group[0].names + group[part].names)
                    .filter { SeasonPartNames.hasPartMarker(it) }
                    .mapTo(this) { SeasonPartNames.stripPartMarker(it) }
            }.filter { it.isNotBlank() }.distinct()

            fun synthesizedOf(part: Int): List<String> =
                SeasonPartNames.synthesizeNames(basesOf(part), part, group.size, seasonsOutsideGroup)

            val mergedEntryNames = if (partIndex == 0) emptySet() else buildSet {
                for (i in 0 until partIndex) addAll(group[i].names)
                addAll(basesOf(0))
                removeAll { name -> own.names.any { MediaListFilters.specialEquals(it, name) } }
            }
            // 第一部分的基础名就是站点合并条目的名字 (「Re：0 第三季」之于「Re：0 第三季 袭击篇」), 对它而言是精确匹配
            val ownSynthesizedNames = (synthesizedOf(partIndex) + if (partIndex == 0) basesOf(0) else emptyList())
                .filter { name -> siblingNames.none { MediaListFilters.specialEquals(it, name) } }
                .toSet()
            val otherPartSynthesizedNames = buildSet {
                for (i in group.indices) {
                    if (i != partIndex) addAll(synthesizedOf(i))
                }
                removeAll { name -> own.names.any { MediaListFilters.specialEquals(it, name) } }
            }
            return SeasonPartInfo(
                partIndex = partIndex,
                partCount = group.size,
                episodeOffset = group.take(partIndex).sumOf { it.mainEpisodeCount },
                episodeCount = own.mainEpisodeCount,
                airedEpisodeCount = own.airedEpisodeCount.coerceIn(0, own.mainEpisodeCount),
                mergedEntryNames = mergedEntryNames,
                ownSynthesizedNames = ownSynthesizedNames,
                otherPartSynthesizedNames = otherPartSynthesizedNames,
                ownSeasonNumber = own.names.firstNotNullOfOrNull { SeasonPartNames.seasonNumber(it) }
                    ?: group[0].names.firstNotNullOfOrNull { SeasonPartNames.seasonNumber(it) },
                laterPartNames = buildSet {
                    for (i in partIndex + 1 until group.size) addAll(group[i].names)
                    removeAll { name -> own.names.any { MediaListFilters.specialEquals(it, name) } }
                },
            )
        }

        private fun findGroup(mainline: List<SeriesMainSubject>, subjectId: Int): List<SeriesMainSubject>? {
            var i = 0
            while (i < mainline.size) {
                val first = mainline[i]
                var j = i + 1
                while (j < mainline.size && SeasonPartNames.isSamePartGroup(first.primaryName, mainline[j].primaryName)) {
                    j++
                }
                val group = mainline.subList(i, j)
                if (group.size > 1 && group.any { it.subjectId == subjectId }) return group
                i = j
            }
            return null
        }

        private val SeriesMainSubject.primaryName: String get() = names.firstOrNull().orEmpty()
    }
}

/**
 * 条目名里的季度与分部标记.
 */
internal object SeasonPartNames {
    private const val CHINESE_NUMERALS = "一二三四五六七八九十"

    /**
     * 「第二季」「第2期」. 分部组识别与季度序号比较都只认这种显式写法, 不含「2」这样的裸数字.
     */
    private val SEASON_MARKER = Regex("""第\s*([一二三四五六七八九十\d]+)\s*[季期]""")

    private val ENGLISH_SEASON = Regex("""(\d+)(?:st|nd|rd|th)\s*season|season\s*(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * 作为季度序号的罗马数字: 前面不是字母, 后面是空白或结尾, 免得把 "MIX" "Vivy" 里的字母当成序号.
     */
    private val ROMAN_SEASON = Regex("""[Ⅱ-Ⅸ]|(?<=[^A-Za-z])(?:II|III|IV|V|VI)(?=\s|$)""")

    /**
     * 站点用罗马数字表示季度 (「无职转生Ⅱ」) 时, 换成阿拉伯数字, 与季度简化后的兄弟条目名比较.
     */
    private val ROMAN_NUMERAL = Regex("""[ⅡⅢⅣⅤⅥ]|(?<![A-Za-z])(?:II|III|IV|V|VI)(?![A-Za-z])""")

    private val ROMAN_VALUES = mapOf(
        "Ⅱ" to 2, "Ⅲ" to 3, "Ⅳ" to 4, "Ⅴ" to 5, "Ⅵ" to 6, "Ⅶ" to 7, "Ⅷ" to 8, "Ⅸ" to 9,
        "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6,
    )

    /**
     * 名字末尾的分部标记. 封闭列表, 只收站点与 Bangumi 实际用过的写法; 放宽会把别的续作认成分部.
     */
    private val PART_MARKER = Regex(
        """(?:第\s*[一二三四五六七八九十\d]+\s*部分|[Pp][Aa][Rr][Tt]\.?\s*\d|[前后後上下]半(?:部分)?|第\s*[一二三四五六七八九十\d]+\s*(?:クール|cour)|\d+(?:nd|rd|th)\s*[Cc]our)\s*$""",
    )

    private const val PART_MARKER_TRAILING_CHARS = " 　:：-－・"

    /**
     * 把「Re：从零开始的休息时间 第2季」简化为「Re：从零开始的休息时间 2」. 条目名可能是后者的形式, 而站点给的是前者.
     */
    val SEASON_TAILING = Regex("""第\s*(?<season>.+)\s*[部季]""")

    fun seasonSimplified(name: String): String = name.replace(SEASON_TAILING, $$"${season}")

    /**
     * 去掉季度标记, 用于判断两个名字是否只差季度: 「无职转生：到了异世界就拿出真本事 第三季」与「无职转生 第三季 ～到了异世界就拿出真本事～」.
     */
    fun withoutSeasonMarker(name: String): String = name.replace(SEASON_MARKER, " ").replace(ENGLISH_SEASON, " ")

    fun romanNumeralsToDigits(name: String): String =
        ROMAN_NUMERAL.replace(name) { ROMAN_VALUES.getValue(it.value).toString() }

    fun seasonNumber(name: String): Int? {
        SEASON_MARKER.find(name)?.let { return parseNumeral(it.groupValues[1]) }
        ENGLISH_SEASON.find(name)?.let { m ->
            return (m.groups[1] ?: m.groups[2])?.value?.toIntOrNull()
        }
        ROMAN_SEASON.find(name)?.let { return ROMAN_VALUES[it.value] }
        return null
    }

    fun hasPartMarker(name: String): Boolean = PART_MARKER.containsMatchIn(name)

    /**
     * 分部标记表示的是第几部分: 「第2部分」「Part 2」「第2クール」为 2, 「前半」「上半」为 1, 「后半」「下半」为 2. 没有标记时为 `null`.
     */
    fun partNumber(name: String): Int? {
        val marker = PART_MARKER.find(name)?.value ?: return null
        if (marker.any { it in "前上" }) return 1
        if (marker.any { it in "后後下" }) return 2
        return Regex("""[一二三四五六七八九十\d]+""").find(marker)?.let { parseNumeral(it.value) }
    }

    fun stripPartMarker(name: String): String =
        PART_MARKER.replace(name, "").trim { it in PART_MARKER_TRAILING_CHARS }

    /**
     * [second] 紧跟在 [first] 之后时, 两者是否属于同一个分部组. 组里后面的部分都与第一个部分比较.
     */
    fun isSamePartGroup(first: String, second: String): Boolean {
        if (hasPartMarker(second) && stripPartMarker(second) == stripPartMarker(first)) return true
        val seasonFirst = seasonNumber(first) ?: return false
        if (seasonFirst != seasonNumber(second) || stripPartMarker(first) == stripPartMarker(second)) return false
        return textBeforeSeasonMarker(first) == textBeforeSeasonMarker(second)
    }

    /**
     * 分部组的基础名: 第二部分带分部标记时取去掉标记的名字, 两者带相同季度标记时取到季度标记为止的前缀, 否则取第一部分去掉标记的名字.
     */
    fun seasonBase(first: String, second: String): String {
        if (hasPartMarker(second)) return stripPartMarker(second)
        val m = SEASON_MARKER.find(first)
        if (m != null && SEASON_MARKER.find(second) != null) {
            val prefix = first.substring(0, m.range.last + 1)
            if (second.startsWith(prefix)) return prefix.trim()
        }
        return stripPartMarker(first)
    }

    /**
     * 第 [partIndex] 部分的合成别名. 「第 N 季」只在系列里其他条目确实没有第 N 季时合成 ([seasonsOutsideGroup]),
     * 否则站点的「X 第二季」是真正的第二季, 不是第二部分; 判断时不看组内条目自己的别名, Bangumi 常给第二部分标「2nd Season」.
     */
    fun synthesizeNames(bases: List<String>, partIndex: Int, partCount: Int, seasonsOutsideGroup: Set<Int>): List<String> {
        val n = partIndex + 1
        val chinese = CHINESE_NUMERALS.getOrNull(partIndex)?.toString() ?: n.toString()
        return buildList {
            for (base in bases) {
                add("$base Part.$n")
                add("$base 第${n}部分")
                add("$base 第${chinese}部分")
                if (partIndex > 0 && seasonNumber(base) == null && n !in seasonsOutsideGroup) {
                    add("$base 第${chinese}季")
                    add("$base 第${n}季")
                }
                if (partCount == 2) {
                    if (partIndex == 0) {
                        add("$base 前半")
                        add("$base 前半部分")
                        add("$base 上半")
                    } else {
                        add("$base 后半")
                        add("$base 后半部分")
                        add("$base 后篇")
                        add("$base 下半")
                    }
                }
            }
        }.distinct()
    }

    private fun parseNumeral(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        if (text.length == 1) {
            val index = CHINESE_NUMERALS.indexOf(text[0])
            if (index >= 0) return index + 1
        }
        return null
    }

    private fun textBeforeSeasonMarker(name: String): String =
        SEASON_MARKER.find(name)?.let { name.substring(0, it.range.first) } ?: name
}

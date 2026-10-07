/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.filter

import me.him188.ani.app.data.models.subject.SeasonPartInfo
import me.him188.ani.app.data.models.subject.SeasonPartNames
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.StringMatcher
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.EpisodeRange

/**
 * 分部条目 ([SeasonPartInfo]) 的 WEB 资源匹配: 按条目名把资源归类 ([classify]), 再在它所在的站点条目里找出对应当前剧集的集号 ([targetNumber]).
 *
 * 名字规则 (按顺序):
 * - 与前面部分或基础名相同的是合并条目; 与本条目名相同, 或与合成别名相同的是本部分; 与系列其他条目相同的排除.
 *   相同指 [MediaListFilters.specialEquals], 站点用罗马数字表示季度 (「无职转生Ⅱ」) 时与季度简化后的名字比较.
 * - 季度序号或分部序号与本部分不同的排除. 本部分没有季度序号而站点条目有时, 去掉季度标记后与系列里带该序号的条目同名的也排除.
 * - 后面部分的名字都以本部分的名字开头, 站点给后面部分起的名字 (「东方少年 淡路岛之战篇」) 能通过本部分的模糊匹配,
 *   所以与后面的部分更相似的按后面的部分算, 排除. 只与后面的部分比: 合并条目本来就与前面的部分相似, 其他季度的名字也彼此相似.
 * - 剩下的按相似度, 且季度序号不同的名字不比 (「第一季」与「第四季」只差一个字): 与合成别名相似的是本部分, 但合成别名之间只差分部或季度序号, 只认两者都相同的
 *   (「石纪元 第三季 Part 2」与「新石纪 第三季 Part.2」, 不认「第三季 Part.2」与「第二季 Part.2」);
 *   与前面部分的名字相似且不比本条目名更像本部分的是合并条目 (「苍穹的法芙娜 EXODUS」, 「Re：0 第三季 国语版」);
 *   其余按本条目名模糊匹配 ([MediaListFilters.ContainsSubjectName]). 合并条目不认包含关系: 基础名短, 「东方少年」被不相干的「东方少年之击斗战车」包含.
 *
 * 集号规则: 站点条目指同一数据源里同一条目名同一线路的全部资源, 它的集号集合反映站点的编号方式, 同一站点条目只取一集.
 * - 合并条目 (各部分连续编号): 取偏移后的集号 `episodeOffset + ep`, 且必须落在本部分的区间内; 站点按全系列编号时取 `sort`, 它得在前面部分的集数之外.
 * - 以本部分命名的条目: 从 1 起编号的 (集号覆盖了开头的大半, 缺第 1 集也算), 集数够得上「前面部分 + 本部分已播出的集数」时
 *   是站点用本部分的叫法合并了多个部分 (「第二季 第2部分」装着整季 24 集), 按合并条目处理; 否则是只有本部分的条目, 按 `ep` 取.
 *   与已播出集数而非总集数比较: 只有本部分的条目常多塞一集总集篇, 还在更新的合并条目则不到总集数.
 *   不从 1 起编号的先按 `sort` (站点按全系列编号), 再按偏移后的集号 (站点接着上一部分编号).
 *   不能总是先按 `sort`: 第二部分比第一部分长时, 从 1 起编号的条目里 `sort` 与 `ep` 各对上一集.
 */
internal class SeasonPartMatcher(
    private val part: SeasonPartInfo,
    ownNames: List<String>,
    private val sequelNames: Set<String>,
    siblingNames: Set<String>,
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
    /**
     * 条目名是否与本条目名模糊匹配, 即 [MediaListFilters.ContainsSubjectName].
     */
    private val fuzzyMatches: (mediaSubjectName: String) -> Boolean,
) {
    private val sort: Float? = (episodeSort as? EpisodeSort.Normal)?.number
    private val ep: Float? = (episodeEp as? EpisodeSort.Normal)?.number ?: sort
    private val offsetEp: Float? = ep?.let { it + part.episodeOffset }

    private val own = NameSet(ownNames)
    private val merged = NameSet(part.mergedEntryNames)
    private val siblings = NameSet(siblingNames)
    private val ownSynthesized = NameSet(part.ownSynthesizedNames)
    private val otherPartSynthesized = NameSet(part.otherPartSynthesizedNames)
    private val laterParts = NameSet(part.laterPartNames)

    /**
     * 一组名字及其比较用的形式, 每次过滤创建一次; 列表里每个资源都要与它们比较, 预先处理这一侧, 免得每个资源都重复执行正则.
     */
    private class NameSet(names: Collection<String>) {
        val names: List<String> = names.toList()
        val normalized: List<String> = this.names.map { MediaListFilters.normalizeForCompare(it) }
        val seasonSimplifiedNormalized: List<String> =
            this.names.map { MediaListFilters.normalizeForCompare(SeasonPartNames.seasonSimplified(it)) }
        val lowercase: List<String> = normalized.map { it.lowercase() }
        val seasonNumbers: List<Int?> = this.names.map { SeasonPartNames.seasonNumber(it) }
        val partNumbers: List<Int?> = this.names.map { SeasonPartNames.partNumber(it) }
        val withoutSeasonMarkerNormalized: List<String> =
            this.names.map { MediaListFilters.normalizeForCompare(SeasonPartNames.withoutSeasonMarker(it)) }

        fun firstSameOrNull(media: MediaName): String? {
            for (i in names.indices) {
                if (normalized[i].equals(media.normalized, ignoreCase = true) ||
                    normalized[i].equals(media.seasonSimplifiedNormalized, ignoreCase = true) ||
                    seasonSimplifiedNormalized[i].equals(media.romanAsDigitsNormalized, ignoreCase = true)
                ) return names[i]
            }
            return null
        }

        fun anySame(media: MediaName): Boolean = firstSameOrNull(media) != null

        fun bestSimilarity(media: MediaName, eligible: (index: Int) -> Boolean = { true }): Int =
            lowercase.indices.filter(eligible).maxOfOrNull { StringMatcher.calculateMatchRate(media.lowercase, lowercase[it]) } ?: 0
    }

    private class MediaName(val name: String) {
        val normalized: String = MediaListFilters.normalizeForCompare(name)
        val seasonSimplifiedNormalized: String = MediaListFilters.normalizeForCompare(SeasonPartNames.seasonSimplified(name))
        val romanAsDigitsNormalized: String = MediaListFilters.normalizeForCompare(SeasonPartNames.romanNumeralsToDigits(name))
        val lowercase: String = normalized.lowercase()
        val seasonNumber: Int? = SeasonPartNames.seasonNumber(name)
        val partNumber: Int? = SeasonPartNames.partNumber(name)
    }

    sealed class Verdict {
        /**
         * 站点把各部分合并进同一个条目, 或者条目以前面的部分命名.
         */
        data object Merged : Verdict()

        /**
         * 本部分的条目. [exact] 为 `true` 表示条目名与本条目的名字或合成别名相同.
         */
        data class Own(val exact: Boolean) : Verdict()

        data class Excluded(val reason: MediaExclusionReason) : Verdict()
    }

    fun classify(mediaSubjectName: String): Verdict {
        val media = MediaName(mediaSubjectName)
        if (part.partIndex > 0 && merged.anySame(media)) return Verdict.Merged
        if (own.anySame(media)) return Verdict.Own(exact = true)
        siblings.firstSameOrNull(media)?.let { sibling ->
            return Verdict.Excluded(
                if (sibling in sequelNames) MediaExclusionReason.FromSequelSeason else MediaExclusionReason.FromSeriesSeason,
            )
        }
        if (ownSynthesized.anySame(media)) return Verdict.Own(exact = true)

        // 明确标了不同季度序号的名字相似度很高 (「第二季」与「第三季」只差一个字), 不能交给模糊匹配; 分部序号同理
        val ownSeason = part.ownSeasonNumber
        if (ownSeason != null && media.seasonNumber != null && media.seasonNumber != ownSeason) {
            return Verdict.Excluded(MediaExclusionReason.FromSeriesSeason)
        }
        if (media.partNumber != null && media.partNumber != part.partIndex + 1) {
            return Verdict.Excluded(MediaExclusionReason.FromSeriesSeason)
        }
        if (otherPartSynthesized.anySame(media)) return Verdict.Excluded(MediaExclusionReason.FromSeriesSeason)
        if (ownSeason == null && media.seasonNumber != null) {
            val mediaWithoutSeason = MediaListFilters.normalizeForCompare(SeasonPartNames.withoutSeasonMarker(mediaSubjectName))
            val isOtherSeason = siblings.names.indices.any { i ->
                siblings.seasonNumbers[i] == media.seasonNumber &&
                        siblings.withoutSeasonMarkerNormalized[i].equals(mediaWithoutSeason, ignoreCase = true)
            }
            if (isOtherSeason) return Verdict.Excluded(MediaExclusionReason.FromSeriesSeason)
        }

        val ownSimilarity = maxOf(own.bestSimilarity(media), ownSynthesized.bestSimilarity(media))
        val laterPartSimilarity = maxOf(
            laterParts.bestSimilarity(media) { seasonCompatible(media, laterParts, it) },
            otherPartSynthesized.bestSimilarity(media) { comparableSynthesized(media, otherPartSynthesized, it) },
        )
        if (laterPartSimilarity >= SIMILAR_NAME_RATE && laterPartSimilarity > ownSimilarity) {
            return Verdict.Excluded(MediaExclusionReason.FromSeriesSeason)
        }

        val synthesizedSimilarity = ownSynthesized.bestSimilarity(media) { comparableSynthesized(media, ownSynthesized, it) }
        if (synthesizedSimilarity >= SIMILAR_NAME_RATE) return Verdict.Own(exact = false)
        if (part.partIndex > 0) {
            val mergedSimilarity = merged.bestSimilarity(media) { seasonCompatible(media, merged, it) }
            if (mergedSimilarity >= SIMILAR_NAME_RATE && mergedSimilarity >= own.bestSimilarity(media)) return Verdict.Merged
        }
        if (fuzzyMatches(mediaSubjectName)) return Verdict.Own(exact = false)
        return Verdict.Excluded(MediaExclusionReason.SubjectNameMismatch)
    }

    /**
     * 两个名字的季度序号不冲突, 才比相似度: 「第一季」与「第四季」只差一个字.
     */
    private fun seasonCompatible(media: MediaName, set: NameSet, index: Int): Boolean =
        media.seasonNumber == null || set.seasonNumbers[index] == null || set.seasonNumbers[index] == media.seasonNumber

    /**
     * 合成别名能否与资源名比相似度: 两者都带分部标记且分部序号与季度序号相同.
     */
    private fun comparableSynthesized(media: MediaName, set: NameSet, index: Int): Boolean =
        media.partNumber != null && set.partNumbers[index] == media.partNumber && set.seasonNumbers[index] == media.seasonNumber

    /**
     * 集号集合为 [entryNumbers] 的站点条目里对应当前剧集的集号, 没有则为 `null`.
     */
    fun targetNumber(verdict: Verdict, entryNumbers: Set<Float>): Float? {
        if (entryNumbers.isEmpty()) return null
        return when (verdict) {
            is Verdict.Excluded -> null
            Verdict.Merged -> mergedTarget(entryNumbers)
            is Verdict.Own -> when {
                startsFromOne(entryNumbers) -> if (holdsEarlierParts(entryNumbers)) {
                    mergedTarget(entryNumbers)
                } else {
                    ep?.takeIf { it in entryNumbers }
                }

                sort != null && sort in entryNumbers -> sort
                offsetEp != null && offsetEp in entryNumbers -> offsetEp
                else -> ep?.takeIf { it in entryNumbers }
            }
        }
    }

    /**
     * 不按集裁剪的条目级候选里, 集号为 [range] 的资源是否属于本部分: 合并条目 (以及装着多个部分的本部分条目) 只有本部分区间内的集算.
     */
    fun belongsToPart(verdict: Verdict, entryNumbers: Set<Float>, range: EpisodeRange?): Boolean {
        val isMerged = verdict == Verdict.Merged ||
                (verdict is Verdict.Own && entryNumbers.isNotEmpty() && startsFromOne(entryNumbers) && holdsEarlierParts(entryNumbers))
        if (!isMerged) return true
        val number = (range?.knownSorts?.singleOrNull() as? EpisodeSort.Normal)?.number ?: return false
        return number > part.episodeOffset && number <= part.episodeOffset + part.episodeCount
    }

    /**
     * 集号是否从 1 起编号: 开头 (到本部分集数或条目最大集号为止) 的集号至少有一半在条目里. 缺第 1 集的条目也算, 只认最小集号会把它当成接着编号.
     */
    private fun startsFromOne(entryNumbers: Set<Float>): Boolean {
        val end = minOf(part.episodeCount, entryNumbers.max().toInt()).coerceAtLeast(1)
        val present = (1..end).count { it.toFloat() in entryNumbers }
        return present * 2 >= end
    }

    /**
     * 从 1 起编号的条目集数够不够得上前面各部分加本部分已播出的集数, 够则装着多个部分.
     */
    private fun holdsEarlierParts(entryNumbers: Set<Float>): Boolean =
        entryNumbers.max() >= part.episodeOffset + part.airedEpisodeCount - 1

    private fun mergedTarget(entryNumbers: Set<Float>): Float? {
        val offset = part.episodeOffset
        return when {
            offsetEp != null && offsetEp > offset && offsetEp <= offset + part.episodeCount && offsetEp in entryNumbers -> offsetEp
            sort != null && sort > offset && sort in entryNumbers -> sort
            else -> null
        }
    }

    /**
     * 选中的集号 [number] 对应的剧集匹配等级: 等于 `sort` 的是 [MatchMetadata.EpisodeMatchKind.SORT], 按 `ep` 或偏移对上的是 [MatchMetadata.EpisodeMatchKind.EP].
     */
    fun episodeMatchKind(number: Float): MatchMetadata.EpisodeMatchKind =
        if (number == sort) MatchMetadata.EpisodeMatchKind.SORT else MatchMetadata.EpisodeMatchKind.EP

    companion object {
        /**
         * 名字算相似的相似度下限, 与 [MediaListFilters.ContainsSubjectName] 的模糊匹配一致.
         */
        private const val SIMILAR_NAME_RATE = 80

        /**
         * 同一站点条目的 key: 数据源, 条目名, 线路.
         */
        fun entryKey(media: Media): Triple<String, String?, String> =
            Triple(media.mediaSourceId, media.properties.subjectName, media.properties.alliance)

        /**
         * 每个站点条目的集号集合. 只统计能解析出普通集号的资源.
         */
        fun collectEntryNumbers(list: List<Media>): Map<Triple<String, String?, String>, Set<Float>> {
            val result = HashMap<Triple<String, String?, String>, MutableSet<Float>>()
            for (media in list) {
                val range = media.episodeRange ?: continue
                val numbers = result.getOrPut(entryKey(media)) { HashSet() }
                range.knownSorts.forEach { if (it is EpisodeSort.Normal) numbers.add(it.number) }
            }
            return result
        }

        fun EpisodeRange.containsNumber(number: Float): Boolean =
            this is EpisodeRange.Season || knownSorts.any { it is EpisodeSort.Normal && it.number == number }
    }
}

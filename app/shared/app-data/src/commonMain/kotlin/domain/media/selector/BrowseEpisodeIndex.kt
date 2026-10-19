/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.subject.SplitSeason
import me.him188.ani.app.domain.media.selector.filter.SplitSeasonPageMatcher
import me.him188.ani.app.domain.media.selector.filter.integerOrNull
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode

/**
 * 线路里第 [pickedIndex] 项被当作第 [pickedAs] 集时, 第 [target] 集按位置对应的下标 k + (t − p).
 * 编号类型不同 (正片与 SP) 或差不是整数时算不出, 返回 null. 不检查下标是否越界.
 * 浏览记忆回放与下载弹窗的手动查找共用这条规则.
 */
fun browseEpisodeIndex(pickedIndex: Int, pickedAs: EpisodeSort, target: EpisodeSort): Int? {
    val sameSeries = when (pickedAs) {
        is EpisodeSort.Normal -> target is EpisodeSort.Normal
        is EpisodeSort.Special -> target is EpisodeSort.Special && pickedAs.type == target.type
        is EpisodeSort.Unknown -> false
    }
    if (!sameSeries) return null
    val offset = (target.number ?: return null) - (pickedAs.number ?: return null)
    if (offset % 1f != 0f) return null
    return pickedIndex + offset.toInt()
}

/**
 * 手动查找打开一条线路时默认预选的一项: [episodes] 里对应当前集 (sort 为 [sort], ep 为 [ep]) 的下标, 没有为 null.
 *
 * 正在观看拆分季 [splitSeason] 的后半时按与自动选择相同的页面规则 ([SplitSeasonPageMatcher]) 用页名 [pageName]
 * 和线路的编号找当前集: 整季合成一页时是季内序号那一项, 后半自己从 1 编的页是 ep 那一项. [subjectNames] 是当前条目的所有名字, 用来认出自己的页面.
 * 规则判定页面只装着前半、或多出来的只是特别篇时不预选: 预选错的一项比不预选更误导.
 * 其他季的页面规则不管 (自动选择直接排除), 只在它编号与 sort 相同时预选: 全系列连续编号的页面上那一项就是当前集.
 *
 * 其余情况先取集号等于 sort 的项, 再取等于 ep 的项: 站点接着上一季编号时前者对, 每季从 1 编时后者对,
 * 先 sort 后 ep 与自动选择对模糊匹配的页面的顺序相同, 先 ep 会在合并页上落到前半的同号集.
 */
fun preselectedBrowseEpisodeIndex(
    pageName: String,
    episodes: List<BrowseEpisode>,
    sort: EpisodeSort,
    ep: EpisodeSort?,
    splitSeason: SplitSeason?,
    subjectNames: List<String>,
): Int? {
    val matcher = splitSeason?.let { SplitSeasonPageMatcher.create(it, subjectNames, sort, ep) }
    if (matcher != null) {
        val classification = matcher.classify(pageName)
        if (classification.kind != SplitSeasonPageMatcher.PageKind.OTHER_SEASON) {
            val numbers = episodes.mapNotNullTo(HashSet()) { it.episodeSort?.integerOrNull() }
            if (numbers.isEmpty()) return null
            val page = SplitSeasonPageMatcher.PageNumbers(numbers)
            return episodes.indexOfFirst { episode ->
                val number = episode.episodeSort?.integerOrNull() ?: return@indexOfFirst false
                matcher.matchKind(classification, page, number) != null
            }.takeIf { it >= 0 }
        }
        return episodes.indexOfSort(sort)
    }
    return episodes.indexOfSort(sort) ?: ep?.let { episodes.indexOfSort(it) }
}

private fun List<BrowseEpisode>.indexOfSort(sort: EpisodeSort): Int? =
    indexOfFirst { it.episodeSort != null && it.episodeSort == sort }.takeIf { it >= 0 }

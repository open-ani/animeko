/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.app.data.models.subject.SplitSeason
import me.him188.ani.app.domain.media.selector.filter.SplitSeasonPageMatcher
import me.him188.ani.app.domain.media.selector.filter.SplitSeasonPageMatcher.PageKind
import me.him188.ani.danmaku.api.provider.DanmakuSplitSeasonMatcher
import me.him188.ani.datasources.api.EpisodeSort

/**
 * 弹幕源的一部番剧当作一个站点页面, 按资源选择器的拆分季页面规则 ([SplitSeasonPageMatcher]) 对集.
 *
 * 弹幕源关联到当前条目的番剧名字认不出时 (只与本季的名字相似), 当作当前条目自己的页面, 从 1 编号时认 ep;
 * 站点页面没有这种关联, 只能当作合并页.
 */
class SplitSeasonDanmakuMatcher private constructor(
    override val previousPartSubjectIds: List<Int>,
    private val pageMatcher: SplitSeasonPageMatcher,
) : DanmakuSplitSeasonMatcher {
    override fun isOtherSeason(subjectName: String): Boolean =
        pageMatcher.classify(subjectName).kind == PageKind.OTHER_SEASON

    override fun isCurrentEpisode(subjectName: String, numbers: Set<Int>, number: Int, mappedToSelf: Boolean): Boolean {
        if (numbers.isEmpty()) return false
        val classification = pageMatcher.classify(subjectName).let {
            if (mappedToSelf && it.kind == PageKind.OTHER) {
                SplitSeasonPageMatcher.Classification(PageKind.OWN, exact = false, it.nameForMatching)
            } else {
                it
            }
        }
        return pageMatcher.matchKind(classification, SplitSeasonPageMatcher.PageNumbers(numbers), number) != null
    }

    companion object {
        /**
         * 正在观看拆分季 [season] 的后半并且有足够信息时创建, 条件同 [SplitSeasonPageMatcher.create].
         *
         * @param subjectNames 当前条目的所有名字.
         */
        fun create(
            season: SplitSeason?,
            subjectNames: List<String>,
            sort: EpisodeSort,
            ep: EpisodeSort?,
        ): SplitSeasonDanmakuMatcher? {
            if (season == null) return null
            val pageMatcher = SplitSeasonPageMatcher.create(season, subjectNames, sort, ep) ?: return null
            return SplitSeasonDanmakuMatcher(season.parts.take(season.selfIndex).map { it.subjectId }, pageMatcher)
        }
    }
}

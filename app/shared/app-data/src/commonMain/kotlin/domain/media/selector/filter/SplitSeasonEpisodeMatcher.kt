/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.filter

import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange

/**
 * 自动选择里的拆分季匹配: 从候选列表统计各站点页面的集号, 按资源所在的页面用 [SplitSeasonPageMatcher] 的规则对集.
 * 非数据源页面的资源 (BT, 本地缓存) 不处理.
 */
internal class SplitSeasonEpisodeMatcher private constructor(
    private val pageMatcher: SplitSeasonPageMatcher,
    private val pages: Map<PageKey, SplitSeasonPageMatcher.PageNumbers>,
) {
    class Result(
        val pageKind: SplitSeasonPageMatcher.PageKind,
        /**
         * 页名去掉分段标记后与条目名或季名完全一致.
         */
        val exact: Boolean,
        val matched: Boolean,
        val episodeMatchKind: MatchMetadata.EpisodeMatchKind,
        /**
         * 条目名匹配规则应使用的页名, 见 [SplitSeasonPageMatcher.Classification.nameForMatching].
         */
        val subjectNameForMatching: String?,
    )

    private data class PageKey(val mediaSourceId: String, val subjectName: String, val channel: String?)

    /**
     * 本季的名字, 见 [SplitSeasonPageMatcher.seasonNames].
     */
    val seasonNames: Set<String> get() = pageMatcher.seasonNames

    /**
     * 返回 `null` 表示 [media] 不是站点页面上的一集, 按通常规则匹配.
     */
    fun match(media: Media): Result? {
        if (media.kind != MediaSourceKind.WEB) return null
        val subjectName = media.properties.subjectName ?: return null
        val number = media.episodeRange?.singleIntegerNumber() ?: return null
        val page = pages[PageKey(media.mediaSourceId, subjectName, media.properties.alliance)] ?: return null
        val classification = pageMatcher.classify(subjectName)
        val matchedKind = pageMatcher.matchKind(classification, page, number)
        return Result(
            classification.kind,
            classification.exact,
            matched = matchedKind != null,
            episodeMatchKind = matchedKind ?: MatchMetadata.EpisodeMatchKind.NONE,
            subjectNameForMatching = classification.nameForMatching,
        )
    }

    companion object {
        /**
         * 正在观看拆分季的后半, 并且有足够信息时创建. [list] 用于统计各页面的集号.
         */
        fun create(context: MediaSelectorContext, list: List<Media>): SplitSeasonEpisodeMatcher? {
            val season = context.subjectSeriesInfo?.splitSeason ?: return null
            val episodeInfo = context.episodeInfo ?: return null
            val ownNames = context.subjectInfo?.allNames ?: return null
            val pageMatcher = SplitSeasonPageMatcher.create(season, ownNames, episodeInfo.sort, episodeInfo.ep) ?: return null

            val pages = HashMap<PageKey, MutableSet<Int>>()
            for (media in list) {
                if (media.kind != MediaSourceKind.WEB) continue
                val subjectName = media.properties.subjectName ?: continue
                val number = media.episodeRange?.singleIntegerNumber() ?: continue
                pages.getOrPut(PageKey(media.mediaSourceId, subjectName, media.properties.alliance)) { HashSet() }.add(number)
            }
            if (pages.isEmpty()) return null
            return SplitSeasonEpisodeMatcher(pageMatcher, pages.mapValues { SplitSeasonPageMatcher.PageNumbers(it.value) })
        }

        private fun EpisodeRange.singleIntegerNumber(): Int? {
            val sorts = knownSorts.take(2).toList()
            return sorts.singleOrNull()?.integerOrNull()
        }
    }
}

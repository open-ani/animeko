/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.rss

import androidx.compose.runtime.Immutable
import me.him188.ani.app.domain.mediasource.MediaListFilter
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.domain.mediasource.rss.RssSearchQuery
import me.him188.ani.app.domain.mediasource.rss.toFilterContext
import me.him188.ani.app.domain.mediasource.test.MatchTag
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.rss.RssItem
import me.him188.ani.app.domain.rss.guessResourceLocation
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.titles.ParsedTopicTitle
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse

@Immutable
class RssItemInfo(
    val rss: RssItem,
    val parsed: ParsedTopicTitle,
    val tags: List<MatchTag>,
) {
    companion object {
        fun compute(
            rss: RssItem,
            config: RssSearchConfig,
            query: RssSearchQuery,
        ): RssItemInfo {
            val parsed = RawTitleParser.getDefault().parse(rss.title)
            val tags = computeTags(rss, parsed, query, config)
            return RssItemInfo(rss, parsed, tags)
        }

        /**
         * 计算出用于标记该资源与 [RssSearchQuery] 的匹配情况的 tags. 例如标题成功匹配、缺失 EP 等.
         */
        private fun computeTags(
            rss: RssItem,
            title: ParsedTopicTitle,
            query: RssSearchQuery,
            config: RssSearchConfig,
        ): List<MatchTag> = buildMatchTags {
            with(query.toFilterContext()) {
                val candidate = rss.asCandidate(title)

                if (config.filterByEpisodeSort) {
                    val episodeRange = title.episodeRange
                    if (episodeRange == null) {
                        // 期望使用 EP 过滤但是没有 EP 信息, 属于为缺失
                        emit("EP", isMissing = true)
                    } else {
                        emit(
                            episodeRange.toString(),
                            isMatch = me.him188.ani.app.domain.mediasource.MediaListFilters.ContainsAnyEpisodeInfo.applyOn(
                                candidate,
                            ),
                        )
                    }
                } else {
                    // 不需要用 EP 过滤也展示 EP 信息
                    title.episodeRange?.let {
                        emit(it.toString())
                    }
                }

                if (config.filterBySubjectName) {
                    emit(
                        "标题",
                        isMatch = me.him188.ani.app.domain.mediasource.MediaListFilters.ContainsSubjectName.applyOn(
                            candidate,
                        ),
                    )
                }
            }

            val resourceLocation = rss.guessResourceLocation()
            when (resourceLocation) {
                is ResourceLocation.HttpStreamingFile -> emit("Streaming")
                is ResourceLocation.HttpTorrentFile -> emit("Torrent")
                is ResourceLocation.LocalFile -> emit("Local")
                is ResourceLocation.MagnetLink -> emit("Magnet")
                is ResourceLocation.WebVideo -> emit("WEB")
                null -> emit("Download", isMissing = true)
            }

            // 以下为普通 tags

            if (title.subtitleLanguages.isEmpty()) {
                emit("Subtitle", isMissing = true)
            } else {
                for (subtitleLanguage in title.subtitleLanguages) {
                    emit(subtitleLanguage.displayName)
                }
            }

            title.resolution?.displayName?.let(::emit)

            title.subtitleKind?.let {
                emit(renderSubtitleKind(it) + "字幕")
            }
        }
    }
}

// TODO: 2024/12/14  renderSubtitleKind is a duplicate of MediaDetailsRenderer.renderSubtitleKind. RssItemInfo should not use this.
private fun renderSubtitleKind(
    subtitleKind: SubtitleKind?,
): String? {
    return when (subtitleKind) {
        SubtitleKind.EMBEDDED -> "内嵌"
        SubtitleKind.CLOSED -> "内封"
        SubtitleKind.EXTERNAL_PROVIDED -> "外挂"
        SubtitleKind.EXTERNAL_DISCOVER -> "未知"
        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> "内封或未知"
        null -> null
    }
}

private fun RssItem.asCandidate(parsed: ParsedTopicTitle): MediaListFilter.Candidate {
    return object : MediaListFilter.Candidate {
        override val originalTitle: String get() = title
        override val episodeRange: EpisodeRange? get() = parsed.episodeRange
    }
}

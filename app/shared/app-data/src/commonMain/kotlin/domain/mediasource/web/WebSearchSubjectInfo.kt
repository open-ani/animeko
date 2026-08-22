/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.utils.xml.Element

data class WebSearchSubjectInfo(
    val internalId: String,
    val name: String,
    val fullUrl: String,
    val partialUrl: String,
    val origin: Element?,
)

class WebSearchChannelInfo(
    val name: String,
    val content: Element,
)

data class WebSearchEpisodeInfo(
    /**
     * 播放线路, 与 [name] 一起组成 ID. 如要修改, 考虑 [SelectorMediaSourceEngine.selectMedia]
     */
    val channel: String?,
    /**
     * "第x集" 等原名.
     */
    val name: String,
    /**
     * 解析成功的 [EpisodeSort], 未解析成功则为 `null`.
     * 可能表示 sort, 也可能是 ep.
     */
    val episodeSortOrEp: EpisodeSort?,
    /**
     * 播放地址
     */
    val playUrl: String
)

/**
 * 从剧集列表中找到正在播放的剧集.
 *
 * 匹配优先级 (与按当前集过滤 media 的语义一致, 见 `MediaListFilters.ContainsAnyEpisodeInfo`):
 * 1. [WebSearchEpisodeInfo.episodeSortOrEp] 匹配系列内集数 [episodeSort];
 * 2. 特殊剧集 (非 [EpisodeSort.Normal]) 按剧集名称匹配 [episodeName];
 * 3. [WebSearchEpisodeInfo.episodeSortOrEp] 匹配季度内集数 [episodeEp].
 */
fun List<WebSearchEpisodeInfo>.findMatchingEpisodeOrNull(
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
    episodeName: String?,
): WebSearchEpisodeInfo? {
    firstOrNull { info ->
        info.episodeSortOrEp?.let { EpisodeRange.single(it).contains(episodeSort) } == true
    }?.let { return it }
    if (episodeSort !is EpisodeSort.Normal && !episodeName.isNullOrBlank()) {
        firstOrNull { MediaListFilters.specialContains(it.name, episodeName) }?.let { return it }
    }
    if (episodeEp != null) {
        firstOrNull { info ->
            info.episodeSortOrEp?.let { EpisodeRange.single(it).contains(episodeEp) } == true
        }?.let { return it }
    }
    return null
}

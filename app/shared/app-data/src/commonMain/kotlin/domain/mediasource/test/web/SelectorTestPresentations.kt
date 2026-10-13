/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.web

import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.test.TestEpisodePresentation
import me.him188.ani.app.domain.mediasource.test.TestSubjectPresentation
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.asCandidate
import me.him188.ani.app.domain.mediasource.web.toFilterContext
import me.him188.ani.utils.xml.Element

/**
 * 从选择器数据源的搜索结果构造测试页条目, 标签标出条目名是否能对上请求.
 */
fun TestSubjectPresentation.Companion.compute(
    info: WebSearchSubjectInfo,
    query: SelectorSearchQuery,
    origin: Element?,
): TestSubjectPresentation {
    val tags = buildMatchTags {
        with(query.toFilterContext()) {
            val candidate = info.asCandidate()
            // 条目名匹配由 MediaSelector 对所有 WEB 资源执行, 这里只展示结果
            emit("标题", isMatch = MediaListFilters.ContainsSubjectName.applyOn(candidate))
            if (info.fullUrl.isBlank()) {
                emit("链接", isMissing = true)
            } else {
                emit(info.partialUrl, isMatch = true)
            }
        }
    }
    return TestSubjectPresentation(
        name = info.name,
        subjectDetailsPageUrl = info.fullUrl,
        origin = origin,
        tags = tags,
    )
}

/**
 * 从选择器数据源的剧集信息构造测试页剧集, 标签标出集号是否与请求一致以及播放地址是否有效.
 */
fun TestEpisodePresentation.Companion.compute(
    info: WebSearchEpisodeInfo,
    searchQuery: SelectorSearchQuery,
    origin: Element?,
    config: SelectorSearchConfig,
): TestEpisodePresentation {
    return TestEpisodePresentation(
        channel = info.channel,
        name = info.name,
        episodeSort = info.episodeSortOrEp,
        playUrl = info.playUrl,
        tags = buildMatchTags {
            if (config.autoMatch.filterByEpisodeSort) {
                if (info.episodeSortOrEp == null) {
                    emit("缺失 EP", isMissing = true)
                } else {
                    val actual = info.episodeSortOrEp
                    val expectedEp = searchQuery.episodeEp
                    val expectedSort = searchQuery.episodeSort
                    emit(
                        "EP: ${info.episodeSortOrEp}",
                        isMatch = expectedSort == actual
                                || (expectedEp != null && expectedEp == actual),
                    )
                }
            }

            if (info.channel != null) {
                emit("线路: ${info.channel}")
            }

            when {
                info.playUrl.isEmpty() -> {
                    emit("缺失播放地址", isMissing = true)
                }

                !info.playUrl.startsWith("http") -> {
                    emit("播放地址: ${info.playUrl}", isMatch = false)
                }

                else -> {
                    emit(info.playUrl, isMatch = true)
                }
            }
        },
        origin = origin,
    )
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort

/**
 * 浏览到的站点条目 (搜索结果中的一项).
 * @see MediaSource.searchSubjects
 */
@Serializable
data class BrowseSubject(
    /**
     * 站点上显示的条目名称.
     */
    val name: String,
    /**
     * 条目页面的完整 URL. 同一条目多次浏览之间稳定, 可用来记住用户的选择.
     */
    val url: String,
)

/**
 * 条目页面上的一条线路 (播放列表) 及其剧集.
 */
@Serializable
data class BrowseChannel(
    /**
     * 线路标识, 与自动匹配时资源的线路名 (`Media.properties.alliance`, 线路阶级的键) 一致.
     * 数据源可能把页面上的多条线路归一化成同一个标识, 因此同一条目里可以重复. 站点没有线路概念时为 `null`.
     */
    val name: String?,
    /**
     * 页面上显示的线路原文, 供展示. 站点没有线路概念时为 `null`.
     */
    val label: String? = name,
    /**
     * 该线路的剧集, 顺序与页面一致.
     */
    val episodes: List<BrowseEpisode>,
)

/**
 * 线路中的一集.
 */
@Serializable
data class BrowseEpisode(
    /**
     * 站点上显示的名称, 例如 "第1集", "OVA", "HD1080P".
     */
    val name: String,
    /**
     * 播放页面的完整 URL. 同一集多次浏览之间稳定.
     */
    val url: String,
    /**
     * 数据源从 [name] 解析出的集号, 仅供展示时提示. 解析不出为 `null`.
     */
    val episodeSort: EpisodeSort? = null,
)

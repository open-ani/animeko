/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.datasources.api.EpisodeSort
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

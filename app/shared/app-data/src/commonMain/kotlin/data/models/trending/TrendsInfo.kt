/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.trending

import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.client.models.AniTrendingSubject

data class TrendsInfo(
    val subjects: List<TrendingSubjectInfo>
)

/**
 * @see AniTrendingSubject
 */
data class TrendingSubjectInfo(
    val bangumiId: Int,
    val nameCn: String,
    val imageLarge: String,
)

/**
 * 完整热度排行中的一项.
 *
 * @param rank 在热度排行中的名次, 从 1 开始. 与条目的评分排名无关.
 * @param heat 最近 30 天在 Bangumi 标记为在看的人数.
 */
data class TrendingRankingItemInfo(
    val rank: Int,
    val heat: Int,
    val subject: BatchSubjectDetails,
)

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.search

import me.him188.ani.app.data.models.schedule.AnimeSeasonId

data class SubjectSearchQuery(
    val keywords: String,
    val type: SubjectType = SubjectType.ANIME,
//    val useOldSearchApi: Boolean = true,
    val tags: List<String>? = null,
    val season: AnimeSeasonId? = null,
    val rating: RatingRange? = null,
//    val rank: Pair<String?, String?> = Pair(null, null),
    val nsfw: Boolean? = null,
    val sort: SearchSort = SearchSort.MATCH,
) {
    fun hasFilters(): Boolean {
        return tags != null || season != null || rating != null || nsfw != null || sort != SearchSort.MATCH
    }
}

/**
 * @see me.him188.ani.datasources.bangumi.models.search.BangumiSort
 */
enum class SearchSort {
    MATCH,

    /**
     * 排名
     */
    RANK,

    /**
     * 收藏人数
     */
    COLLECTION,
}

data class RatingRange(
    val min: Int?,
    val max: Int?,
)

enum class SubjectType {
    ANIME,

    /*
    bangumi supports
            条目类型
            - `1` 为 书籍
            - `2` 为 动画
            - `3` 为 音乐
            - `4` 为 游戏
            - `6` 为 三次元
            
            没有 `5`
     */
}

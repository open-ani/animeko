/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.search

data class SubjectSearchQuery(
    val keywords: String,
    val type: SubjectType = SubjectType.ANIME,
//    val useOldSearchApi: Boolean = true,
    val tags: List<String>? = null,
    /**
     * 番剧索引的年份筛选. null 表示不限年份.
     */
    val year: Int? = null,
    /**
     * 番剧索引的季度筛选, 取值 1..4 (冬/春/夏/秋). null 表示不限季度.
     *
     * 季度从属于 [year]: 仅当 [year] 非空时才有意义. 服务端按单个日期区间过滤,
     * 无法表达"所有年份的某个季度", 因此不支持跨年的仅季度筛选 (与 B 站索引行为一致).
     */
    val quarter: Int? = null,
    val rating: RatingRange? = null,
//    val rank: Pair<String?, String?> = Pair(null, null),
    val nsfw: Boolean? = null,
    val sort: SearchSort = SearchSort.MATCH,
) {
    fun normalized(): SubjectSearchQuery {
        return copy(keywords = keywords.trim())
    }

    fun hasFilters(): Boolean {
        return tags != null || year != null || rating != null || nsfw != null || sort != SearchSort.MATCH
    }

    fun hasSearchRequest(): Boolean {
        return keywords.isNotEmpty() || hasFilters()
    }
}

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

    /**
     * 发布日期
     */
    DATE,
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

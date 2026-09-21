/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.search

import me.him188.ani.app.data.models.schedule.AnimeSeason

data class SubjectSearchQuery(
    val keywords: String,
    val type: SubjectType = SubjectType.ANIME,
//    val useOldSearchApi: Boolean = true,
    val tags: List<String>? = null,
    /**
     * 番剧索引的年份筛选. null 表示不限年份.
     *
     * 语义为自然年 (该年 [year]-01-01 至 [year]+1-01-01 开播). 与按季度浏览不同,
     * 上年 12 月开播的冬季档 (如 2025-12 开播的 2026 冬季番) 只会在 "该年 + Q1" 中出现,
     * 不会落入仅年份筛选 (与 B 站索引行为一致).
     */
    val year: Int? = null,
    /**
     * 番剧索引的季度筛选. null 表示不限季度.
     *
     * 季度从属于 [year]: 仅当 [year] 非空时才有意义 (由 init 校验, 类型上保证该不变量).
     * 服务端按单个日期区间过滤, 无法表达"所有年份的某个季度",
     * 因此不支持跨年的仅季度筛选 (与 B 站索引行为一致).
     */
    val season: AnimeSeason? = null,
    val rating: RatingRange? = null,
//    val rank: Pair<String?, String?> = Pair(null, null),
    val nsfw: Boolean? = null,
    val sort: SearchSort = SearchSort.MATCH,
) {
    init {
        // 季度从属于年份: 只选季度而不选年份的查询没有意义, 直接拒绝构造.
        require(season == null || year != null) { "season 从属于 year, 不能单独设置" }
    }

    fun normalized(): SubjectSearchQuery {
        return copy(keywords = keywords.trim())
    }

    fun hasFilters(): Boolean {
        return tags != null || year != null || season != null || rating != null || nsfw != null ||
                sort != SearchSort.MATCH
    }

    fun hasSearchRequest(): Boolean {
        return keywords.isNotEmpty() || hasFilters()
    }
}

/**
 * 切换浏览年份筛选. [year] 为 null 表示"全部年份", 同时清除从属的季度筛选
 * (避免出现"季度仍被选中但年份不限"的不一致状态).
 *
 * 切换到具体年份时保留原有季度, 语义为"该季度在新年份内".
 */
fun SubjectSearchQuery.withYearFilter(year: Int?): SubjectSearchQuery {
    return if (year == null) {
        copy(year = null, season = null)
    } else {
        copy(year = year)
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

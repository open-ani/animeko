/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

data class SubjectSearchFilters(
    val tags: List<String>? = null, // "童年", "原创"
    val airDates: List<String>? = null, // YYYY-MM-DD
    val ratings: List<String>? = null, // ">=6", "<8"
    val ranks: List<String>? = null,
    val nsfw: Boolean? = null,
)

enum class SubjectSearchField {
    NAME,
    SUMMARY,
    IMAGE_LARGE,
    NSFW,
    AIR_DATE,
    SCORE,
    RANK,
    RATING_TOTAL,
    FAVORITE,
    TAGS,
    MAIN_EPISODE_COUNT,
    LIGHT_RELATED_PERSON_INFO,
}

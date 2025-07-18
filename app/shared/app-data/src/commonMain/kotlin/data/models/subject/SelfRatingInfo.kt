/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.persistent.database.ProtoConverters
import me.him188.ani.utils.platform.annotations.TestOnly

@Serializable
@Immutable
data class SelfRatingInfo(
    /**
     * 0 表示未评分
     */
    val score: Int,
    /**
     * `null` 表示未评价
     */
    val comment: String?,
    @all:TypeConverters(ProtoConverters.StringList::class)
    val tags: List<String>,
    val isPrivate: Boolean,
) {
    companion object {
        @Stable
        val Empty = SelfRatingInfo(0, null, emptyList(), false)
    }
}

@TestOnly
val TestSelfRatingInfo
    get() = SelfRatingInfo(
        score = 7,
        comment = "test",
        tags = listOf("My tag"),
        isPrivate = false,
    )

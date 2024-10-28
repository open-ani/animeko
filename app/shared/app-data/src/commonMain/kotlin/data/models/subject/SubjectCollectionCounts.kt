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
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * @see UnifiedCollectionType
 */
@Immutable
data class SubjectCollectionCounts(
    val wish: Int,
    val doing: Int,
    val done: Int,
    val onHold: Int,
    val dropped: Int,
    val total: Int,
) {
    @Stable
    fun getCount(type: UnifiedCollectionType): Int {
        return when (type) {
            UnifiedCollectionType.WISH -> wish
            UnifiedCollectionType.DOING -> doing
            UnifiedCollectionType.DONE -> done
            UnifiedCollectionType.ON_HOLD -> onHold
            UnifiedCollectionType.DROPPED -> dropped
            UnifiedCollectionType.NOT_COLLECTED -> throw IllegalArgumentException("NOT_COLLECTED is not a valid collection type")
        }
    }
}

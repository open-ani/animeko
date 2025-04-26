/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.topic

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.NOT_COLLECTED

/**
 * Unified type for all collection types, also added representation for [NOT_COLLECTED].
 */
@Serializable // so don't change field names
enum class UnifiedCollectionType {
    WISH,
    DOING,
    DONE,
    ON_HOLD,
    DROPPED,

    /**
     * The item is not collected.
     *
     * This is a dummy type that will not be returned from the server,
     * as when the item is not collected, the server simply won't return the item.
     *
     * [NOT_COLLECTED] can be useful to represent the "Delete" action to be received as an argument
     * e.g. to update the user's collection type.
     */
    NOT_COLLECTED,
}

fun UnifiedCollectionType.isDoneOrDropped(): Boolean {
    return this == UnifiedCollectionType.DONE || this == UnifiedCollectionType.DROPPED
}

/**
 * 反转收藏状态
 */
fun UnifiedCollectionType.toggleCollected(): UnifiedCollectionType {
    return if (this.isDoneOrDropped()) {
        UnifiedCollectionType.WISH
    } else {
        UnifiedCollectionType.DONE
    }
}

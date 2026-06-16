/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.player

import kotlinx.serialization.Serializable

/**
 * 播放记录
 */
@Serializable
data class EpisodeHistory(
    val episodeId: Int,
    val positionMillis: Long,
    val subjectId: Int? = null,
    val episodeSort: Float? = null,
    val subjectName: String? = null,
    val subjectImageUrl: String? = null,
    val episodeName: String? = null,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long = 0,
    val deletedAtMillis: Long? = null,
    val isDirty: Boolean = true,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null

    val versionMillis: Long get() = maxOf(updatedAtMillis, deletedAtMillis ?: 0L)
}

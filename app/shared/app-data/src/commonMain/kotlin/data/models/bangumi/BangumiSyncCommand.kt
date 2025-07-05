/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.bangumi

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniSelfRatingInfo

// 注意, 这些 schema 都是对应 server v5.0.0 的, 不能修改参数名称.

@Serializable
data class BangumiSyncCommand(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "op") val op: BangumiSyncOp?,
    @SerialName(value = "createdAt") val createdAt: Instant,
)

@Serializable
sealed class BangumiSyncOp() {
    @SerialName("UpdateCollection")
    @Serializable
    data class UpdateCollection(
        val subjectId: Long,
        @SerialName("collectionType") val type: AniCollectionType?,
        val rating: AniSelfRatingInfo? = null,
    ) : BangumiSyncOp()

    @SerialName("DeleteCollection")
    @Serializable
    data class DeleteCollection(
        val subjectId: Long,
    ) : BangumiSyncOp()

    @SerialName("AddCollection")
    @Serializable
    data class AddCollection(
        val subjectId: Long,
        @SerialName("collectionType") val type: AniCollectionType,
        val rating: AniSelfRatingInfo? = null,
    ) : BangumiSyncOp()

    @SerialName("UpdateEpisodeCollection")
    @Serializable
    data class UpdateEpisodeCollection(
        val subjectId: Long,
        val episodeId: Long,
        @SerialName("episodeCollectionType") val type: AniEpisodeCollectionType?,
    ) : BangumiSyncOp()
}

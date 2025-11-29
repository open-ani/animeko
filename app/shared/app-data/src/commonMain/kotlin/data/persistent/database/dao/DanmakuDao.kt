/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuMatchInfo
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.SimpleDanmakuProvider

@Entity(
    tableName = "danmaku",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EpisodeCollectionEntity::class,
            parentColumns = ["subjectId", "episodeId"],
            childColumns = ["subjectId", "episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["id"]),
        Index(value = ["subjectId"]),
        Index(value = ["subjectId", "episodeId"]),
    ],
)
class DanmakuEntity(
    val id: String,
    val subjectId: Int,
    val episodeId: Int,
    val serviceId: DanmakuServiceId, // 弹幕源的 service id
    val presentationServiceId: DanmakuServiceId, // 外显 service id, dandanplay 的 origin
    val senderId: String,
    @Embedded(prefix = "content_") val content: DanmakuContent
)

@Dao
interface DanmakuDao {
    @Query("SELECT COUNT(*) FROM danmaku WHERE subjectId = :subjectId AND episodeId = :episodeId")
    fun countBySubjectAndEpisode(subjectId: Int, episodeId: Int): Flow<Int>

    @Query("SELECT * FROM danmaku WHERE subjectId = :subjectId AND episodeId = :episodeId")
    suspend fun getDanmaku(subjectId: Int, episodeId: Int): List<DanmakuEntity>

    @Upsert
    suspend fun upsertAll(danmaku: List<DanmakuEntity>)

    @Query("DELETE FROM danmaku WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: Int)

    @Query("DELETE FROM danmaku WHERE subjectId = :subjectId AND episodeId = :episodeId")
    suspend fun deleteBySubjectAndEpisode(subjectId: Int, episodeId: Int)
}


class LocalDanmakuProvider(
    private val danmakuDao: DanmakuDao,
) : SimpleDanmakuProvider {
    override val providerId: DanmakuProviderId = DanmakuProviderId.Local
    override val mainServiceId: DanmakuServiceId = DanmakuServiceId.Animeko

    override suspend fun fetchAutomatic(request: DanmakuFetchRequest): List<DanmakuFetchResult> {
        val list = danmakuDao.getDanmaku(request.subjectId, request.episodeId)
            .groupBy { it.presentationServiceId }

        return list.map { (presentationServiceId, danmakus) ->
            DanmakuFetchResult(
                providerId = providerId,
                matchInfo = DanmakuMatchInfo(
                    serviceId = presentationServiceId,
                    count = danmakus.size,
                    method = DanmakuMatchMethod.ExactId(request.subjectId, request.episodeId),
                ),
                list = danmakus.map {
                    DanmakuInfo(
                        id = it.id,
                        serviceId = it.serviceId,
                        senderId = it.senderId,
                        content = it.content,
                    )
                },
            )
        }
    }
}
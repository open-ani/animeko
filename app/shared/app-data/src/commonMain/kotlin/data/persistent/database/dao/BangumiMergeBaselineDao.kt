/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.persistent.database.ProtoConverters
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * Bangumi 合并的同步基线: 上次成功同步 (或应用合并) 后, 每个条目的收藏状态快照.
 *
 * 冲突检测以此为三方合并的共同祖先: 本地与远端相对基线都有不同修改的字段才是冲突,
 * 只有一侧修改的字段可自动合并.
 *
 * 故意不设置到 `subject_collection` 的外键: 收藏行在同步时可能被整表删除重建
 * (如 [SubjectCollectionDao.deleteAll]), 基线必须在这种情况下保留.
 *
 * @since 5.4.0
 */
@Entity(
    tableName = "bangumi_merge_baseline",
)
data class BangumiMergeBaselineEntity(
    @PrimaryKey val subjectId: Int,
    /**
     * 收藏状态. [UnifiedCollectionType.NOT_COLLECTED] 表示基线记录为未收藏 (例如合并结果为删除收藏).
     */
    val collectionType: UnifiedCollectionType,
    /**
     * 评分. `0` 表示未评分.
     */
    val score: Int,
    /**
     * 短评. `null` 表示无短评.
     */
    val comment: String?,
    /**
     * 已看过 (DONE) 的剧集 id, 升序. 其他剧集视为未看.
     */
    @field:TypeConverters(ProtoConverters.IntList::class)
    val watchedEpisodeIds: List<Int>,
    /**
     * 此基线写入时间 (epoch millis), 审计用.
     */
    val updatedAtMillis: Long,
)

@Dao
interface BangumiMergeBaselineDao {
    @Query("SELECT * FROM bangumi_merge_baseline")
    suspend fun getAll(): List<BangumiMergeBaselineEntity>

    @Query("SELECT * FROM bangumi_merge_baseline WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: Int): BangumiMergeBaselineEntity?

    @Upsert
    suspend fun upsertAll(items: List<BangumiMergeBaselineEntity>)

    @Query("DELETE FROM bangumi_merge_baseline WHERE subjectId IN (:subjectIds)")
    suspend fun deleteBySubjectIds(subjectIds: Collection<Int>)

    @Query("DELETE FROM bangumi_merge_baseline")
    suspend fun deleteAll()

    /**
     * 用 [items] 整体替换现有基线.
     */
    @Transaction
    suspend fun replaceAll(items: List<BangumiMergeBaselineEntity>) {
        deleteAll()
        upsertAll(items)
    }
}

/**
 * 供 commonTest 使用的内存实现.
 */
@TestOnly
fun createMemoryBangumiMergeBaselineDao(): BangumiMergeBaselineDao = object : BangumiMergeBaselineDao {
    private val items = MutableStateFlow<Map<Int, BangumiMergeBaselineEntity>>(emptyMap())

    override suspend fun getAll(): List<BangumiMergeBaselineEntity> =
        items.value.values.sortedBy { it.subjectId }

    override suspend fun getBySubjectId(subjectId: Int): BangumiMergeBaselineEntity? =
        items.value[subjectId]

    override suspend fun upsertAll(items: List<BangumiMergeBaselineEntity>) {
        this.items.update { map -> map + items.associateBy { it.subjectId } }
    }

    override suspend fun deleteBySubjectIds(subjectIds: Collection<Int>) {
        items.update { it - subjectIds.toSet() }
    }

    override suspend fun deleteAll() {
        items.value = emptyMap()
    }
}

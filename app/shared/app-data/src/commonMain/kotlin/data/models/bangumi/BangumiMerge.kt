/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.bangumi

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

/**
 * 参与合并的一侧. 合并总是发生在 Animeko (服务端数据库) 与 Bangumi (bgm.tv) 之间.
 */
enum class BangumiMergeSide {
    /** Animeko 侧 */
    ANIMEKO,

    /** Bangumi 侧 */
    BANGUMI,
}

/**
 * 条目内可以冲突或被自动合并的字段类型. 与服务端 `BangumiConflictFieldType` 一致.
 */
enum class BangumiConflictFieldType {
    /**
     * 收藏状态, 包括一侧删除了收藏.
     */
    COLLECTION,

    /**
     * 评分单元: 评分 + 短评 + 标签 + 是否私密, 作为整体解决.
     */
    RATING,

    /**
     * 剧集观看进度. 只出现在自动合并明细中, 服务端不会产生此类型的冲突.
     */
    EPISODE,
}

/**
 * 条目内一个需要用户确认的冲突字段, 携带两侧的取值.
 *
 * 服务端只会产生 [Collection] 与 [Rating] 两种冲突.
 */
@Immutable
sealed class BangumiConflictField {
    abstract val type: BangumiConflictFieldType

    /**
     * 收藏状态冲突. 某一侧为 [UnifiedCollectionType.NOT_COLLECTED] 表示该侧未收藏 (已删除收藏),
     * 此时整个条目只有这一个冲突, 选择 NOT_COLLECTED 的一侧会删除收藏 (破坏性).
     */
    @Immutable
    data class Collection(
        val animeko: UnifiedCollectionType,
        val bangumi: UnifiedCollectionType,
    ) : BangumiConflictField() {
        override val type: BangumiConflictFieldType get() = BangumiConflictFieldType.COLLECTION

        fun sideValue(side: BangumiMergeSide): UnifiedCollectionType = when (side) {
            BangumiMergeSide.ANIMEKO -> animeko
            BangumiMergeSide.BANGUMI -> bangumi
        }
    }

    /**
     * 评分单元冲突. 两侧都有非空的评分单元且不同 (评分 / 短评 / 标签 / 私密任一不同).
     */
    @Immutable
    data class Rating(
        val animeko: SelfRatingInfo,
        val bangumi: SelfRatingInfo,
    ) : BangumiConflictField() {
        override val type: BangumiConflictFieldType get() = BangumiConflictFieldType.RATING

        fun sideValue(side: BangumiMergeSide): SelfRatingInfo = when (side) {
            BangumiMergeSide.ANIMEKO -> animeko
            BangumiMergeSide.BANGUMI -> bangumi
        }
    }
}

/**
 * 冲突的全局唯一标识: 条目 + 字段类型. 每个条目每种字段类型最多一个冲突.
 */
@Immutable
data class BangumiConflictKey(
    val subjectId: Int,
    val fieldType: BangumiConflictFieldType,
)

/**
 * 一个条目的全部冲突.
 */
@Immutable
data class BangumiSubjectConflict(
    val subjectId: Int,
    /**
     * 展示用标题 (优先中文名, 其次原名, 都没有则为 `#subjectId`).
     */
    val title: String,
    /**
     * 检测时 Animeko 侧收藏的更新时间. `null` 表示未知.
     */
    val animekoUpdatedAt: Instant?,
    /**
     * 检测时 Bangumi 侧收藏的更新时间. Bangumi 只在收藏类型变化时更新它, 因此只对 [BangumiConflictField.Collection] 有意义;
     * Bangumi 侧已删除时为 `null`.
     */
    val bangumiUpdatedAt: Instant?,
    val detectedAt: Instant,
    val fields: List<BangumiConflictField>,
) {
    val conflictKeys: List<BangumiConflictKey>
        get() = fields.map { BangumiConflictKey(subjectId, it.type) }
}

/**
 * 上次全量同步自动合并 (无需用户确认) 的一项差异.
 */
@Immutable
data class BangumiAutoMergedChange(
    val subjectId: Int,
    val title: String,
    val fieldType: BangumiConflictFieldType,
    /**
     * 采用了哪一侧的值.
     */
    val side: BangumiMergeSide,
    /**
     * [BangumiConflictFieldType.COLLECTION]: 是否为新增收藏 (而非修改收藏状态).
     */
    val isNew: Boolean,
    /**
     * [BangumiConflictFieldType.COLLECTION]: 合并后的收藏状态.
     */
    val collectionType: UnifiedCollectionType?,
    /**
     * [BangumiConflictFieldType.RATING]: 合并后的评分单元.
     */
    val rating: SelfRatingInfo?,
    /**
     * [BangumiConflictFieldType.EPISODE]: 标记为已看的剧集序号 (如 "5", "12.5"). 剧集库中没有时为 `#episodeId`.
     */
    val watchedEpisodeSorts: List<String>,
    /**
     * [BangumiConflictFieldType.EPISODE]: 取消已看的剧集序号.
     */
    val unwatchedEpisodeSorts: List<String>,
)

/**
 * 冲突摘要, 用于主界面提示与设置入口.
 */
@Immutable
data class BangumiMergeSummary(
    /**
     * 待处理的冲突字段总数 (所有条目的 fields.size 之和).
     */
    val conflictCount: Int,
    val autoMergedTotal: Int,
    /**
     * 上次全量同步 (对账) 完成时间. 从未完成过时为 `null`.
     */
    val lastSyncedAt: Instant?,
    /**
     * 服务端全量同步是否正在进行中. 进行中时冲突列表可能不完整.
     */
    val syncInProgress: Boolean,
)

/**
 * 合并界面的完整状态: 待确认的冲突 + 上次全量同步已自动合并的差异.
 */
@Immutable
data class BangumiMergeState(
    val conflicts: List<BangumiSubjectConflict>,
    /**
     * 自动合并明细, 服务端最多返回 100 条; 总数见 [autoMergedTotal].
     */
    val autoMerged: List<BangumiAutoMergedChange>,
    val autoMergedTotal: Int,
    val lastSyncedAt: Instant?,
    val syncInProgress: Boolean,
) {
    /**
     * 待处理的冲突字段总数.
     */
    val conflictCount: Int get() = conflicts.sumOf { it.fields.size }

    val hasConflicts: Boolean get() = conflicts.isNotEmpty()

    companion object {
        val Empty = BangumiMergeState(
            conflicts = emptyList(),
            autoMerged = emptyList(),
            autoMergedTotal = 0,
            lastSyncedAt = null,
            syncInProgress = false,
        )
    }
}

/**
 * 用户对一个冲突字段的选择.
 */
@Immutable
data class BangumiConflictResolution(
    val subjectId: Int,
    val fieldType: BangumiConflictFieldType,
    val side: BangumiMergeSide,
)

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiSubjectConflict
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

/**
 * 字段在一侧的取值与修改时间.
 */
@Immutable
data class MergeSideValue<out T>(
    val value: T,
    /**
     * 此侧此字段的最后修改时间. `null` 表示未知 (UI 不显示时间与 ● 较新标记).
     */
    val modifiedAt: Instant?,
)

/**
 * 评分单元在一侧的取值: 评分 + 短评 + 标签 + 是否私密.
 */
@Immutable
data class MergeRatingValue(
    /**
     * `0` 表示未评分.
     */
    val score: Int,
    /**
     * 已归一化: 空白短评视为 `null`.
     */
    val comment: String?,
    val tags: List<String>,
    val isPrivate: Boolean,
) {
    companion object {
        fun from(info: SelfRatingInfo): MergeRatingValue = MergeRatingValue(
            score = info.score,
            comment = info.comment?.takeIf { it.isNotBlank() },
            tags = info.tags,
            isPrivate = info.isPrivate,
        )
    }
}

/**
 * 合并界面中的一行冲突. 由服务端的 [BangumiConflictField] 映射而来:
 *
 * - [BangumiConflictField.Collection] → [Collection];
 * - [BangumiConflictField.Rating] → [Rating] (评分不同, 或仅标签/私密不同) 或 [Comment] (仅短评不同).
 *
 * 每行都对应服务端的一个冲突字段 [key], 用户的选择按 [key] 提交.
 */
@Immutable
sealed class BangumiMergeConflict {
    abstract val subjectId: Int
    abstract val fieldType: BangumiConflictFieldType

    /**
     * 提交选择时使用的冲突标识.
     */
    val key: BangumiConflictKey get() = BangumiConflictKey(subjectId, fieldType)

    /**
     * 较新的一侧. 两侧修改时间都已知且不相等时非 `null`; "采用较新的" 只作用于非 `null` 的行.
     */
    abstract val newerSide: BangumiMergeSide?

    /**
     * 收藏状态冲突. 某一侧为 [UnifiedCollectionType.NOT_COLLECTED] 表示该侧已删除收藏 (破坏性, 以 error 色警示).
     *
     * Bangumi 只在收藏类型变化时更新 `updated_at`, 因此只有这种行能显示 ● 较新标记;
     * Bangumi 侧已删除时没有 Bangumi 时间, 也不显示 ●.
     */
    @Immutable
    data class Collection(
        override val subjectId: Int,
        val animeko: MergeSideValue<UnifiedCollectionType>,
        val bangumi: MergeSideValue<UnifiedCollectionType>,
    ) : BangumiMergeConflict() {
        override val fieldType: BangumiConflictFieldType get() = BangumiConflictFieldType.COLLECTION
        override val newerSide: BangumiMergeSide? = newerSideOf(animeko.modifiedAt, bangumi.modifiedAt)
    }

    /**
     * 评分单元冲突, 以评分为主展示. [includesComment] / [includesTags] / [includesPrivate] 标记哪些次级字段也不同,
     * 需要在单元格内一并展示 (选择时整个评分单元一起解决).
     */
    @Immutable
    data class Rating(
        override val subjectId: Int,
        val animeko: MergeSideValue<MergeRatingValue>,
        val bangumi: MergeSideValue<MergeRatingValue>,
        /**
         * 短评不同, 在评分后展示短评.
         */
        val includesComment: Boolean,
        /**
         * 标签不同, 次级文本展示 "标签: …".
         */
        val includesTags: Boolean,
        /**
         * 是否私密不同, 次级文本展示 "私密" / "公开".
         */
        val includesPrivate: Boolean,
    ) : BangumiMergeConflict() {
        override val fieldType: BangumiConflictFieldType get() = BangumiConflictFieldType.RATING
        override val newerSide: BangumiMergeSide? = newerSideOf(animeko.modifiedAt, bangumi.modifiedAt)
    }

    /**
     * 评分单元冲突, 但只有短评不同 (评分 / 标签 / 私密都相同), 以短评行展示.
     */
    @Immutable
    data class Comment(
        override val subjectId: Int,
        val animeko: MergeSideValue<String?>,
        val bangumi: MergeSideValue<String?>,
    ) : BangumiMergeConflict() {
        override val fieldType: BangumiConflictFieldType get() = BangumiConflictFieldType.RATING
        override val newerSide: BangumiMergeSide? = newerSideOf(animeko.modifiedAt, bangumi.modifiedAt)
    }

    /**
     * 此侧的修改时间.
     */
    fun sideModifiedAt(side: BangumiMergeSide): Instant? = when (this) {
        is Collection -> sideValue(side).modifiedAt
        is Rating -> sideValue(side).modifiedAt
        is Comment -> sideValue(side).modifiedAt
    }

    /**
     * 选择 [side] 后, 此冲突是否会导致收藏被删除等破坏性结果. UI 用 error 色警示.
     */
    fun isDestructive(side: BangumiMergeSide): Boolean = when (this) {
        is Collection -> sideValue(side).value == UnifiedCollectionType.NOT_COLLECTED
        is Rating, is Comment -> false
    }
}

fun BangumiMergeConflict.Collection.sideValue(side: BangumiMergeSide): MergeSideValue<UnifiedCollectionType> =
    when (side) {
        BangumiMergeSide.ANIMEKO -> animeko
        BangumiMergeSide.BANGUMI -> bangumi
    }

fun BangumiMergeConflict.Rating.sideValue(side: BangumiMergeSide): MergeSideValue<MergeRatingValue> =
    when (side) {
        BangumiMergeSide.ANIMEKO -> animeko
        BangumiMergeSide.BANGUMI -> bangumi
    }

fun BangumiMergeConflict.Comment.sideValue(side: BangumiMergeSide): MergeSideValue<String?> =
    when (side) {
        BangumiMergeSide.ANIMEKO -> animeko
        BangumiMergeSide.BANGUMI -> bangumi
    }

/**
 * 一个条目的全部冲突行, 对应界面上的一张分组卡片.
 */
@Immutable
data class SubjectMergeConflictGroup(
    val subjectId: Int,
    val title: String,
    val conflicts: List<BangumiMergeConflict>,
) {
    val conflictKeys: List<BangumiConflictKey>
        get() = conflicts.map { it.key }
}

/**
 * 两侧修改时间都已知且不相等时返回较新的一侧, 否则 `null`.
 */
fun newerSideOf(animeko: Instant?, bangumi: Instant?): BangumiMergeSide? {
    if (animeko == null || bangumi == null || animeko == bangumi) return null
    return if (animeko > bangumi) BangumiMergeSide.ANIMEKO else BangumiMergeSide.BANGUMI
}

/**
 * 把服务端的冲突列表映射为界面的分组卡片.
 */
fun BangumiMergeState.toConflictGroups(): List<SubjectMergeConflictGroup> = conflicts.map { it.toUiGroup() }

fun BangumiSubjectConflict.toUiGroup(): SubjectMergeConflictGroup = SubjectMergeConflictGroup(
    subjectId = subjectId,
    title = title,
    conflicts = fields.map { it.toUiConflict(subjectId, animekoUpdatedAt, bangumiUpdatedAt) },
)

/**
 * 把一个服务端冲突字段映射为一行.
 *
 * @param animekoUpdatedAt 条目在 Animeko 侧的更新时间 (条目级).
 * @param bangumiUpdatedAt 条目在 Bangumi 侧的更新时间; Bangumi 只在收藏类型变化时更新它,
 * 因此只用于 [BangumiConflictField.Collection]; 评分行两侧都不显示时间.
 */
fun BangumiConflictField.toUiConflict(
    subjectId: Int,
    animekoUpdatedAt: Instant?,
    bangumiUpdatedAt: Instant?,
): BangumiMergeConflict = when (this) {
    is BangumiConflictField.Collection -> BangumiMergeConflict.Collection(
        subjectId = subjectId,
        animeko = MergeSideValue(animeko, animekoUpdatedAt),
        bangumi = MergeSideValue(
            bangumi,
            // Bangumi 侧已删除时没有更新时间.
            if (bangumi == UnifiedCollectionType.NOT_COLLECTED) null else bangumiUpdatedAt,
        ),
    )

    is BangumiConflictField.Rating -> {
        val a = MergeRatingValue.from(animeko)
        val b = MergeRatingValue.from(bangumi)
        val scoreDiffers = a.score != b.score
        val commentDiffers = a.comment != b.comment
        val tagsDiffer = a.tags.toSet() != b.tags.toSet()
        val privateDiffers = a.isPrivate != b.isPrivate
        if (!scoreDiffers && commentDiffers && !tagsDiffer && !privateDiffers) {
            // 仅短评不同: 短评行. 评分单元的时间对 Bangumi 无意义, 两侧都不显示.
            BangumiMergeConflict.Comment(
                subjectId = subjectId,
                animeko = MergeSideValue(a.comment, null),
                bangumi = MergeSideValue(b.comment, null),
            )
        } else {
            BangumiMergeConflict.Rating(
                subjectId = subjectId,
                animeko = MergeSideValue(a, null),
                bangumi = MergeSideValue(b, null),
                includesComment = commentDiffers,
                includesTags = tagsDiffer,
                includesPrivate = privateDiffers,
            )
        }
    }
}

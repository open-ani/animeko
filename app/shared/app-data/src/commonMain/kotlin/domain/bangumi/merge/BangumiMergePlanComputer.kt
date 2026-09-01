/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.bangumi.merge

import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

/**
 * 三方合并引擎: 对每个条目, 以上次同步基线为参照, 对比 Animeko 与 Bangumi 两侧的当前状态,
 * 计算出需要用户确认的冲突与可自动合并的差异.
 *
 * 合并规则 (对每个原子字段):
 * - 两侧值相同: 无差异; 若与基线不同, 记为一致更改自动合并 ([AutoMergeReason.CONSISTENT]).
 * - 两侧值不同:
 *     - 无基线: 记为冲突, 由用户决定.
 *     - 仅一侧相对基线有修改: 自动采用修改过的一侧 ([AutoMergeReason.LOCAL_ONLY] / [AutoMergeReason.REMOTE_ONLY]).
 *     - 两侧都相对基线有修改: 记为冲突.
 *
 * 特殊规则:
 * - 若收藏状态的差异涉及一侧未收藏/已删除 ([UnifiedCollectionType.NOT_COLLECTED]),
 *   则该条目按整体处理: 只产生一条收藏冲突 (或自动合并), 不再产生剧集/评分/短评级别的差异.
 *   用户 (或自动合并) 的选择将决定整个条目采用哪一侧的完整状态.
 * - 单侧修改若是删除收藏 (破坏性变更), 不自动合并, 仍记为冲突由用户确认.
 * - 评分与短评同时冲突时, 短评并入评分冲突一起解决, 只展示一行.
 *
 * 此类为纯函数, 不做任何 IO.
 */
class BangumiMergePlanComputer {

    fun compute(inputs: List<SubjectMergeInput>): BangumiMergePlan {
        // 归一化后再计算, 并把归一化后的输入存入计划:
        // 保证 BangumiMergeOpCompiler 对"涉及未收藏"等判定与本引擎一致.
        val normalizedInputs = inputs.map { it.normalized() }

        val conflictGroups = mutableListOf<SubjectMergeConflictGroup>()
        val autoMerged = mutableListOf<AutoMergedChange>()

        for (input in normalizedInputs) {
            val result = computeSubject(input)
            if (result.conflicts.isNotEmpty()) {
                conflictGroups.add(SubjectMergeConflictGroup(input.subjectId, input.title, result.conflicts))
            }
            autoMerged.addAll(result.autoMerged)
        }

        return BangumiMergePlan(
            conflictGroups = conflictGroups,
            autoMerged = autoMerged,
            inputs = normalizedInputs,
        )
    }

    private class SubjectResult(
        val conflicts: List<BangumiMergeConflict>,
        val autoMerged: List<AutoMergedChange>,
    )

    /**
     * 单个原子字段的对比结果.
     */
    private sealed class FieldOutcome<out T> {
        /** 两侧一致且与基线一致 (或无基线且一致), 无差异. */
        data object Unchanged : FieldOutcome<Nothing>()

        /** 两侧一致更改. */
        data class Consistent<T>(val value: T) : FieldOutcome<T>()

        /** 仅一侧修改, 自动采用. */
        data class AutoTake<T>(val side: BangumiMergeSide, val value: T) : FieldOutcome<T>()

        /** 两侧都修改且不一致, 需要用户确认. */
        data object Conflict : FieldOutcome<Nothing>()
    }

    private fun <T> compareField(local: T, remote: T, base: T?, hasBase: Boolean): FieldOutcome<T> {
        if (local == remote) {
            return if (hasBase && local != base) FieldOutcome.Consistent(local) else FieldOutcome.Unchanged
        }
        if (!hasBase) return FieldOutcome.Conflict
        return when {
            local == base -> FieldOutcome.AutoTake(BangumiMergeSide.BANGUMI, remote)
            remote == base -> FieldOutcome.AutoTake(BangumiMergeSide.ANIMEKO, local)
            else -> FieldOutcome.Conflict
        }
    }

    private fun computeSubject(input: SubjectMergeInput): SubjectResult {
        val local = input.local
        val remote = input.remote
        val base = input.base
        val hasBase = base != null

        // 一侧未收藏时, 整个条目按整体处理 (与 BangumiMergeOpCompiler 的判定保持一致).
        val involvesNotCollected =
            local.collectionType == UnifiedCollectionType.NOT_COLLECTED ||
                remote.collectionType == UnifiedCollectionType.NOT_COLLECTED
        if (involvesNotCollected) {
            return computeWholeSubject(input)
        }

        val collectionOutcome = compareField(local.collectionType, remote.collectionType, base?.collectionType, hasBase)

        val conflicts = mutableListOf<BangumiMergeConflict>()
        val autoMerged = mutableListOf<AutoMergedChange>()
        val newerSideOfCollection = newerSide(local.collectionModifiedAt, remote.collectionModifiedAt)

        when (collectionOutcome) {
            is FieldOutcome.Unchanged -> {}
            is FieldOutcome.Consistent -> autoMerged.add(
                autoChange(input, BangumiMergeFieldId.Collection, AutoMergeReason.CONSISTENT, collectionOutcome.value),
            )

            is FieldOutcome.AutoTake -> autoMerged.add(
                autoChange(input, BangumiMergeFieldId.Collection, collectionOutcome.side.toReason(), collectionOutcome.value),
            )

            is FieldOutcome.Conflict -> conflicts.add(
                BangumiMergeConflict.Collection(
                    local = MergeSideValue(local.collectionType, local.collectionModifiedAt),
                    remote = MergeSideValue(remote.collectionType, remote.collectionModifiedAt),
                    newerSide = newerSideOfCollection,
                    baseType = base?.collectionType,
                ),
            )
        }

        // 剧集: 取三方 episodeId 并集, 按序号排序保证输出稳定.
        val episodeIds = buildSet {
            addAll(local.episodes.keys)
            addAll(remote.episodes.keys)
            base?.episodes?.keys?.let(::addAll)
        }.sortedWith(compareBy({ input.episodeSorts[it] }, { it }))

        for (episodeId in episodeIds) {
            val outcome = compareField(
                local.episodeType(episodeId),
                remote.episodeType(episodeId),
                base?.episodeType(episodeId),
                hasBase,
            )
            val fieldId = BangumiMergeFieldId.Episode(episodeId)
            when (outcome) {
                is FieldOutcome.Unchanged -> {}
                is FieldOutcome.Consistent -> autoMerged.add(
                    autoChange(input, fieldId, AutoMergeReason.CONSISTENT, outcome.value),
                )

                is FieldOutcome.AutoTake -> autoMerged.add(
                    autoChange(input, fieldId, outcome.side.toReason(), outcome.value),
                )

                is FieldOutcome.Conflict -> conflicts.add(
                    BangumiMergeConflict.Episode(
                        episodeId = episodeId,
                        sort = input.episodeSorts[episodeId],
                        local = MergeSideValue(local.episodeType(episodeId), local.episodeModifiedAt(episodeId)),
                        remote = MergeSideValue(remote.episodeType(episodeId), remote.episodeModifiedAt(episodeId)),
                        newerSide = newerSide(local.episodeModifiedAt(episodeId), remote.episodeModifiedAt(episodeId)),
                    ),
                )
            }
        }

        val scoreOutcome = compareField(local.score, remote.score, base?.score, hasBase)
        val commentOutcome = compareField(local.comment, remote.comment, base?.comment, hasBase)
        val scoreConflicts = scoreOutcome is FieldOutcome.Conflict
        val commentConflicts = commentOutcome is FieldOutcome.Conflict

        if (scoreConflicts) {
            // 短评同时冲突时并入评分一起解决.
            val absorbComment = commentConflicts
            conflicts.add(
                BangumiMergeConflict.Rating(
                    local = MergeSideValue(
                        MergeRatingValue(local.score, if (absorbComment) local.comment else null),
                        local.collectionModifiedAt,
                    ),
                    remote = MergeSideValue(
                        MergeRatingValue(remote.score, if (absorbComment) remote.comment else null),
                        remote.collectionModifiedAt,
                    ),
                    newerSide = newerSideOfCollection,
                    includesComment = absorbComment,
                ),
            )
        } else {
            when (scoreOutcome) {
                is FieldOutcome.Consistent -> autoMerged.add(
                    autoChange(input, BangumiMergeFieldId.Rating, AutoMergeReason.CONSISTENT, MergeRatingValue(scoreOutcome.value, null)),
                )

                is FieldOutcome.AutoTake -> autoMerged.add(
                    autoChange(input, BangumiMergeFieldId.Rating, scoreOutcome.side.toReason(), MergeRatingValue(scoreOutcome.value, null)),
                )

                else -> {}
            }
        }

        if (commentConflicts && !scoreConflicts) {
            conflicts.add(
                BangumiMergeConflict.Comment(
                    local = MergeSideValue(local.comment, local.collectionModifiedAt),
                    remote = MergeSideValue(remote.comment, remote.collectionModifiedAt),
                    newerSide = newerSideOfCollection,
                ),
            )
        } else if (!commentConflicts) {
            when (commentOutcome) {
                is FieldOutcome.Consistent -> autoMerged.add(
                    autoChange(input, BangumiMergeFieldId.Comment, AutoMergeReason.CONSISTENT, commentOutcome.value),
                )

                is FieldOutcome.AutoTake -> autoMerged.add(
                    autoChange(input, BangumiMergeFieldId.Comment, commentOutcome.side.toReason(), commentOutcome.value),
                )

                else -> {}
            }
        }

        // 输出顺序: 收藏状态 → 剧集 (按序号) → 评分 → 短评.
        val ordered = conflicts.sortedBy {
            when (it) {
                is BangumiMergeConflict.Collection -> 0
                is BangumiMergeConflict.Episode -> 1
                is BangumiMergeConflict.Rating -> 2
                is BangumiMergeConflict.Comment -> 3
            }
        }
        return SubjectResult(ordered, autoMerged)
    }

    /**
     * 整个条目按整体处理: 只产生一条收藏级别的冲突或自动合并.
     *
     * 注意, 一侧是否"有修改"以**完整快照的值语义**判断 (收藏状态 + 评分 + 短评 + 剧集进度):
     * 例如本地只看了几集新剧集而远端删除了收藏, 两侧都算有修改, 必须由用户确认,
     * 不能因为收藏状态本身没变就自动采用删除.
     */
    private fun computeWholeSubject(input: SubjectMergeInput): SubjectResult {
        val local = input.local
        val remote = input.remote
        val base = input.base

        fun conflict() = SubjectResult(
            listOf(
                BangumiMergeConflict.Collection(
                    local = MergeSideValue(local.collectionType, local.collectionModifiedAt),
                    remote = MergeSideValue(remote.collectionType, remote.collectionModifiedAt),
                    newerSide = newerSide(local.collectionModifiedAt, remote.collectionModifiedAt),
                    baseType = base?.collectionType,
                ),
            ),
            emptyList(),
        )

        fun auto(reason: AutoMergeReason, value: UnifiedCollectionType) = SubjectResult(
            emptyList(),
            listOf(
                autoChange(
                    input, BangumiMergeFieldId.Collection, reason, value,
                    isDestructive = value == UnifiedCollectionType.NOT_COLLECTED,
                ),
            ),
        )

        if (local.valuesEqual(remote)) {
            // 两侧一致 (归一化后, 一侧未收藏且相等意味着两侧都未收藏).
            return if (base != null && !local.valuesEqual(base)) {
                auto(AutoMergeReason.CONSISTENT, local.collectionType)
            } else {
                SubjectResult(emptyList(), emptyList())
            }
        }

        if (base == null) return conflict()
        val localChanged = !local.valuesEqual(base)
        val remoteChanged = !remote.valuesEqual(base)
        // 防御性规则: 单侧修改若是删除收藏 (破坏性), 也必须由用户确认, 不自动合并.
        // 即使上游快照有误 (如把缓存缺失误判为删除), 也不会静默丢失另一侧的收藏.
        return when {
            localChanged && remoteChanged -> conflict()
            remoteChanged ->
                if (remote.collectionType == UnifiedCollectionType.NOT_COLLECTED) conflict()
                else auto(AutoMergeReason.REMOTE_ONLY, remote.collectionType)

            else ->
                if (local.collectionType == UnifiedCollectionType.NOT_COLLECTED) conflict()
                else auto(AutoMergeReason.LOCAL_ONLY, local.collectionType)
        }
    }

    private fun autoChange(
        input: SubjectMergeInput,
        fieldId: BangumiMergeFieldId,
        reason: AutoMergeReason,
        mergedValue: Any?,
        isDestructive: Boolean = false,
    ): AutoMergedChange = AutoMergedChange(
        subjectId = input.subjectId,
        title = input.title,
        fieldId = fieldId,
        episodeSort = (fieldId as? BangumiMergeFieldId.Episode)?.let { input.episodeSorts[it.episodeId] },
        reason = reason,
        mergedValue = mergedValue,
        isDestructive = isDestructive,
    )

    private fun BangumiMergeSide.toReason(): AutoMergeReason = when (this) {
        BangumiMergeSide.ANIMEKO -> AutoMergeReason.LOCAL_ONLY
        BangumiMergeSide.BANGUMI -> AutoMergeReason.REMOTE_ONLY
    }

    private fun newerSide(localAt: Instant?, remoteAt: Instant?): BangumiMergeSide? {
        if (localAt == null || remoteAt == null) return null
        return when {
            localAt > remoteAt -> BangumiMergeSide.ANIMEKO
            remoteAt > localAt -> BangumiMergeSide.BANGUMI
            else -> null
        }
    }
}

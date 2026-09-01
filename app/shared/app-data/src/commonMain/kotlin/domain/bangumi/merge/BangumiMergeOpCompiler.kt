/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.bangumi.merge

import androidx.compose.runtime.Immutable
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 编译结果: 需要执行的写操作 + 每个条目合并后的最终状态 (用于写入新的同步基线).
 */
@Immutable
data class BangumiMergeCompileResult(
    val ops: List<BangumiMergeApplyOp>,
    val mergedStates: List<MergedSubjectState>,
)

/**
 * 将 [BangumiMergePlan] 与用户的 [BangumiMergeResolution] 编译为最终合并状态与需要执行的写操作.
 *
 * 编译规则:
 * - 最终状态 = 本地快照, 叠加所有决策为 Bangumi 侧的字段值 (冲突选择了 Bangumi, 或仅 Bangumi 侧修改的自动合并).
 * - 收藏状态差异涉及一侧未收藏时按整体处理: 采用被选中一侧的完整快照.
 * - 写操作 = 最终状态与任一侧当前值不同的字段. 所有写操作都通过现有仓库执行
 *   (客户端 → 服务器 → Bangumi 推送队列), 因此同一个操作会同时收敛两侧.
 *
 * 此类为纯函数, 不做任何 IO.
 */
class BangumiMergeOpCompiler {

    /**
     * @throws IllegalArgumentException 如果 [resolution] 缺少 [plan] 中任何冲突的选择.
     */
    fun compile(plan: BangumiMergePlan, resolution: BangumiMergeResolution): BangumiMergeCompileResult {
        val conflictsBySubject = plan.conflictGroups.associateBy { it.subjectId }
        for (group in plan.conflictGroups) {
            for (key in group.conflictKeys) {
                require(key in resolution.choices) { "未解决的冲突: $key" }
            }
        }
        val autoBySubject = plan.autoMerged.groupBy { it.subjectId }

        val ops = mutableListOf<BangumiMergeApplyOp>()
        val mergedStates = mutableListOf<MergedSubjectState>()

        for (input in plan.inputs) {
            val final = computeFinalSnapshot(
                input,
                conflictsBySubject[input.subjectId],
                autoBySubject[input.subjectId].orEmpty(),
                resolution,
            )
            ops.addAll(compileOps(input, final))
            mergedStates.add(MergedSubjectState(input.subjectId, final))
        }

        return BangumiMergeCompileResult(ops, mergedStates)
    }

    private fun computeFinalSnapshot(
        input: SubjectMergeInput,
        group: SubjectMergeConflictGroup?,
        auto: List<AutoMergedChange>,
        resolution: BangumiMergeResolution,
    ): SubjectMergeSnapshot {
        val local = input.local
        val remote = input.remote

        fun chosenSide(fieldId: BangumiMergeFieldId): BangumiMergeSide? =
            resolution.choices[BangumiMergeConflictKey(input.subjectId, fieldId)]

        // 整体处理: 收藏状态差异涉及一侧未收藏.
        val collectionConflict = group?.conflicts?.filterIsInstance<BangumiMergeConflict.Collection>()?.firstOrNull()
        val involvesNotCollected =
            local.collectionType == UnifiedCollectionType.NOT_COLLECTED ||
                remote.collectionType == UnifiedCollectionType.NOT_COLLECTED
        if (involvesNotCollected) {
            val chosenSnapshot: SubjectMergeSnapshot? = when {
                collectionConflict != null -> when (chosenSide(BangumiMergeFieldId.Collection)) {
                    BangumiMergeSide.ANIMEKO -> local
                    BangumiMergeSide.BANGUMI -> remote
                    null -> null // 已由 compile 校验, 不会发生
                }

                else -> auto.firstOrNull { it.fieldId == BangumiMergeFieldId.Collection }?.let {
                    when (it.reason) {
                        AutoMergeReason.LOCAL_ONLY -> local
                        AutoMergeReason.REMOTE_ONLY -> remote
                        // 一致更改: 两侧收藏状态相同. 若为都未收藏则取空快照, 否则不属于整体处理 (不会到这里).
                        AutoMergeReason.CONSISTENT -> if (local.collectionType == UnifiedCollectionType.NOT_COLLECTED) {
                            SubjectMergeSnapshot.NotCollected
                        } else {
                            local
                        }
                    }
                }
            }
            if (chosenSnapshot != null) {
                return if (chosenSnapshot.collectionType == UnifiedCollectionType.NOT_COLLECTED) {
                    SubjectMergeSnapshot.NotCollected
                } else {
                    chosenSnapshot.copy(collectionModifiedAt = newerInstant(local, remote))
                }
            }
            // 两侧收藏状态无差异 (例如都未收藏且无基线差异): 落到常规路径.
            if (local.collectionType == UnifiedCollectionType.NOT_COLLECTED &&
                remote.collectionType == UnifiedCollectionType.NOT_COLLECTED
            ) {
                return SubjectMergeSnapshot.NotCollected
            }
        }

        // 常规路径: 从本地快照出发, 叠加决策为 Bangumi 侧的字段.
        var collectionType = local.collectionType
        var score = local.score
        var comment = local.comment
        val episodes = local.episodes.toMutableMap()

        fun takeRemote(fieldId: BangumiMergeFieldId) {
            when (fieldId) {
                is BangumiMergeFieldId.Collection -> collectionType = remote.collectionType
                is BangumiMergeFieldId.Rating -> score = remote.score
                is BangumiMergeFieldId.Comment -> comment = remote.comment
                is BangumiMergeFieldId.Episode -> episodes[fieldId.episodeId] = remote.episodeType(fieldId.episodeId)
            }
        }

        for (change in auto) {
            if (change.reason == AutoMergeReason.REMOTE_ONLY) {
                takeRemote(change.fieldId)
            }
        }

        group?.conflicts?.forEach { conflict ->
            val side = chosenSide(conflict.id) ?: return@forEach
            if (side == BangumiMergeSide.BANGUMI) {
                takeRemote(conflict.id)
                // 短评并入评分冲突时一并采用.
                if (conflict is BangumiMergeConflict.Rating && conflict.includesComment) {
                    comment = remote.comment
                }
            } else if (conflict is BangumiMergeConflict.Rating && conflict.includesComment) {
                comment = local.comment
            }
        }

        return SubjectMergeSnapshot(
            collectionType = collectionType,
            score = score,
            comment = comment,
            episodes = episodes.filterValues { it != UnifiedCollectionType.NOT_COLLECTED },
            collectionModifiedAt = newerInstant(local, remote),
        )
    }

    private fun compileOps(
        input: SubjectMergeInput,
        final: SubjectMergeSnapshot,
    ): List<BangumiMergeApplyOp> {
        val local = input.local
        val remote = input.remote
        val subjectId = input.subjectId
        val ops = mutableListOf<BangumiMergeApplyOp>()

        val typeDiffers = final.collectionType != local.collectionType || final.collectionType != remote.collectionType
        if (typeDiffers) {
            ops.add(BangumiMergeApplyOp.SetSubjectCollection(subjectId, final.collectionType))
        }

        if (final.collectionType == UnifiedCollectionType.NOT_COLLECTED) {
            // 删除收藏后无需再同步评分与剧集.
            return ops
        }

        val ratingDiffers = final.score != local.score || final.comment != local.comment ||
            final.score != remote.score || final.comment != remote.comment
        if (ratingDiffers) {
            ops.add(BangumiMergeApplyOp.UpdateRating(subjectId, final.score, final.comment))
        }

        val episodeIds = buildSet {
            addAll(local.episodes.keys)
            addAll(remote.episodes.keys)
            addAll(final.episodes.keys)
        }.sortedWith(compareBy({ input.episodeSorts[it] }, { it }))
        for (episodeId in episodeIds) {
            val finalType = final.episodeType(episodeId)
            if (finalType != local.episodeType(episodeId) || finalType != remote.episodeType(episodeId)) {
                ops.add(BangumiMergeApplyOp.SetEpisodeCollection(subjectId, episodeId, finalType))
            }
        }

        return ops
    }

    private fun newerInstant(local: SubjectMergeSnapshot, remote: SubjectMergeSnapshot) =
        listOfNotNull(local.collectionModifiedAt, remote.collectionModifiedAt).maxOrNull()
}

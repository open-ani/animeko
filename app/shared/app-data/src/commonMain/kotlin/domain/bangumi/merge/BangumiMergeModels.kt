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
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

/**
 * 参与合并的一侧.
 *
 * 合并总是发生在本地 (Animeko) 与远端 (Bangumi) 两侧之间.
 */
enum class BangumiMergeSide {
    /** Animeko 本地状态 */
    ANIMEKO,

    /** Bangumi 远端状态 */
    BANGUMI,
}

/**
 * 一个条目在某一侧 (或上次同步基线) 的收藏快照.
 *
 * 这是合并引擎的输入. 所有字段都是"当前值", 与另一侧及基线快照逐字段对比后得出冲突或自动合并结果.
 *
 * @see BangumiMergePlanComputer
 */
@Immutable
data class SubjectMergeSnapshot(
    /**
     * 收藏状态. [UnifiedCollectionType.NOT_COLLECTED] 表示此侧未收藏 (或已删除收藏).
     */
    val collectionType: UnifiedCollectionType,
    /**
     * 自己的评分. `0` 表示未评分.
     */
    val score: Int,
    /**
     * 自己的短评. `null` 表示无短评.
     */
    val comment: String?,
    /**
     * 各剧集的收藏状态, key 为 episodeId. 缺失的剧集视为 [UnifiedCollectionType.NOT_COLLECTED].
     */
    val episodes: Map<Int, UnifiedCollectionType>,
    /**
     * 条目级字段 (收藏状态/评分/短评) 在此侧的最后修改时间. `null` 表示未知.
     *
     * 用于 UI 的 ● 较新标记与"采用较新的"批量操作.
     */
    val collectionModifiedAt: Instant?,
    /**
     * 剧集级修改时间, key 为 episodeId. 缺失时回退到 [collectionModifiedAt].
     */
    val episodeModifiedAt: Map<Int, Instant?> = emptyMap(),
) {
    fun episodeType(episodeId: Int): UnifiedCollectionType =
        episodes[episodeId] ?: UnifiedCollectionType.NOT_COLLECTED

    fun episodeModifiedAt(episodeId: Int): Instant? =
        episodeModifiedAt[episodeId] ?: collectionModifiedAt

    /**
     * 归一化: 未收藏的快照不携带任何残留字段 (评分/短评/剧集), 剧集表中不保留 NOT_COLLECTED 条目.
     *
     * 合并引擎在入口统一做归一化, 保证冲突计算与应用编译对同一条目使用一致的判定.
     */
    fun normalized(): SubjectMergeSnapshot {
        if (collectionType == UnifiedCollectionType.NOT_COLLECTED) {
            return NotCollected.copy(collectionModifiedAt = collectionModifiedAt)
        }
        val filtered = episodes.filterValues { it != UnifiedCollectionType.NOT_COLLECTED }
        return if (filtered.size == episodes.size) this else copy(episodes = filtered)
    }

    /**
     * 值语义相等: 只比较收藏状态/评分/短评/剧集, 忽略修改时间.
     */
    fun valuesEqual(other: SubjectMergeSnapshot): Boolean =
        collectionType == other.collectionType &&
            score == other.score &&
            comment == other.comment &&
            episodes == other.episodes

    companion object {
        /**
         * 此侧未收藏该条目时的快照.
         */
        val NotCollected = SubjectMergeSnapshot(
            collectionType = UnifiedCollectionType.NOT_COLLECTED,
            score = 0,
            comment = null,
            episodes = emptyMap(),
            collectionModifiedAt = null,
        )
    }
}

/**
 * 合并引擎对单个条目的输入: 两侧快照 + 上次同步基线 + 展示所需的元数据.
 */
@Immutable
data class SubjectMergeInput(
    val subjectId: Int,
    /**
     * 展示用的条目标题 (优先中文).
     */
    val title: String,
    val local: SubjectMergeSnapshot,
    val remote: SubjectMergeSnapshot,
    /**
     * 上次同步完成时的合并基线. `null` 表示没有基线 (如首次合并),
     * 此时任何两侧不一致的字段都会成为冲突, 由用户决定.
     */
    val base: SubjectMergeSnapshot?,
    /**
     * 剧集序号, 用于展示 "EP7" 等. key 为 episodeId.
     */
    val episodeSorts: Map<Int, EpisodeSort> = emptyMap(),
) {
    /**
     * @see SubjectMergeSnapshot.normalized
     */
    fun normalized(): SubjectMergeInput {
        val local = local.normalized()
        val remote = remote.normalized()
        val base = base?.normalized()
        return if (local === this.local && remote === this.remote && base === this.base) {
            this
        } else {
            copy(local = local, remote = remote, base = base)
        }
    }
}

/**
 * 条目内一个可冲突字段的标识.
 */
@Immutable
@Serializable
sealed class BangumiMergeFieldId {
    /**
     * 收藏状态, 包括收藏类型变化与删除收藏 ([UnifiedCollectionType.NOT_COLLECTED]).
     */
    @Serializable
    data object Collection : BangumiMergeFieldId()

    /**
     * 评分. 当短评也同时冲突时, 短评会并入此字段一起解决.
     */
    @Serializable
    data object Rating : BangumiMergeFieldId()

    /**
     * 仅短评冲突 (评分无冲突).
     */
    @Serializable
    data object Comment : BangumiMergeFieldId()

    /**
     * 单个剧集的收藏状态 (观看进度).
     */
    @Serializable
    data class Episode(val episodeId: Int) : BangumiMergeFieldId()
}

/**
 * 字段在一侧的取值与修改时间.
 */
@Immutable
data class MergeSideValue<out T>(
    val value: T,
    /**
     * 此侧此字段的最后修改时间. `null` 表示未知 (UI 不显示 ● 较新标记).
     */
    val modifiedAt: Instant?,
)

/**
 * 评分字段的取值. 当短评与评分同时冲突时, [comment] 会并入评分字段一起展示与解决.
 */
@Immutable
data class MergeRatingValue(
    val score: Int,
    /**
     * 并入此字段解决的短评. `null` 表示短评不参与此字段 (无短评或短评单独/自动解决).
     */
    val comment: String?,
)

/**
 * 需要用户确认的一个字段冲突: 两侧都相对基线做出了不同的修改.
 */
@Immutable
sealed class BangumiMergeConflict {
    abstract val id: BangumiMergeFieldId

    /**
     * 较新的一侧. 两侧修改时间都已知且不相等时非 `null`.
     */
    abstract val newerSide: BangumiMergeSide?

    @Immutable
    data class Collection(
        val local: MergeSideValue<UnifiedCollectionType>,
        val remote: MergeSideValue<UnifiedCollectionType>,
        override val newerSide: BangumiMergeSide?,
        /**
         * 基线收藏状态, 用于 UI 展示 "想看 → 在看" 式的变化. `null` 表示无基线.
         */
        val baseType: UnifiedCollectionType? = null,
    ) : BangumiMergeConflict() {
        override val id get() = BangumiMergeFieldId.Collection
    }

    @Immutable
    data class Rating(
        val local: MergeSideValue<MergeRatingValue>,
        val remote: MergeSideValue<MergeRatingValue>,
        override val newerSide: BangumiMergeSide?,
        /**
         * 短评是否并入此字段一起解决 (评分与短评同时冲突时为 `true`).
         * 为 `false` 时选择只影响评分, 不改变短评.
         */
        val includesComment: Boolean = false,
    ) : BangumiMergeConflict() {
        override val id get() = BangumiMergeFieldId.Rating
    }

    @Immutable
    data class Comment(
        val local: MergeSideValue<String?>,
        val remote: MergeSideValue<String?>,
        override val newerSide: BangumiMergeSide?,
    ) : BangumiMergeConflict() {
        override val id get() = BangumiMergeFieldId.Comment
    }

    @Immutable
    data class Episode(
        val episodeId: Int,
        /**
         * 剧集序号, 用于展示 "EP7". `null` 表示未知.
         */
        val sort: EpisodeSort?,
        val local: MergeSideValue<UnifiedCollectionType>,
        val remote: MergeSideValue<UnifiedCollectionType>,
        override val newerSide: BangumiMergeSide?,
    ) : BangumiMergeConflict() {
        override val id get() = BangumiMergeFieldId.Episode(episodeId)
    }

    /**
     * 选择一侧后, 此冲突是否会导致收藏被删除等破坏性结果. UI 用 error 色警示.
     */
    fun isDestructive(side: BangumiMergeSide): Boolean = when (this) {
        is Collection -> sideValue(side).value == UnifiedCollectionType.NOT_COLLECTED
        else -> false
    }
}

fun BangumiMergeConflict.Collection.sideValue(side: BangumiMergeSide): MergeSideValue<UnifiedCollectionType> =
    when (side) {
        BangumiMergeSide.ANIMEKO -> local
        BangumiMergeSide.BANGUMI -> remote
    }

/**
 * 一个条目的全部冲突.
 */
@Immutable
data class SubjectMergeConflictGroup(
    val subjectId: Int,
    val title: String,
    val conflicts: List<BangumiMergeConflict>,
)

/**
 * 自动合并 (无需用户确认) 的一项差异的来源.
 */
enum class AutoMergeReason {
    /**
     * 两侧做出了一致的更改 (最终值相同), 无需写入任何一侧, 仅更新基线.
     */
    CONSISTENT,

    /**
     * 仅 Animeko 侧相对基线有修改, 自动采用 Animeko 侧.
     */
    LOCAL_ONLY,

    /**
     * 仅 Bangumi 侧相对基线有修改, 自动采用 Bangumi 侧.
     */
    REMOTE_ONLY,
}

/**
 * 已自动合并的一项差异, 用于底部可展开的审计明细.
 */
@Immutable
data class AutoMergedChange(
    val subjectId: Int,
    val title: String,
    val fieldId: BangumiMergeFieldId,
    /**
     * 剧集序号 (仅 [BangumiMergeFieldId.Episode]).
     */
    val episodeSort: EpisodeSort?,
    val reason: AutoMergeReason,
    /**
     * 合并后的最终取值描述, 供 UI 展示. 收藏状态用 [UnifiedCollectionType], 评分用 [MergeRatingValue], 短评用 [String].
     */
    val mergedValue: Any?,
    /**
     * 此项自动合并是否会删除收藏等破坏性结果.
     */
    val isDestructive: Boolean = false,
)

/**
 * 合并引擎的完整输出: 需要用户确认的冲突 + 已自动合并的差异.
 */
@Immutable
data class BangumiMergePlan(
    val conflictGroups: List<SubjectMergeConflictGroup>,
    val autoMerged: List<AutoMergedChange>,
    /**
     * 引擎输入, 供 [BangumiMergeResolution] 应用时计算最终状态.
     */
    val inputs: List<SubjectMergeInput>,
) {
    val totalConflictCount: Int = conflictGroups.sumOf { it.conflicts.size }

    val isEmpty: Boolean get() = conflictGroups.isEmpty() && autoMerged.isEmpty()

    companion object {
        val Empty = BangumiMergePlan(emptyList(), emptyList(), emptyList())
    }
}

/**
 * 冲突的全局唯一标识: 条目 + 字段.
 */
@Immutable
@Serializable
data class BangumiMergeConflictKey(
    val subjectId: Int,
    val fieldId: BangumiMergeFieldId,
)

val SubjectMergeConflictGroup.conflictKeys: List<BangumiMergeConflictKey>
    get() = conflicts.map { BangumiMergeConflictKey(subjectId, it.id) }

/**
 * 用户对全部冲突的选择.
 */
@Immutable
data class BangumiMergeResolution(
    val choices: Map<BangumiMergeConflictKey, BangumiMergeSide>,
) {
    companion object {
        val Empty = BangumiMergeResolution(emptyMap())
    }
}

/**
 * 应用合并时需要执行的一个写操作. 由 [BangumiMergeOpCompiler] 从最终合并状态编译得出.
 *
 * 所有写操作都通过现有仓库执行 (客户端 → 服务器 → Bangumi 推送队列), 因此天然同时收敛两侧.
 */
@Immutable
sealed class BangumiMergeApplyOp {
    abstract val subjectId: Int

    /**
     * 设置收藏状态. [type] 为 [UnifiedCollectionType.NOT_COLLECTED] 表示删除收藏.
     */
    @Immutable
    data class SetSubjectCollection(
        override val subjectId: Int,
        val type: UnifiedCollectionType,
    ) : BangumiMergeApplyOp()

    /**
     * 更新评分或短评.
     */
    @Immutable
    data class UpdateRating(
        override val subjectId: Int,
        val score: Int,
        val comment: String?,
    ) : BangumiMergeApplyOp()

    /**
     * 设置剧集收藏状态.
     */
    @Immutable
    data class SetEpisodeCollection(
        override val subjectId: Int,
        val episodeId: Int,
        val type: UnifiedCollectionType,
    ) : BangumiMergeApplyOp()
}

/**
 * 合并完成后的一个条目最终状态, 用于写入新的同步基线.
 */
@Immutable
data class MergedSubjectState(
    val subjectId: Int,
    val snapshot: SubjectMergeSnapshot,
)

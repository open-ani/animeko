/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.dao.BangumiMergeBaselineDao
import me.him188.ani.app.data.persistent.database.dao.BangumiMergeBaselineEntity
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeApplyOp
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeCompileResult
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeOpCompiler
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlanComputer
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeResolution
import me.him188.ani.app.domain.bangumi.merge.SubjectMergeInput
import me.him188.ani.app.domain.bangumi.merge.SubjectMergeSnapshot
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * Bangumi 合并 (冲突处理) 仓库.
 *
 * 负责:
 * 1. 构建三方快照 (本地 Room 状态 / 远端 Bangumi 状态 / 上次同步基线), 计算 [BangumiMergePlan];
 * 2. 应用用户确认后的合并结果, 并写入新基线.
 */
abstract class BangumiMergeRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    /**
     * 拉取远端状态并与本地状态、同步基线对比, 计算合并计划.
     *
     * 网络失败时抛出异常 (由调用方转换为 LoadError).
     */
    abstract suspend fun computeMergePlan(): BangumiMergePlan

    /**
     * 应用合并: 依次执行写操作, 全部成功后写入新的同步基线.
     *
     * @throws IllegalArgumentException 如果 [resolution] 缺少任何冲突的选择.
     */
    abstract suspend fun applyMerge(
        plan: BangumiMergePlan,
        resolution: BangumiMergeResolution,
    ): BangumiMergeCompileResult
}

/**
 * 合并写操作的执行出口. 所有写操作都通过现有仓库执行
 * (客户端 → 服务器 → Bangumi 推送队列), 因此同一个操作会同时收敛两侧.
 */
interface BangumiMergeWriteGateway {
    /**
     * 设置收藏状态. [type] 为 `null` 表示删除收藏.
     */
    suspend fun setSubjectCollection(subjectId: Int, type: UnifiedCollectionType?)

    /**
     * 更新评分与短评. [comment] 为 `null` 表示删除短评.
     */
    suspend fun updateRating(subjectId: Int, score: Int, comment: String?)

    suspend fun setEpisodeCollection(subjectId: Int, episodeId: Int, type: UnifiedCollectionType)
}

class DefaultBangumiMergeWriteGateway(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectService: SubjectService,
) : BangumiMergeWriteGateway {
    override suspend fun setSubjectCollection(subjectId: Int, type: UnifiedCollectionType?) {
        subjectCollectionRepository.setSubjectCollectionTypeOrDelete(subjectId, type)
    }

    override suspend fun updateRating(subjectId: Int, score: Int, comment: String?) {
        // 合并只决定 score 与 comment, 但 patch 接口是整体覆盖语义:
        // tags 与 isPrivate 必须回读当前值原样带上, 否则会被清空.
        val current = subjectCollectionDao.findById(subjectId).first()?.selfRatingInfo
            ?: subjectService.getSubjectCollection(subjectId)?.selfRating?.toSelfRatingInfo()
        // updateRating 语义: score 0 表示删除评分; 空字符串表示删除短评, null 表示不修改.
        subjectCollectionRepository.updateRating(
            subjectId,
            score = score,
            comment = comment ?: "",
            tags = current?.tags.orEmpty(),
            isPrivate = current?.isPrivate ?: false,
        )
    }

    override suspend fun setEpisodeCollection(subjectId: Int, episodeId: Int, type: UnifiedCollectionType) {
        val accepted = episodeCollectionRepository.setEpisodeCollectionType(subjectId, episodeId, type)
        // 写入被服务器拒绝 (会话失效 / 条目未收藏) 时必须失败,
        // 否则基线会被当作已收敛保存, 该修改将永远丢失.
        check(accepted) {
            "Server rejected episode collection update for subject $subjectId episode $episodeId"
        }
    }
}

/**
 * ## 快照口径
 *
 * - 剧集进度统一归一化为 "看过 (DONE) / 未看": 服务器侧剧集收藏只支持 DONE,
 *   其他状态无法往返同步, 若保留会产生永远无法收敛的伪冲突.
 * - 空短评 (`""`) 归一化为 `null`.
 */
class DefaultBangumiMergeRepository(
    private val subjectService: SubjectService,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val baselineDao: BangumiMergeBaselineDao,
    private val writeGateway: BangumiMergeWriteGateway,
    private val getCurrentTimeMillis: () -> Long = { currentTimeMillis() },
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : BangumiMergeRepository(defaultDispatcher) {
    private val computer = BangumiMergePlanComputer()
    private val compiler = BangumiMergeOpCompiler()

    override suspend fun computeMergePlan(): BangumiMergePlan = withContext(defaultDispatcher) {
        val localSubjects = subjectCollectionDao.getAllList()
        val localEpisodes = episodeCollectionDao.all().first().groupBy { it.subjectId }
        val remoteSubjects = fetchAllRemoteCollections()
        val baselines = baselineDao.getAll().associateBy { it.subjectId }

        val subjectIds = buildSet {
            localSubjects.forEach { add(it.subjectId) }
            remoteSubjects.forEach { add(it.id.toInt()) }
            // 基线中有而两侧都没有的条目已经在两侧都删除, 无需参与合并.
        }

        val localById = localSubjects.associateBy { it.subjectId }
        val remoteById = remoteSubjects.associateBy { it.id.toInt() }

        val inputs = subjectIds.sorted().map { subjectId ->
            val local = localById[subjectId]
            val remote = remoteById[subjectId]
            val episodes = localEpisodes[subjectId].orEmpty()

            val remoteSnapshot = remote?.toMergeSnapshot() ?: SubjectMergeSnapshot.NotCollected

            val baseSnapshot = baselines[subjectId]?.toMergeSnapshot()

            // 本地 Room 表是分页缓存 (RemoteMediator 刷新时清空重填, 且只填已加载的页):
            // "行不存在" 只说明缓存未加载, 不代表用户删除了收藏或取消了看过.
            // 所有写操作都是 network-first, 本地不可能领先服务器, 因此:
            // - 条目行缺失时, 本地视为与基线一致 (无基线则视为与远端一致), 绝不由缺失推断出删除;
            // - 条目行存在但剧集行缺失时, 该剧集回退基线的值 (无基线则回退远端的值).
            val localSnapshot = local?.let { entity ->
                val observedEpisodeIds = episodes.mapTo(mutableSetOf()) { it.episodeId }
                val episodeFallback = (baseSnapshot ?: remoteSnapshot).episodes
                    .filterKeys { it !in observedEpisodeIds }
                SubjectMergeSnapshot(
                    collectionType = entity.collectionType,
                    score = entity.selfRatingInfo.score,
                    comment = entity.selfRatingInfo.comment.normalizeComment(),
                    episodes = episodes
                        .filter { it.selfCollectionType == UnifiedCollectionType.DONE }
                        .associate { it.episodeId to UnifiedCollectionType.DONE } + episodeFallback,
                    collectionModifiedAt = entity.lastUpdated.takeIf { it > 0 }
                        ?.let(Instant::fromEpochMilliseconds),
                )
            } ?: baseSnapshot ?: remoteSnapshot

            val episodeSorts = buildMap {
                remote?.episodes?.forEach { put(it.episodeId.toInt(), EpisodeSort(it.sort)) }
                episodes.forEach { put(it.episodeId, it.sort) }
            }

            SubjectMergeInput(
                subjectId = subjectId,
                title = local?.nameCn?.takeIf { it.isNotBlank() }
                    ?: local?.name?.takeIf { it.isNotBlank() }
                    ?: remote?.nameCn?.takeIf { it.isNotBlank() }
                    ?: remote?.name?.takeIf { it.isNotBlank() }
                    ?: subjectId.toString(),
                local = localSnapshot,
                remote = remoteSnapshot,
                base = baseSnapshot,
                episodeSorts = episodeSorts,
            )
        }

        computer.compute(inputs)
    }

    override suspend fun applyMerge(
        plan: BangumiMergePlan,
        resolution: BangumiMergeResolution,
    ): BangumiMergeCompileResult = withContext(defaultDispatcher) {
        val result = compiler.compile(plan, resolution)

        for (op in result.ops) {
            when (op) {
                is BangumiMergeApplyOp.SetSubjectCollection ->
                    writeGateway.setSubjectCollection(
                        op.subjectId,
                        op.type.takeIf { it != UnifiedCollectionType.NOT_COLLECTED },
                    )

                is BangumiMergeApplyOp.UpdateRating ->
                    writeGateway.updateRating(op.subjectId, op.score, op.comment)

                is BangumiMergeApplyOp.SetEpisodeCollection ->
                    writeGateway.setEpisodeCollection(op.subjectId, op.episodeId, op.type)
            }
        }

        saveBaseline(result)
        result
    }

    private suspend fun saveBaseline(result: BangumiMergeCompileResult) {
        val now = getCurrentTimeMillis()
        baselineDao.replaceAll(
            result.mergedStates.map { state ->
                BangumiMergeBaselineEntity(
                    subjectId = state.subjectId,
                    collectionType = state.snapshot.collectionType,
                    score = state.snapshot.score,
                    comment = state.snapshot.comment,
                    watchedEpisodeIds = state.snapshot.episodes
                        .filterValues { it == UnifiedCollectionType.DONE }
                        .keys.sorted(),
                    updatedAtMillis = now,
                )
            },
        )
    }

    private suspend fun fetchAllRemoteCollections(): List<AniSubjectCollection> {
        val result = mutableListOf<AniSubjectCollection>()
        var offset = 0
        while (true) {
            val page = subjectService.getSubjectCollections(
                type = null,
                offset = offset,
                limit = REMOTE_PAGE_SIZE,
            )
            // 以空页为终止条件: 服务器可能将单页上限压到比请求的 limit 小,
            // 若以 page.size < limit 终止会静默截断.
            if (page.isEmpty()) break
            result.addAll(page)
            offset += page.size
        }
        return result
    }

    private fun AniSubjectCollection.toMergeSnapshot(): SubjectMergeSnapshot {
        val type = collectionType.toUnifiedCollectionType()
        if (type == UnifiedCollectionType.NOT_COLLECTED) return SubjectMergeSnapshot.NotCollected
        return SubjectMergeSnapshot(
            collectionType = type,
            score = selfRating.score,
            comment = selfRating.comment.normalizeComment(),
            episodes = episodes
                .filter { it.collectionType == AniEpisodeCollectionType.DONE }
                .associate { it.episodeId.toInt() to UnifiedCollectionType.DONE },
            // 时间戳只用于 "较新" 标记, 解析失败时宁可缺失也不让整个合并计划失败.
            collectionModifiedAt = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private fun BangumiMergeBaselineEntity.toMergeSnapshot(): SubjectMergeSnapshot {
        if (collectionType == UnifiedCollectionType.NOT_COLLECTED) return SubjectMergeSnapshot.NotCollected
        return SubjectMergeSnapshot(
            collectionType = collectionType,
            score = score,
            comment = comment.normalizeComment(),
            episodes = watchedEpisodeIds.associateWith { UnifiedCollectionType.DONE },
            collectionModifiedAt = null,
        )
    }

    private fun String?.normalizeComment(): String? = this?.takeIf { it.isNotEmpty() }

    private companion object {
        const val REMOTE_PAGE_SIZE = 100
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.bangumi.BangumiAutoMergedChange
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiMergeSummary
import me.him188.ani.app.data.models.bangumi.BangumiSubjectConflict
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.client.models.AniBangumiAutoMerged
import me.him188.ani.client.models.AniBangumiConflictFieldType
import me.him188.ani.client.models.AniBangumiConflictResolution
import me.him188.ani.client.models.AniBangumiMergeSide
import me.him188.ani.client.models.AniBangumiMergeState
import me.him188.ani.client.models.AniBangumiMergeSummary
import me.him188.ani.client.models.AniBangumiSyncConflict
import me.him188.ani.client.models.AniBangumiSyncConflictField
import me.him188.ani.client.models.AniResolveMergeConflictsRequest
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * [BangumiMergeRepository] 的默认实现, 使用服务端 `/bangumi/sync/merge*` 接口 (生成的 [BangumiAniApi]).
 *
 * [resolve] 成功后对涉及的条目调用 [SubjectCollectionRepository.invalidateCache]: 这些条目在服务端的值已经改变, 本地缓存不再可信.
 */
class DefaultBangumiMergeRepository(
    private val bangumiApi: ApiInvoker<BangumiAniApi>,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : BangumiMergeRepository(defaultDispatcher) {
    override suspend fun getSummary(): BangumiMergeSummary = withContext(defaultDispatcher) {
        try {
            bangumiApi.invoke { getMergeSummary().body() }.toModel()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun getMergeState(): BangumiMergeState = withContext(defaultDispatcher) {
        try {
            bangumiApi.invoke { getMergeState().body() }.toModel()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun resolve(resolutions: List<BangumiConflictResolution>): BangumiMergeState =
        withContext(defaultDispatcher) {
            val remaining = try {
                bangumiApi.invoke {
                    resolveMergeConflicts(
                        AniResolveMergeConflictsRequest(
                            resolutions = resolutions.map { it.toEntity() },
                        ),
                    ).body()
                }.toModel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Conflict) {
                    throw BangumiMergeSyncInProgressException(cause = e)
                }
                throw RepositoryException.wrapOrThrowCancellation(e)
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }

            // 服务端已改写这些条目, 本地缓存失效. 失效失败不影响合并结果, 只记日志.
            val touched = resolutions.map { it.subjectId }.distinct()
            if (touched.isNotEmpty()) {
                try {
                    subjectCollectionRepository.invalidateCache(touched)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to invalidate subject collection cache after resolving Bangumi conflicts" }
                }
            }
            remaining
        }
}

// ─── 服务端实体 → 客户端模型 ────────────────────────────────────────────────

internal fun AniBangumiMergeSummary.toModel(): BangumiMergeSummary = BangumiMergeSummary(
    conflictCount = conflictCount,
    autoMergedTotal = autoMergedTotal,
    lastSyncedAt = lastSyncedAt.toInstantOrNull(),
    syncInProgress = syncInProgress,
)

internal fun AniBangumiMergeState.toModel(): BangumiMergeState = BangumiMergeState(
    conflicts = conflicts.mapNotNull { it.toModel() },
    autoMerged = autoMerged.map { it.toModel() },
    autoMergedTotal = autoMergedTotal,
    lastSyncedAt = lastSyncedAt.toInstantOrNull(),
    syncInProgress = syncInProgress,
)

/**
 * @return `null` 表示该条目没有客户端能展示的冲突字段 (如服务端新增了未知字段类型)
 */
internal fun AniBangumiSyncConflict.toModel(): BangumiSubjectConflict? {
    val fields = fields.mapNotNull { it.toModel() }
    if (fields.isEmpty()) return null
    return BangumiSubjectConflict(
        subjectId = subjectId.toInt(),
        title = displayTitle(subjectId, subjectName, subjectNameCn),
        animekoUpdatedAt = animekoUpdatedAt.toInstantOrNull(),
        bangumiUpdatedAt = bangumiUpdatedAt.toInstantOrNull(),
        detectedAt = detectedAt.toInstantOrNull() ?: Instant.fromEpochMilliseconds(0),
        fields = fields,
    )
}

internal fun AniBangumiSyncConflictField.toModel(): BangumiConflictField? = when (fieldType) {
    AniBangumiConflictFieldType.COLLECTION -> BangumiConflictField.Collection(
        animeko = animekoCollectionType?.toUnifiedCollectionType() ?: UnifiedCollectionType.NOT_COLLECTED,
        bangumi = bangumiCollectionType?.toUnifiedCollectionType() ?: UnifiedCollectionType.NOT_COLLECTED,
    )

    AniBangumiConflictFieldType.RATING -> {
        val animeko = animekoRating ?: return null
        val bangumi = bangumiRating ?: return null
        BangumiConflictField.Rating(
            animeko = animeko.toSelfRatingInfo(),
            bangumi = bangumi.toSelfRatingInfo(),
        )
    }

    AniBangumiConflictFieldType.EPISODE -> null // 服务端不会产生剧集冲突
}

internal fun AniBangumiAutoMerged.toModel(): BangumiAutoMergedChange = BangumiAutoMergedChange(
    subjectId = subjectId.toInt(),
    title = displayTitle(subjectId, subjectName, subjectNameCn),
    fieldType = fieldType.toModel(),
    side = side.toModel(),
    isNew = isNew,
    collectionType = collectionType?.toUnifiedCollectionType(),
    rating = rating?.toSelfRatingInfo(),
    watchedEpisodeSorts = watchedEpisodeSorts.orEmpty(),
    unwatchedEpisodeSorts = unwatchedEpisodeSorts.orEmpty(),
)

internal fun AniBangumiConflictFieldType.toModel(): BangumiConflictFieldType = when (this) {
    AniBangumiConflictFieldType.COLLECTION -> BangumiConflictFieldType.COLLECTION
    AniBangumiConflictFieldType.RATING -> BangumiConflictFieldType.RATING
    AniBangumiConflictFieldType.EPISODE -> BangumiConflictFieldType.EPISODE
}

internal fun BangumiConflictFieldType.toEntity(): AniBangumiConflictFieldType = when (this) {
    BangumiConflictFieldType.COLLECTION -> AniBangumiConflictFieldType.COLLECTION
    BangumiConflictFieldType.RATING -> AniBangumiConflictFieldType.RATING
    BangumiConflictFieldType.EPISODE -> AniBangumiConflictFieldType.EPISODE
}

internal fun AniBangumiMergeSide.toModel(): BangumiMergeSide = when (this) {
    AniBangumiMergeSide.ANIMEKO -> BangumiMergeSide.ANIMEKO
    AniBangumiMergeSide.BANGUMI -> BangumiMergeSide.BANGUMI
}

internal fun BangumiMergeSide.toEntity(): AniBangumiMergeSide = when (this) {
    BangumiMergeSide.ANIMEKO -> AniBangumiMergeSide.ANIMEKO
    BangumiMergeSide.BANGUMI -> AniBangumiMergeSide.BANGUMI
}

internal fun BangumiConflictResolution.toEntity(): AniBangumiConflictResolution = AniBangumiConflictResolution(
    subjectId = subjectId.toLong(),
    fieldType = fieldType.toEntity(),
    side = side.toEntity(),
)

/**
 * 展示标题: 优先中文名, 其次原名, 条目库中没有该条目时为 `#subjectId`.
 */
internal fun displayTitle(subjectId: Long, name: String?, nameCn: String?): String =
    nameCn?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "#$subjectId"

/**
 * 服务端的 Instant 在生成的客户端里是 ISO-8601 字符串. 解析失败 (格式意外) 视为未知时间.
 */
internal fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null
    return try {
        Instant.parse(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

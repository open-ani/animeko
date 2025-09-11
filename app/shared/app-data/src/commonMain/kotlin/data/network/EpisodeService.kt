/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.data.repository.subject.toEntity1
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.canAccessAniApiNow
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniBatchUpdateEpisodeCollectionsRequest
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeCollectionTypeUpdate
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.EpisodeType.*
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiEpType
import me.him188.ani.datasources.bangumi.models.BangumiEpisode
import me.him188.ani.datasources.bangumi.models.BangumiEpisodeDetail
import me.him188.ani.datasources.bangumi.models.BangumiUserEpisodeCollection
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.BigNum
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

/**
 * 执行网络请求查询.
 */
sealed interface EpisodeService {
    /**
     * 获取用户在这个条目下的所有剧集的收藏状态. 当用户没有收藏此条目时返回 [collectionType] 均为 [UnifiedCollectionType.NOT_COLLECTED].
     *
     * @return 分页的剧集收藏信息.
     */
    suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int? = 0,
        limit: Int? = 100,
        episodeType: BangumiEpType? = null,
    ): Paged<EpisodeCollectionInfo>

    /**
     * 获取单个剧集的信息和用户的收藏状态. 如果用户没有收藏这个剧集所属的条目, 则返回 [collectionType] 为 [UnifiedCollectionType.NOT_COLLECTED].
     *
     * 只有在 [episodeId] 找不到对应的公开剧集时返回 `null`.
     */
    suspend fun getEpisodeCollectionById(subjectId: Int, episodeId: Int): EpisodeCollectionInfo?

    /**
     * 设置多个剧集的收藏状态.
     *
     * 当设置成功时返回 `true`. 返回 `false` 表示用户没有收藏这个条目. 其他异常将会抛出.
     */
    suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType,
    ): Boolean
}

class EpisodeServiceImpl(
    private val subjectApi: ApiInvoker<SubjectsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : EpisodeService, KoinComponent {
    private val logger = logger<EpisodeServiceImpl>()
    private val sessionManager: SessionStateProvider by inject()

    override suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int?,
        limit: Int?,
        episodeType: BangumiEpType?,
    ): Paged<EpisodeCollectionInfo> {
        return withContext(ioDispatcher) {

            subjectApi.invoke {
                this.getSubject(subjectId.toLong()).body() // TODO: 2025/6/15 API 不支持 paging 
            }.let { subjectCollection ->
                Paged(
                    subjectCollection.episodes.map {
                        it.toEntity1(subjectId, lastFetched = currentTimeMillis())
                            .toEpisodeCollectionInfo()
                    },
                )
            }
//                .run {
////                    Paged.processPagedResponse(total, limit ?: 100, data)
////                }.map {
////                    it.toEpisodeCollectionInfo()
////                }
        }
    }


    override suspend fun getEpisodeCollectionById(subjectId: Int, episodeId: Int): EpisodeCollectionInfo? =
        withContext(ioDispatcher) {
            try {
                return@withContext subjectApi.invoke {
                    this.getEpisode(subjectId.toLong(), episodeId.toLong()).body().toEpisodeCollectionInfo()
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    return@withContext null
                }
                throw e
            }
        }

    override suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType,
    ): Boolean = withContext(ioDispatcher) {
        if (!sessionManager.canAccessAniApiNow()) {
            return@withContext false
        }
        try {
            subjectApi {
                batchUpdateEpisodeCollections(
                    subjectId.toLong(),
                    AniBatchUpdateEpisodeCollectionsRequest(
                        episodeIds = episodeId.map { it.toLong() },
                        episodeCollectionType = type.toAniEpisodeCollectionTypeUpdate(),
                    ),
                ).body()
            }
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@withContext false
            }
            throw e
        }
    }

    private companion object {
        fun HttpStatusCode.isUnauthorized(): Boolean {
            return this == HttpStatusCode.Unauthorized || this == HttpStatusCode.Forbidden
        }

        fun HttpStatusCode.isServerError(): Boolean {
            return this.value in 500..599
        }
    }
}

private fun EpisodeInfo.createNotCollected(): EpisodeCollectionInfo {
    return EpisodeCollectionInfo(
        episodeInfo = this,
        collectionType = UnifiedCollectionType.NOT_COLLECTED,
    )
}

private fun BangumiUserEpisodeCollection.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(episode.toEpisodeInfo(), type.toCollectionType())

internal fun BangumiEpisode.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.id,
        type = getEpisodeTypeByBangumiCode(this.type),
        name = this.name,
        nameCn = this.nameCn,
        airDate = PackedDate.parseFromDate(this.airdate),
        comment = this.comment,
//        duration = this.duration,
        desc = this.desc,
//        disc = this.disc,
        sort = EpisodeSort(this.sort, getEpisodeTypeByBangumiCode(this.type)),
        ep = EpisodeSort(this.ep ?: BigNum.ONE, getEpisodeTypeByBangumiCode(this.type)),
//        durationSeconds = this.durationSeconds
    )
}

internal fun BangumiEpisodeDetail.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = id,
        type = getEpisodeTypeByBangumiCode(this.type),
        name = name,
        nameCn = nameCn,
        sort = EpisodeSort(this.sort, getEpisodeTypeByBangumiCode(this.type)),
        airDate = PackedDate.parseFromDate(this.airdate),
        comment = comment,
//        duration = duration,
        desc = desc,
//        disc = disc,
        ep = EpisodeSort(this.ep ?: BigNum.ONE, getEpisodeTypeByBangumiCode(this.type)),
    )
}


internal fun EpisodeType.toBangumiEpType(): BangumiEpType {
    return when (this) {
        MainStory -> BangumiEpType.MainStory
        SP -> BangumiEpType.SP
        OP -> BangumiEpType.OP
        ED -> BangumiEpType.ED
        PV -> BangumiEpType.PV
        MAD -> BangumiEpType.MAD
        EpisodeType.OVA -> BangumiEpType.Other
        EpisodeType.OAD -> BangumiEpType.Other
    }
}

internal fun BangumiEpType.toEpisodeType(): EpisodeType? {
    return when (this) {
        BangumiEpType.MainStory -> MainStory
        BangumiEpType.SP -> SP
        BangumiEpType.OP -> OP
        BangumiEpType.ED -> ED
        BangumiEpType.PV -> PV
        BangumiEpType.MAD -> MAD
        BangumiEpType.Other -> null
    }
}

private fun getEpisodeTypeByBangumiCode(code: Int): EpisodeType? {
    return when (code) {
        0 -> MainStory
        1 -> SP
        2 -> OP
        3 -> ED
        4 -> PV
        5 -> MAD
        else -> null
    }
}

fun UnifiedCollectionType.toAniEpisodeCollectionType(): AniEpisodeCollectionType? {
    return when (this) {
        UnifiedCollectionType.NOT_COLLECTED -> null
        UnifiedCollectionType.WISH -> null
        UnifiedCollectionType.DOING -> null
        UnifiedCollectionType.DONE -> AniEpisodeCollectionType.DONE
        UnifiedCollectionType.ON_HOLD -> null
        UnifiedCollectionType.DROPPED -> null
    }
}

fun UnifiedCollectionType.toAniEpisodeCollectionTypeUpdate(): AniEpisodeCollectionTypeUpdate {
    return when (this) {
        UnifiedCollectionType.NOT_COLLECTED -> AniEpisodeCollectionTypeUpdate.NOT_COLLECTED
        UnifiedCollectionType.WISH -> AniEpisodeCollectionTypeUpdate.NOT_COLLECTED
        UnifiedCollectionType.DOING -> AniEpisodeCollectionTypeUpdate.NOT_COLLECTED
        UnifiedCollectionType.DONE -> AniEpisodeCollectionTypeUpdate.DONE
        UnifiedCollectionType.ON_HOLD -> AniEpisodeCollectionTypeUpdate.NOT_COLLECTED
        UnifiedCollectionType.DROPPED -> AniEpisodeCollectionTypeUpdate.NOT_COLLECTED
    }
}

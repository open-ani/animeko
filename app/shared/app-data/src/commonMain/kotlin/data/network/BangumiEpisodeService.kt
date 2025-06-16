/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.canAccessBangumiApiNow
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.EpisodeType.ED
import me.him188.ani.datasources.api.EpisodeType.MAD
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.EpisodeType.OP
import me.him188.ani.datasources.api.EpisodeType.PV
import me.him188.ani.datasources.api.EpisodeType.SP
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.paging.processPagedResponse
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.models.BangumiEpType
import me.him188.ani.datasources.bangumi.models.BangumiEpisode
import me.him188.ani.datasources.bangumi.models.BangumiEpisodeDetail
import me.him188.ani.datasources.bangumi.models.BangumiPatchUserSubjectEpisodeCollectionRequest
import me.him188.ani.datasources.bangumi.models.BangumiUserEpisodeCollection
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toEpisodeCollectionType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.serialization.BigNum
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

/**
 * 执行网络请求查询.
 */
sealed interface BangumiEpisodeService {
    /**
     * 获取用户在这个条目下的所有剧集的收藏状态. 当用户没有收藏此条目时返回 [collectionType] 均为 [UnifiedCollectionType.NOT_COLLECTED].
     *
     * @return 分页的剧集收藏信息. 使用 `toList()` 可以获取所有数据.
     */
    suspend fun getEpisodeCollectionInfosBySubjectId(subjectId: Int, epType: EpisodeType?): Flow<EpisodeCollectionInfo>

    /**
     * 获取用户在这个条目下的所有剧集的收藏状态. 当用户没有收藏此条目时返回 [collectionType] 均为 [UnifiedCollectionType.NOT_COLLECTED].
     *
     * @return 分页的剧集收藏信息.
     */
    suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int? = 0,
        limit: Int? = 100,
        episodeType: BangumiEpType? = null
    ): Paged<EpisodeCollectionInfo>

    /**
     * 获取单个剧集的信息和用户的收藏状态. 如果用户没有收藏这个剧集所属的条目, 则返回 [collectionType] 为 [UnifiedCollectionType.NOT_COLLECTED].
     *
     * 只有在 [episodeId] 找不到对应的公开剧集时返回 `null`.
     */
    suspend fun getEpisodeCollectionById(episodeId: Int): EpisodeCollectionInfo?

    /**
     * 设置多个剧集的收藏状态.
     *
     * 当设置成功时返回 `true`. 返回 `false` 表示用户没有收藏这个条目. 其他异常将会抛出.
     */
    suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType
    ): Boolean
}

class BangumiEpisodeServiceImpl(
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : BangumiEpisodeService, KoinComponent {
    private val client by inject<BangumiClient>()
    private val logger = logger<BangumiEpisodeServiceImpl>()
    private val sessionManager: SessionStateProvider by inject()

    override suspend fun getEpisodeCollectionInfosBySubjectId(
        subjectId: Int,
        epType: EpisodeType?
    ): Flow<EpisodeCollectionInfo> = withContext(ioDispatcher) {
        getSubjectEpisodeCollections(subjectId, epType?.toBangumiEpType())?.map {
            it.toEpisodeCollectionInfo()
        } ?: getEpisodesBySubjectId(subjectId, epType?.toBangumiEpType()).map {
            it.toEpisodeInfo().createNotCollected()
        }
    }.flowOn(ioDispatcher)

    override suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int?,
        limit: Int?,
        episodeType: BangumiEpType?
    ): Paged<EpisodeCollectionInfo> {
        return withContext(ioDispatcher) {
            if (sessionManager.canAccessBangumiApiNow()) {
                client.api {
                    getUserSubjectEpisodeCollection(
                        subjectId,
                        episodeType = episodeType,
                        offset = offset,
                        limit = limit,
                    ).body()
                }.run {
                    Paged.processPagedResponse(total, limit ?: 100, data)
                }.map {
                    it.toEpisodeCollectionInfo()
                }
            } else {
                getEpisodesBySubjectIdPage(subjectId, episodeType, offset = offset ?: 0, limit = limit ?: 100).map {
                    it.toEpisodeInfo().createNotCollected()
                }
            }
        }
    }

    private fun getEpisodesBySubjectId(subjectId: Int, type: BangumiEpType?): Flow<BangumiEpisode> {
        val episodes = PageBasedPagedSource { page ->
            getEpisodesBySubjectIdPage(subjectId, type, page * 100, 100)
        }
        return episodes.results
    }

    private suspend fun getEpisodesBySubjectIdPage(
        subjectId: Int,
        type: BangumiEpType?,
        offset: Int,
        limit: Int
    ): Paged<BangumiEpisode> = withContext(ioDispatcher) {
        try {
            client.api { getEpisodes(subjectId, type, offset = offset, limit = limit).body() }.run {
                Paged(this.total ?: 0, !this.data.isNullOrEmpty(), this.data.orEmpty())
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.BadRequest
                || e.response.status == HttpStatusCode.NotFound
            ) {
                return@withContext Paged.empty()
            }
            throw e
        }
    }

    private suspend fun getSubjectEpisodeCollections(
        subjectId: Int,
        type: BangumiEpType?
    ): Flow<BangumiUserEpisodeCollection>? {
        if (!sessionManager.canAccessBangumiApiNow()) {
            return null
        }

        val firstPage = try {
            withContext(ioDispatcher) {
                client.api {
                    getUserSubjectEpisodeCollection(
                        subjectId,
                        episodeType = type,
                        offset = 0,
                        limit = 100,
                    ).body()
                }
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound
                || e.response.status == HttpStatusCode.Unauthorized
                || e.response.status == HttpStatusCode.BadRequest // #1031
            ) {
                return null
            }
            throw e
        }

        if (firstPage.data.isNullOrEmpty()) {
            return null
        }

        val episodes = PageBasedPagedSource { page ->
            val resp = if (page == 0) {
                firstPage
            } else {
                try {
                    withContext(ioDispatcher) {
                        client.api {
                            getUserSubjectEpisodeCollection(
                                subjectId,
                                episodeType = type,
                                offset = page * 100,
                                limit = 100,
                            ).body()
                        }
                    }
                } catch (e: ClientRequestException) {
                    if (e.response.status == HttpStatusCode.BadRequest // #1031
                    ) {
                        return@PageBasedPagedSource Paged.empty()
                    }
                    throw e
                }
            }
            Paged.processPagedResponse(resp.total, 100, resp.data)
        }
        return episodes.results
    }

    override suspend fun getEpisodeCollectionById(episodeId: Int): EpisodeCollectionInfo? = withContext(ioDispatcher) {
        if (sessionManager.isLoggedInNow()) {
            try {
                return@withContext client.api { getUserEpisodeCollection(episodeId).body().toEpisodeCollectionInfo() }
            } catch (e: ClientRequestException) {
                if (e.response.status != HttpStatusCode.NotFound && !e.response.status.isUnauthorized()) {
                    throw e
                }
            }
        }

        try {
            client.api { getEpisodeById(episodeId).body().toEpisodeInfo().createNotCollected() }
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == HttpStatusCode.NotFound) {
                return@withContext null
            }
            throw e
        }
    }

    override suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType
    ): Boolean = withContext(ioDispatcher) {
        if (!sessionManager.isLoggedInNow()) {
            return@withContext false
        }
        try {
            client.api {
                patchUserSubjectEpisodeCollection(
                    subjectId,
                    BangumiPatchUserSubjectEpisodeCollectionRequest(
                        episodeId,
                        type.toEpisodeCollectionType(),
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

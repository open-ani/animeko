/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import me.him188.ani.app.data.models.episode.EpisodeCollection
import me.him188.ani.app.data.models.episode.EpisodeCollections
import me.him188.ani.app.data.models.episode.episode
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.network.toEpisodeInfo
import me.him188.ani.app.data.persistent.PlatformDataStoreManager
import me.him188.ani.app.data.repository.BangumiSubjectRepository
import me.him188.ani.app.data.repository.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.data.repository.SubjectCollectionRepository
import me.him188.ani.app.data.repository.SubjectRepository
import me.him188.ani.app.data.repository.toSelfRatingInfo
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.tools.ldc.ContentPolicy
import me.him188.ani.app.tools.ldc.mutate
import me.him188.ani.app.tools.ldc.setEach
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiEpType
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toEpisodeCollectionType
import me.him188.ani.utils.coroutines.runUntilSuccess
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 管理收藏条目以及它们的缓存.
 */
abstract class SubjectManager {
    ///////////////////////////////////////////////////////////////////////////
    // Subject progress
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 获取指定条目下指定剧集的收藏情况 flow.
     */
    abstract fun episodeCollectionFlow(
        subjectId: Int,
        episodeId: Int,
        contentPolicy: ContentPolicy
    ): Flow<EpisodeCollection>

    abstract suspend fun setAllEpisodesWatched(subjectId: Int)

    abstract suspend fun setEpisodeCollectionType(subjectId: Int, episodeId: Int, collectionType: UnifiedCollectionType)

    ///////////////////////////////////////////////////////////////////////////
    // Rating
    ///////////////////////////////////////////////////////////////////////////

    abstract suspend fun updateRating(
        subjectId: Int,
        score: Int? = null, // 0 to remove rating
        comment: String? = null, // set empty to remove
        tags: List<String>? = null,
        isPrivate: Boolean? = null
    )
}

/**
 * 获取指定条目是否已经完结. 不是用户是否看完, 只要条目本身完结了就算.
 */
fun EpisodeCollectionRepository.subjectCompletedFlow(subjectId: Int): Flow<Boolean> {
    return episodeCollectionsFlow(subjectId).map { epCollection ->
        EpisodeCollections.isSubjectCompleted(epCollection.map { it.episode })
    }
}

suspend inline fun SubjectManager.setEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) =
    setEpisodeCollectionType(
        subjectId,
        episodeId,
        if (watched) UnifiedCollectionType.DONE else UnifiedCollectionType.WISH,
    )

class SubjectManagerImpl(
    dataStores: PlatformDataStoreManager
) : KoinComponent, SubjectManager() {
    private val bangumiSubjectRepository: BangumiSubjectRepository by inject()
    private val bangumiEpisodeService: BangumiEpisodeService by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val subjectRepository: SubjectRepository by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()

    private val sessionManager: SessionManager by inject()
    private val cacheManager: MediaCacheManager by inject()

    private val showAllEpisodes: Flow<Boolean> = settingsRepository.debugSettings.flow.map { it.showAllEpisodes }

    override suspend fun updateRating(
        subjectId: Int,
        score: Int?,
        comment: String?,
        tags: List<String>?,
        isPrivate: Boolean?
    ) {
        bangumiSubjectRepository.patchSubjectCollection(
            subjectId,
            BangumiUserSubjectCollectionModifyPayload(
                rate = score,
                comment = comment,
                tags = tags,
                private = isPrivate,
            ),
        )

        findSubjectCacheById(subjectId)?.mutate {
            setEach({ it.subjectId == subjectId }) {
                copy(
                    selfRatingInfo = SelfRatingInfo(
                        score = score ?: selfRatingInfo.score,
                        comment = comment ?: selfRatingInfo.comment,
                        tags = tags ?: selfRatingInfo.tags,
                        isPrivate = isPrivate ?: selfRatingInfo.isPrivate,
                    ),
                )
            }
        }
    }

    override fun episodeCollectionFlow(
        subjectId: Int,
        episodeId: Int,
        contentPolicy: ContentPolicy
    ): Flow<EpisodeCollection> {
        return subjectCollectionFlow(subjectId, contentPolicy)
            .transform { subject ->
                if (subject == null) {
                    emit(
                        runUntilSuccess {
                            bangumiEpisodeService.getEpisodeCollection(
                                episodeId,
                            )?.toEpisodeCollection()
                                ?: bangumiEpisodeService.getEpisodeById(episodeId)?.let {
                                    EpisodeCollection(it.toEpisodeInfo(), UnifiedCollectionType.NOT_COLLECTED)
                                }
                                ?: error("Failed to get episode collection")
                        },
                    )
                } else {
                    emitAll(
                        subject.episodes.filter { it.episode.id == episodeId }.asFlow(),
                    )
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun setSubjectCollectionType(subjectId: Int, type: UnifiedCollectionType) {
        TODO()
    }

    override suspend fun setAllEpisodesWatched(subjectId: Int) {
        TODO()
    }

    override suspend fun setEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType
    ) {
        bangumiEpisodeService.setEpisodeCollection(
            subjectId,
            listOf(episodeId),
            collectionType.toEpisodeCollectionType(),
        )

        TODO()
    }

    private suspend fun fetchSubjectCollection(
        subjectId: Int,
    ): SubjectCollection {
        val info = getSubjectInfo(subjectId)

        // 查收藏状态, 没收藏就查剧集, 认为所有剧集都没有收藏
        val episodes: List<EpisodeCollection> = fetchEpisodeCollections(subjectId)

        val collection = bangumiSubjectRepository.subjectCollectionById(subjectId).first()

        return SubjectCollection(
            info, episodes,
            collectionType = collection?.type?.toCollectionType()
                ?: UnifiedCollectionType.NOT_COLLECTED,
            selfRatingInfo = collection?.toSelfRatingInfo() ?: SelfRatingInfo.Empty,
        )
    }

    private suspend fun fetchEpisodeCollections(subjectId: Int): List<EpisodeCollection> {
        val type: BangumiEpType? = if (showAllEpisodes.first()) null else BangumiEpType.MainStory
        // 查收藏状态, 没收藏就查剧集, 认为所有剧集都没有收藏
        return bangumiEpisodeService.getSubjectEpisodeCollections(subjectId, type)
            .let { collections ->
                collections?.toList()?.map { it.toEpisodeCollection() }
                    ?: bangumiEpisodeService.getEpisodesBySubjectId(subjectId, type)
                        .toList()
                        .map {
                            EpisodeCollection(it.toEpisodeInfo(), UnifiedCollectionType.NOT_COLLECTED)
                        }
            }
    }

}



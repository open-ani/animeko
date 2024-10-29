/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.SubjectCollectionInfo
import me.him188.ani.app.data.models.SubjectInfo
import me.him188.ani.app.data.models.episode.isKnownCompleted
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.network.BangumiSubjectService
import me.him188.ani.app.data.persistent.database.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.SubjectCollectionEntity
import me.him188.ani.app.data.repository.Repository.Companion.defaultPagingConfig
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

typealias BangumiSubjectApi = DefaultApi

class SubjectCollectionRepository(
    private val client: BangumiClient,
    private val api: Flow<BangumiSubjectApi>,
    private val bangumiSubjectService: BangumiSubjectService,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val usernameProvider: RepositoryUsernameProvider,
    private val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : Repository {
    fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> {
        return (bangumiSubjectService.subjectCollectionCountsFlow() as Flow<SubjectCollectionCounts?>)
            .retry(2)
            .catch {
                logger.error("Failed to get subject collection counts", it)
                emit(null)
            }
//        return combine(
//            subjectCollectionDao.countCollected(UnifiedCollectionType.WISH),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DOING),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DONE),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.ON_HOLD),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DROPPED),
//        ) { wish, doing, done, onHold, dropped ->
//            SubjectCollectionCounts(
//                wish = wish,
//                doing = doing,
//                done = done,
//                onHold = onHold,
//                dropped = dropped,
//                total = wish + doing + done + onHold + dropped,
//            )
//        }
    }

    fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
        subjectCollectionDao.findById(subjectId).map { entity ->
            if (entity != null) {
                return@map entity.toSubjectCollectionInfo(
                    episodes = getSubjectEpisodeCollections(subjectId),
                    currentDate = getCurrentDate(),
                )
            } else {
                val collection = bangumiSubjectService.subjectCollectionById(subjectId).first()
                batchGetSubjectDetails(listOf(subjectId)).first()
                    .toEntity(
                        collection?.type.toCollectionType(),
                        selfRatingInfo = collection?.toSelfRatingInfo() ?: SelfRatingInfo.Empty,
                    )
                    .also {
                        subjectCollectionDao.upsert(it)
                    }
                    .toSubjectCollectionInfo(
                        episodes = getSubjectEpisodeCollections(subjectId),
                        currentDate = getCurrentDate(),
                    )
            }
        }

    private suspend fun getSubjectEpisodeCollections(subjectId: Int) =
        episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId).first()

    fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        type: UnifiedCollectionType? = null, // null for all
    ): Flow<List<SubjectCollectionInfo>> =
        subjectCollectionDao.filterMostRecentCollected(type, limit).map { list ->
            list.map { entity ->
                entity.toSubjectCollectionInfo(
                    episodes = getSubjectEpisodeCollections(entity.subjectId),
                    currentDate = getCurrentDate(),
                )
            }
        }

    fun subjectCollectionsPager(
        query: CollectionsFilterQuery = CollectionsFilterQuery.Empty,
        pagingConfig: PagingConfig = defaultPagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> = Pager(
        config = pagingConfig,
        initialKey = 0,
        remoteMediator = SubjectCollectionRemoteMediator(query),
        pagingSourceFactory = {
            subjectCollectionDao.filterByCollectionTypePaging(query.type)
        },
    ).flow.map { data ->
        data.map { entity ->
            entity.toSubjectCollectionInfo(
                episodes = getSubjectEpisodeCollections(entity.subjectId),
                currentDate = getCurrentDate(),
            )
        }
    }

    suspend fun updateRating(
        subjectId: Int,
        score: Int? = null, // 0 to remove rating
        comment: String? = null, // set empty to remove
        tags: List<String>? = null,
        isPrivate: Boolean? = null,
    ) {
        bangumiSubjectService.patchSubjectCollection(
            subjectId,
            BangumiUserSubjectCollectionModifyPayload(
                rate = score,
                comment = comment,
                tags = tags,
                private = isPrivate,
            ),
        )

        subjectCollectionDao.updateRating(
            subjectId,
            score,
            comment,
            tags,
            isPrivate,
        )
    }

    private inner class SubjectCollectionRemoteMediator<T : Any>(
        private val query: CollectionsFilterQuery,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction = withContext(ioDispatcher) {
            if ((currentTimeMillis() - subjectCollectionDao.lastUpdated()).milliseconds > 1.hours) {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            } else {
                InitializeAction.SKIP_INITIAL_REFRESH
            }
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult {
            return try {
                withContext(ioDispatcher) {
                    val offset = when (loadType) {
                        LoadType.REFRESH -> {
                            subjectCollectionDao.deleteAll()
                            0
                        }

                        LoadType.PREPEND -> return@withContext MediatorResult.Success(
                            endOfPaginationReached = true,
                        )

                        LoadType.APPEND -> {
                            val lastLoadedPage = state.pages.lastOrNull()
                            if (lastLoadedPage != null) {
                                lastLoadedPage.itemsBefore + lastLoadedPage.data.size
                            } else {
                                0
                            }
                        }
                    }

                    val username = usernameProvider.getOrThrow()
                    val resp = api.first().getUserCollectionsByUsername(
                        username,
                        type = query.type?.toSubjectCollectionType(),
                        limit = state.config.pageSize,
                        offset = offset,
                    ).body()
                    val collections = resp.data.orEmpty()
                    val items = batchGetSubjectDetails(collections.map { it.subjectId })
                        .map { batch ->
                            val collection =
                                collections.first { it.subjectId == batch.subjectInfo.subjectId }
                            batch.toEntity(
                                collection.type.toCollectionType(),
                                collection.toSelfRatingInfo(),
                            )
                        }
                        .also { subjectCollectionDao.upsert(it) }

                    MediatorResult.Success(endOfPaginationReached = items.isEmpty())
                }
            } catch (e: RepositoryException) {
                MediatorResult.Error(e)
            } catch (e: ResponseException) {
                MediatorResult.Error(e)
            } catch (e: Exception) {
                MediatorResult.Error(e)
            }
        }

    }

    suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    ) {
        return if (type == null) {
            deleteSubjectCollection(subjectId)
        } else {
            patchSubjectCollection(
                subjectId,
                BangumiUserSubjectCollectionModifyPayload(type.toSubjectCollectionType()),
            )
        }
    }

    private suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: BangumiUserSubjectCollectionModifyPayload,
    ) {
        api.first().postUserCollection(subjectId, payload)
        subjectCollectionDao.updateType(subjectId, payload.type.toCollectionType())
    }

    private suspend fun deleteSubjectCollection(subjectId: Int) {
        // TODO: deleteSubjectCollection
    }


    private suspend fun getSubjectDetails(ids: Int): BatchSubjectDetails {
        return batchGetSubjectDetails(listOf(ids)).first()
    }

    suspend fun batchGetSubjectDetails(ids: List<Int>): List<BatchSubjectDetails> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val resp = client.executeGraphQL(
            """
            fragment Ep on Episode {
              id
              type
              name
              name_cn
              airdate
              comment
              description
              sort
            }

            fragment SubjectFragment on Subject {
              id
              name
              name_cn
              images{large, common}
              characters {
                order
                type
                character {
                  id
                  name
                }
              }
              infobox {
                values {
                  k
                  v
                }
                key
              }
              summary
              eps
              collection{collect , doing, dropped, on_hold, wish}
              airtime{date}
              rating{count, rank, score, total}
              nsfw
              tags{count, name}
              
              leadingEpisodes : episodes(limit: 1) { ...Ep }
              trailingEpisodes : episodes(limit: 1, offset:-1) { ...Ep }
            }

            query BatchGetSubjectQuery {
              ${
                ids.joinToString(separator = "\n") { id ->
                    """
                        s$id:subject(id: $id){...SubjectFragment}
                """
                }
            }
            }
        """.trimIndent(),
        )
        resp["errors"]?.let {
            logger.error("batchGetSubjectDetails failed for query $ids: $it")
        }
        val list = resp
            .getOrFail("data")
            .let { element ->
                when (element) {
                    is JsonObject -> {
                        element.values.mapIndexed { index, it ->
                            if (it is JsonNull) { // error
                                val id = ids[index]
                                BatchSubjectDetails(
                                    SubjectInfo.Empty.copy(
                                        subjectId = id, subjectType = SubjectType.ANIME,
                                        nameCn = "<$id 错误>",
                                        name = "<$id 错误>",
                                        summary = resp["errors"].toString(),
                                    ),
                                )
                            } else {
                                it.jsonObject.toBatchSubjectDetails()
                            }
                        }
                    }

                    is JsonNull -> throw IllegalStateException("batchGetSubjectDetails response data is null for ids $ids: $resp")
                    else -> throw IllegalStateException("Unexpected response: $element")
                }
            }

        return list
    }

    private companion object {
        val logger = logger<SubjectCollectionRepository>()
    }
}

data class CollectionsFilterQuery(
    val type: UnifiedCollectionType?,
) {
    companion object {
        val Empty = CollectionsFilterQuery(null)
    }
}

class BatchSubjectDetails(
    val subjectInfo: SubjectInfo,
)

private fun BangumiUserSubjectCollection.toSelfRatingInfo(): SelfRatingInfo {
    return SelfRatingInfo(
        score = rate,
        comment = comment.takeUnless { it.isNullOrBlank() },
        tags = tags,
        isPrivate = private,
    )
}

private fun BatchSubjectDetails.toEntity(
    collectionType: UnifiedCollectionType,
    selfRatingInfo: SelfRatingInfo,
): SubjectCollectionEntity =
    subjectInfo.run {
        SubjectCollectionEntity(
            subjectId = subjectId,
//            subjectType = SubjectType.ANIME,
            name = name,
            nameCn = nameCn,
            summary = summary,
            nsfw = nsfw,
            imageLarge = imageLarge,
            totalEpisodes = totalEpisodes,
            airDate = airDate,
            tags = tags,
            aliases = aliases,
            ratingInfo = ratingInfo,
            collectionStats = collectionStats,
            completeDate = completeDate,
            selfRatingInfo = selfRatingInfo,
            collectionType = collectionType,
        )
    }

private fun SubjectCollectionEntity.toSubjectInfo(): SubjectInfo {
    return SubjectInfo(
        subjectId = subjectId,
        subjectType = SubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = imageLarge,
        totalEpisodes = totalEpisodes,
        airDate = airDate,
        tags = tags,
        aliases = aliases,
        ratingInfo = ratingInfo,
        collectionStats = collectionStats,
        completeDate = completeDate,
    )
}

private fun JsonElement.vSequence(): Sequence<String> {
    return when (this) {
        is JsonArray -> this.asSequence().flatMap { it.vSequence() }
        is JsonPrimitive -> sequenceOf(content)
        is JsonObject -> this["v"]?.vSequence() ?: emptySequence()
        else -> emptySequence()
    }
}

private fun JsonObject.toBatchSubjectDetails(): BatchSubjectDetails {
    fun infobox(key: String): Sequence<String> = sequence {
        for (jsonElement in getOrFail("infobox").jsonArray) {
            if (jsonElement.jsonObject.getStringOrFail("key") == key) {
                yieldAll(jsonElement.jsonObject.getOrFail("values").vSequence())
            }
        }
    }

    val completionDate = (infobox("播放结束") + infobox("放送结束"))
        .firstOrNull()
        ?.let {
            PackedDate.parseFromDate(
                it.replace('年', '-')
                    .replace('月', '-')
                    .removeSuffix("日"),
            )
        }
        ?: PackedDate.Invalid

    return try {
        BatchSubjectDetails(
            SubjectInfo(
                subjectId = getIntOrFail("id"),
                subjectType = SubjectType.ANIME,
                name = getStringOrFail("name"),
                nameCn = getStringOrFail("name_cn"),
                summary = getStringOrFail("summary"),
                nsfw = getBooleanOrFail("nsfw"),
                imageLarge = getOrFail("images").jsonObjectOrNull?.getString("large")
                    ?: "", // 有少数没有
                totalEpisodes = getIntOrFail("eps"),
                airDate = PackedDate.parseFromDate(getOrFail("airtime").jsonObject.getStringOrFail("date")),
                tags = getOrFail("tags").jsonArray.map {
                    val obj = it.jsonObject
                    Tag(
                        obj.getStringOrFail("name"),
                        obj.getIntOrFail("count"),
                    )
                },
                aliases = infobox("别名").filter { it.isNotEmpty() }.toList(),
                ratingInfo = getOrFail("rating").jsonObject.let { rating ->
                    RatingInfo(
                        rank = rating.getIntOrFail("rank"),
                        total = rating.getIntOrFail("total"),
                        count = rating.getOrFail("count").jsonArray.let { array ->
                            RatingCounts(
                                s1 = array[0].jsonPrimitive.int,
                                s2 = array[1].jsonPrimitive.int,
                                s3 = array[2].jsonPrimitive.int,
                                s4 = array[3].jsonPrimitive.int,
                                s5 = array[4].jsonPrimitive.int,
                                s6 = array[5].jsonPrimitive.int,
                                s7 = array[6].jsonPrimitive.int,
                                s8 = array[7].jsonPrimitive.int,
                                s9 = array[8].jsonPrimitive.int,
                                s10 = array[9].jsonPrimitive.int,
                            )
                        },
                        score = rating.getStringOrFail("score"),
                    )
                },
                collectionStats = getOrFail("collection").jsonObject.let { collection ->
                    SubjectCollectionStats(
                        wish = collection.getIntOrFail("wish"),
                        doing = collection.getIntOrFail("doing"),
                        done = collection.getIntOrFail("collect"),
                        onHold = collection.getIntOrFail("on_hold"),
                        dropped = collection.getIntOrFail("dropped"),
                    )
                },
                completeDate = completionDate,
            ),
        )
    } catch (e: Exception) {
        throw IllegalStateException(
            "Failed to parse subject details for subject id ${this["id"]}: $this",
            e,
        )
    }
}

private val JsonElement.jsonObjectOrNull get() = (this as? JsonObject)

private fun JsonObject.getOrFail(key: String): JsonElement {
    return get(key) ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getIntOrFail(key: String): Int {
    return get(key)?.jsonPrimitive?.int ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getString(key: String): String? {
    return get(key)?.jsonPrimitive?.content
}

private fun JsonObject.getStringOrFail(key: String): String {
    return getString(key) ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getBooleanOrFail(key: String): Boolean {
    return get(key)?.jsonPrimitive?.boolean ?: throw NoSuchElementException("key $key not found")
}

private fun SubjectCollectionEntity.toSubjectCollectionInfo(
    episodes: List<EpisodeCollectionInfo>,
    currentDate: PackedDate,
): SubjectCollectionInfo {
    val subjectInfo = toSubjectInfo()
    return SubjectCollectionInfo(
        collectionType = collectionType,
        subjectInfo = subjectInfo,
        selfRatingInfo = selfRatingInfo,
        airingInfo = SubjectAiringInfo.computeFromEpisodeList(
            episodes.map { it.episodeInfo },
            airDate,
        ),
        episodes = episodes,
        progressInfo = SubjectProgressInfo.compute(
            subjectStarted = currentDate > subjectInfo.airDate,
            episodes = episodes.map {
                SubjectProgressInfo.Episode(
                    it.episodeId,
                    it.collectionType,
                    it.episodeInfo.sort,
                    it.episodeInfo.airDate,
                    it.episodeInfo.isKnownCompleted,
                )
            },
            subjectAirDate = subjectInfo.airDate,
        ),
    )
}


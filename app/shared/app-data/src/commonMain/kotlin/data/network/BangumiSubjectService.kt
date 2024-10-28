/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.username
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.paging.processPagedResponse
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.models.BangumiCount
import me.him188.ani.datasources.bangumi.models.BangumiRating
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Performs network requests.
 * Use [SubjectManager] instead.
 */
interface BangumiSubjectService {
    suspend fun getSubject(id: Int): BangumiSubject

    fun getSubjectCollections(
        username: String,
        subjectType: BangumiSubjectType? = null,
        subjectCollectionType: BangumiSubjectCollectionType? = null,
    ): PagedSource<BangumiUserSubjectCollection>

    /**
     * 获取用户对这个条目的收藏状态. flow 一定会 emit 至少一个值或抛出异常. 当用户没有收藏这个条目时 emit `null`.
     */
    fun subjectCollectionById(subjectId: Int): Flow<BangumiUserSubjectCollection?>

    fun subjectCollectionTypeById(subjectId: Int): Flow<UnifiedCollectionType>

    suspend fun patchSubjectCollection(subjectId: Int, payload: BangumiUserSubjectCollectionModifyPayload)
    suspend fun deleteSubjectCollection(subjectId: Int)

    /**
     * 获取各个收藏分类的数量.
     */
    fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts>
}

suspend inline fun BangumiSubjectService.setSubjectCollectionTypeOrDelete(
    subjectId: Int,
    type: BangumiSubjectCollectionType?
) {
    return if (type == null) {
        deleteSubjectCollection(subjectId)
    } else {
        patchSubjectCollection(subjectId, BangumiUserSubjectCollectionModifyPayload(type))
    }
}

class RemoteBangumiSubjectService : BangumiSubjectService, KoinComponent {
    private val client: BangumiClient by inject()
    private val sessionManager: SessionManager by inject()
    private val logger = logger(this::class)

    override suspend fun getSubject(id: Int): BangumiSubject = client.getApi().getSubjectById(id).body()

    override suspend fun patchSubjectCollection(subjectId: Int, payload: BangumiUserSubjectCollectionModifyPayload) {
        client.getApi().postUserCollection(subjectId, payload)
    }

    override suspend fun deleteSubjectCollection(subjectId: Int) {
        // TODO:  deleteSubjectCollection
    }

    @OptIn(OpaqueSession::class)
    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts> {
        return sessionManager.username.filterNotNull().map { username ->
            val types = UnifiedCollectionType.entries - UnifiedCollectionType.NOT_COLLECTED
            val totals = IntArray(types.size) { type ->
                client.getApi().getUserCollectionsByUsername(
                    username,
                    subjectType = BangumiSubjectType.Anime,
                    type = types[type].toSubjectCollectionType(),
                    limit = 1, // we only need the total count. API requires at least 1
                ).body().total ?: 0
            }
            SubjectCollectionCounts(
                wish = totals[UnifiedCollectionType.WISH.ordinal],
                doing = totals[UnifiedCollectionType.DOING.ordinal],
                done = totals[UnifiedCollectionType.DONE.ordinal],
                onHold = totals[UnifiedCollectionType.ON_HOLD.ordinal],
                dropped = totals[UnifiedCollectionType.DROPPED.ordinal],
                total = totals.sum(),
            )
        }.flowOn(Dispatchers.IO_)
    }

    override fun getSubjectCollections(
        username: String,
        subjectType: BangumiSubjectType?,
        subjectCollectionType: BangumiSubjectCollectionType?,
    ): PagedSource<BangumiUserSubjectCollection> {
        return PageBasedPagedSource { page ->
            try {
                val pageSize = 10
                withContext(Dispatchers.IO_) {
                    client.getApi().getUserCollectionsByUsername(
                        username,
                        offset = page * pageSize, limit = pageSize,
                        subjectType = subjectType,
                        type = subjectCollectionType,
                    ).body().run {
                        total?.let { setTotalSize(it) }
                        Paged.processPagedResponse(total, pageSize, data)
                    }
                }
            } catch (e: ResponseException) {
                logger.warn("Exception in getCollections, page=$page", e)
                null
            }
        }
    }

    override fun subjectCollectionById(subjectId: Int): Flow<BangumiUserSubjectCollection?> {
        return flow {
            emit(
                try {
                    @OptIn(OpaqueSession::class)
                    client.getApi().getUserCollection(sessionManager.username.first() ?: "-", subjectId).body()
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        null
                    } else {
                        throw e
                    }
                },
            )
        }
    }

    override fun subjectCollectionTypeById(subjectId: Int): Flow<UnifiedCollectionType> {
        return flow {
            emit(
                try {
                    @OptIn(OpaqueSession::class)
                    val username = sessionManager.username.first() ?: "-"
                    client.getApi().getUserCollection(username, subjectId).body().type.toCollectionType()
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        UnifiedCollectionType.NOT_COLLECTED
                    } else {
                        throw e
                    }
                },
            )
        }
    }
}


//fun BangumiSubject.toSubjectInfo(): SubjectInfo {
//    return SubjectInfo(
//        id = id,
//        name = name,
//        nameCn = nameCn,
//        summary = this.summary,
//        nsfw = this.nsfw,
//        locked = this.locked,
//        platform = this.platform,
//        volumes = this.volumes,
//        eps = this.eps,
//        totalEpisodes = this.totalEpisodes,
//        airDateString = this.date,
//        tags = this.tags.map { Tag(it.name, it.count) }.sortedByDescending { it.count },
//        infobox = this.infobox?.map { it.toInfoboxItem() }.orEmpty(),
//        imageCommon = this.images.common,
//        imageLarge = this.images.large,
//        collection = this.collection.run {
//            SubjectCollectionStats(
//                wish = wish,
//                doing = doing,
//                done = collect,
//                onHold = onHold,
//                dropped = dropped,
//            )
//        },
//        ratingInfo = this.rating.toRatingInfo(),
//    )
//}


private fun BangumiRating.toRatingInfo(): RatingInfo = RatingInfo(
    rank = rank,
    total = total,
    count = count.toRatingCounts(),
    score = score.toString(),
)

private fun BangumiCount.toRatingCounts() = RatingCounts(
    _1 ?: 0,
    _2 ?: 0,
    _3 ?: 0,
    _4 ?: 0,
    _5 ?: 0,
    _6 ?: 0,
    _7 ?: 0,
    _8 ?: 0,
    _9 ?: 0,
    _10 ?: 0,
)

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.IntSet
import androidx.collection.intListOf
import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableIntSetOf
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessBangumiApiNow
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.models.BangumiCount
import me.him188.ani.datasources.bangumi.models.BangumiPerson
import me.him188.ani.datasources.bangumi.models.BangumiRating
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.associateWithTo
import me.him188.ani.utils.platform.collections.mapToIntList
import org.koin.core.component.KoinComponent
import kotlin.coroutines.CoroutineContext

/**
 * Performs network requests.
 * Use [SubjectManager] instead.
 */
interface BangumiSubjectService {
    suspend fun getSubject(id: Int): BangumiSubject

    suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int
    ): List<BatchSubjectCollection>

    suspend fun getSubjectCollection(subjectId: Int): BatchSubjectCollection

    suspend fun batchGetSubjectDetails(
        ids: IntList,
        withCharacterActors: Boolean = true,
    ): List<BatchSubjectDetails>

    suspend fun batchGetSubjectRelations(
        ids: IntList,
        withCharacterActors: Boolean,
    ): List<BatchSubjectRelations>

    suspend fun batchGetLightSubjectAndEpisodes(
        subjectIds: IntList,
    ): List<LightSubjectAndEpisodes>

    /**
     * 获取用户对这个条目的收藏状态. flow 一定会 emit 至少一个值或抛出异常. 当用户没有收藏这个条目时 emit `null`. 当没有登录时 emit `null`.
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

data class BatchSubjectCollection(
    val batchSubjectDetails: BatchSubjectDetails,
    /**
     * `null` 表示未收藏
     */
    val collection: BangumiUserSubjectCollection?,
)

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

class RemoteBangumiSubjectService(
    private val client: BangumiClient,
    private val api: ApiInvoker<DefaultApi>,
    private val sessionManager: SessionStateProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : BangumiSubjectService, KoinComponent {
    private val logger = logger<RemoteBangumiSubjectService>()

    override suspend fun getSubject(id: Int): BangumiSubject = withContext(ioDispatcher) {
        api { getSubjectById(id).body() }
    }

    override suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int
    ): List<BatchSubjectCollection> = withContext(ioDispatcher) {
        sessionManager.checkAccessBangumiApiNow()
        val collections = try {
            api {

                getUserCollectionsByUsername(
                    TODO("getSubjectCollections"),
                    subjectType = BangumiSubjectType.Anime,
                    type = type,
                    limit = limit,
                    offset = offset,
                ).body().data.orEmpty()
            }
        } catch (e: ClientRequestException) {
            // invalid: 400 . Text: "{"title":"Bad Request","details":{"path":"/v0/users/him188/collections","method":"GET","query_string":"subject_type=2&type=1&limit=30&offset=35"},"request_id":".","description":"offset should be less than or equal to 34"}
            if (e.response.status == HttpStatusCode.BadRequest) {
                emptyList()
            } else {
                throw e
            }
        }

        val list = batchGetSubjectDetails(collections.mapToIntList { it.subjectId })

        list.map {
            val subjectId = it.subjectInfo.subjectId
            val dto = collections.firstOrNull { it.subjectId == subjectId }
                ?: error("Subject $subjectId not found in collections")
            BatchSubjectCollection(
                batchSubjectDetails = it,
                collection = dto,
            )
        }
    }

    override suspend fun getSubjectCollection(subjectId: Int): BatchSubjectCollection {
        val collection = subjectCollectionById(subjectId).first()
        return BatchSubjectCollection(
            batchGetSubjectDetails(intListOf(subjectId)).first(),
            collection,
        )
    }

    override suspend fun batchGetSubjectDetails(
        ids: IntList,
        withCharacterActors: Boolean
    ): List<BatchSubjectDetails> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            val respDeferred = async {
                BangumiSubjectGraphQLExecutor.execute(client, ids)
            }

            // 等待查询条目信息
            val (response, errors) = respDeferred.await()

            // 解析条目详情
            response.mapIndexed { index, element ->
                if (element == null) { // error
                    if (errors == null) {
                        // 没有错误, 说明这个条目是没权限获取
                        val subjectId = ids[index]
                        BatchSubjectDetails(
                            SubjectInfo.Empty.copy(
                                subjectId = subjectId,
                                subjectType = SubjectType.ANIME,
                                nameCn = "账号注册满四个月后可看 $subjectId",
                                name = "账号注册满四个月后可看 $subjectId",
                                summary = "此条目已被隐藏, 请尝试登录后再次尝试. 如已登录, 请等待注册时间满四个月后再看.",
                                nsfw = true,
                            ),
                            mainEpisodeCount = 0,
                            LightSubjectRelations(
                                emptyList(),
                                emptyList(),
                            ),
                        )
                    } else {
                        val subjectId = ids[index]
                        BatchSubjectDetails(
                            SubjectInfo.Empty.copy(
                                subjectId = subjectId, subjectType = SubjectType.ANIME,
                                nameCn = "<$subjectId 错误>",
                                name = "<$subjectId 错误>",
                                summary = errors,
                            ),
                            mainEpisodeCount = 0,
                            LightSubjectRelations(
                                emptyList(),
                                emptyList(),
                            ),
                        )
                    }
                } else {
                    BangumiSubjectGraphQLParser.parseBatchSubjectDetails(element)
                }
            }
        }
    }

    override suspend fun batchGetSubjectRelations(
        ids: IntList,
        withCharacterActors: Boolean
    ): List<BatchSubjectRelations> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            val respDeferred = async {
                BangumiSubjectRelationsGraphQLExecutor.execute(client, ids)
            }

            val actorConcurrency = Semaphore(10)
            // subjectId to List<Character>
            val subjectIdToActorsDeferred = if (withCharacterActors) {
                ids.associateWithTo(HashMap(ids.size)) { id ->
                    // faster query
                    async {
                        actorConcurrency.withPermit {
                            mutableIntObjectMapOf<List<BangumiPerson>>().apply {
                                for (character in api { getRelatedCharactersBySubjectId(id).body() }) {
                                    put(character.id, character.actors.orEmpty())
                                }
                            }
                        }
                    }
                }
            } else {
                emptyMap()
            }
            val subjectIdToActors = subjectIdToActorsDeferred.mapValues { it.value.await() }

            // 等待查询条目信息
            val (response, errors) = respDeferred.await()

            // 获取所有条目的所有配音人员 ID
            val actorPersonIdSet: IntSet = mutableIntSetOf().apply {
                for (element in response) {
                    if (element != null) {
                        BangumiSubjectGraphQLParser.forEachCharacter(element) { subjectId, characterId ->
                            subjectIdToActors[subjectId]!![characterId]?.forEach {
                                add(it.id)
                            }
                        }
                    }
                }
            }

            val actorPersonIdArray = actorPersonIdSet.toIntArray()

            // 获取配音人员详情
            // key is person id
            val actorPersons: IntObjectMap<PersonInfo> = mutableIntObjectMapOf<PersonInfo>().apply {
                BangumiPersonGraphQLExecutor.execute(
                    client,
                    actorPersonIdArray,
                ).data.forEachIndexed { index, jsonObject ->
                    if (jsonObject != null) {
                        put(actorPersonIdArray[index], BangumiSubjectGraphQLParser.parsePerson(jsonObject))
                    }
                }
            }

            // 解析条目详情
            response.mapIndexed { index, element ->
                if (element == null) { // error
                    val subjectId = ids[index]
                    BatchSubjectRelations(
                        subjectId = subjectId,
                        listOf(
                            RelatedCharacterInfo(
                                0,
                                CharacterInfo(
                                    id = 0,
                                    nameCn = "<错误>",
                                    name = "<错误>",
                                    actors = emptyList(),
                                    imageMedium = "",
                                    imageLarge = "",
                                ),
                                CharacterRole.MAIN,
                            ),
                        ),
                        emptyList(),
                    )
                } else {
                    val subjectId = ids[index]
                    BangumiSubjectGraphQLParser.parseBatchSubjectRelations(
                        element,
                        getActors = {
                            subjectIdToActors[subjectId]!![it]?.map { person ->
                                actorPersons[person.id]
                                    ?: error("Actor (person) ${person.id} not found. Available actors: $actorPersons")
                            }.orEmpty()
                        },
                    )
                }
            }
        }
    }

    override suspend fun batchGetLightSubjectAndEpisodes(subjectIds: IntList): List<LightSubjectAndEpisodes> {
        if (subjectIds.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            // 等待查询条目信息
            val (response, errors) = BangumiLightSubjectGraphQLExecutor.execute(client, subjectIds)

            // 解析条目详情
            response.mapIndexed { index, element ->
                if (element == null) { // error
                    val subjectId = subjectIds[index]
                    LightSubjectAndEpisodes(
                        subject = LightSubjectInfo(
                            subjectId,
                            name = "错误 $subjectId: $errors",
                            nameCn = "错误 $subjectId: $errors",
                            imageLarge = "",
                        ),
                        episodes = emptyList(),
                    )
                } else {
                    BangumiSubjectGraphQLParser.parseLightSubjectAndEpisodes(element)
                }
            }
        }
    }


    override suspend fun patchSubjectCollection(subjectId: Int, payload: BangumiUserSubjectCollectionModifyPayload) {
        sessionManager.checkAccessBangumiApiNow()
        withContext(ioDispatcher) {
            api {
                postUserCollection(subjectId, payload)
                Unit
            }
        }
    }

    override suspend fun deleteSubjectCollection(subjectId: Int) {
        sessionManager.checkAccessBangumiApiNow()
        // TODO:  deleteSubjectCollection
    }

    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts> {
        TODO("subjectCollectionCountsFlow")
//        return sessionManager.username.filterNotNull().map { username ->
//            sessionManager.checkTokenNow()
//            val types = UnifiedCollectionType.entries - UnifiedCollectionType.NOT_COLLECTED
//            val totals = IntArray(types.size) { type ->
//                api {
//                    getUserCollectionsByUsername(
//                        username,
//                        subjectType = BangumiSubjectType.Anime,
//                        type = types[type].toSubjectCollectionType(),
//                        limit = 1, // we only need the total count. API requires at least 1
//                    ).body().total ?: 0
//                }
//            }
//            SubjectCollectionCounts(
//                wish = totals[UnifiedCollectionType.WISH.ordinal],
//                doing = totals[UnifiedCollectionType.DOING.ordinal],
//                done = totals[UnifiedCollectionType.DONE.ordinal],
//                onHold = totals[UnifiedCollectionType.ON_HOLD.ordinal],
//                dropped = totals[UnifiedCollectionType.DROPPED.ordinal],
//                total = totals.sum(),
//            )
//        }.flowOn(ioDispatcher)
    }

    override fun subjectCollectionById(subjectId: Int): Flow<BangumiUserSubjectCollection?> {
        return flow {
            emit(
                try {
                    TODO("subjectCollectionById")
//                    if (sessionManager.verifiedAccessToken.first() == null) {
//                        emit(null)
//                        return@flow
//                    }
//                    @OptIn(OpaqueSession::class)
//                    api { getUserCollection(sessionManager.username.first() ?: "-", subjectId).body() }
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        null
                    } else {
                        throw e
                    }
                },
            )
        }.flowOn(ioDispatcher)
    }

    override fun subjectCollectionTypeById(subjectId: Int): Flow<UnifiedCollectionType> {
        return flow {
            emit(
                try {
                    TODO("subjectCollectionTypeById")
//                    val username = sessionManager.username.first() ?: "-"
//                    api { getUserCollection(username, subjectId).body().type.toCollectionType() }
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        UnifiedCollectionType.NOT_COLLECTED
                    } else {
                        throw e
                    }
                },
            )
        }.flowOn(ioDispatcher)
    }
}


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


data class BatchSubjectDetails(
    val subjectInfo: SubjectInfo,
    val mainEpisodeCount: Int,
    val lightSubjectRelations: LightSubjectRelations,
)

data class LightSubjectRelations(
    val lightRelatedPersonInfoList: List<LightRelatedPersonInfo>,
    val lightRelatedCharacterInfoList: List<LightRelatedCharacterInfo>,
)

data class LightRelatedPersonInfo(
    val name: String,
    val position: PersonPosition,
)

data class LightRelatedCharacterInfo(
    val id: Int,
    val name: String,
    val nameCn: String,
    val role: CharacterRole,
)

data class BatchSubjectRelations(
    val subjectId: Int,
    val relatedCharacterInfoList: List<RelatedCharacterInfo>,
    val relatedPersonInfoList: List<RelatedPersonInfo>,
) {
    val allPersons
        get() = relatedCharacterInfoList.asSequence()
            .flatMap { it.character.actors } + relatedPersonInfoList.asSequence().map { it.personInfo }
}

internal fun BangumiUserSubjectCollection?.toSelfRatingInfo(): SelfRatingInfo {
    if (this == null) {
        return SelfRatingInfo.Empty
    }
    return SelfRatingInfo(
        score = rate,
        comment = comment.takeUnless { it.isNullOrBlank() },
        tags = tags,
        isPrivate = private,
    )
}

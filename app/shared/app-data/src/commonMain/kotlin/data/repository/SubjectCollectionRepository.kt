/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.compose.runtime.Immutable
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.persistent.database.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.SubjectCollectionEntity
import me.him188.ani.app.data.repository.Repository.Companion.defaultPagingConfig
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

typealias BangumiSubjectApi = DefaultApi

/**
 * 用户对一个条目的收藏情况
 */
@Immutable
data class SubjectCollectionInfo(
    val subjectId: Int,
    val type: UnifiedCollectionType,
    val subjectInfo: SubjectInfo,
)

class SubjectCollectionRepository(
    private val api: Flow<BangumiSubjectApi>,
    private val subjectRepository: SubjectRepository,
    private val dao: SubjectCollectionDao,
    private val usernameProvider: RepositoryUsernameProvider,
) : Repository {
    fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> {
        return dao.findById(subjectId).map { entity ->
            if (entity == null) {
                val response = api.first().getUserCollection("-", subjectId).body()
                dao.upsert(
                    SubjectCollectionEntity(
                        response.subjectId,
                        response.type.toCollectionType()
                    )
                )
                SubjectCollectionInfo(
                    subjectId,
                    response.type.toCollectionType(),
                    subjectRepository.getSubjectDetails(subjectId).subjectInfo,
                )
            } else {
                val subjectInfo = subjectRepository.getSubjectDetails(subjectId)
                entity.toSubjectCollectionInfo(subjectInfo.subjectInfo)
            }
        }
    }

    fun subjectCollectionPager(
        type: UnifiedCollectionType? = null, // null for all
        pagingConfig: PagingConfig = defaultPagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> = Pager(
        config = pagingConfig,
        initialKey = 0,
        remoteMediator = object : RemoteMediator<Int, SubjectCollectionInfo>() {
            override suspend fun initialize(): InitializeAction {
                if ((dao.lastUpdated() - currentTimeMillis()).milliseconds > 1.hours) {
                    return InitializeAction.LAUNCH_INITIAL_REFRESH
                }
                return InitializeAction.SKIP_INITIAL_REFRESH
            }

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, SubjectCollectionInfo>,
            ): MediatorResult {
                val offset = when (loadType) {
                    LoadType.REFRESH -> 0
                    LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                    LoadType.APPEND -> state.pages.size * state.config.pageSize
                }
                return try {
                    val username = usernameProvider.getOrThrow()
                    val resp = api.first().getUserCollectionsByUsername(
                        username,
                        type = type?.toSubjectCollectionType(),
                        limit = 30,
                        offset = offset,
                    ).body()
                    val items = resp.data?.map { it.toEntity() }
                    dao.upsert(items.orEmpty())
                    return MediatorResult.Success(endOfPaginationReached = items.isNullOrEmpty())
                } catch (e: RepositoryException) {
                    MediatorResult.Error(e)
                } catch (e: ResponseException) {
                    MediatorResult.Error(e)
                } catch (e: Exception) {
                    MediatorResult.Error(e)
                }
            }

        },
        pagingSourceFactory = {
            object : PagingSource<Int, SubjectCollectionInfo>() {
                override fun getRefreshKey(state: PagingState<Int, SubjectCollectionInfo>): Int? =
                    null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubjectCollectionInfo> {
                    val offset = params.key
                        ?: return LoadResult.Error(IllegalArgumentException("Key is null"))
                    return try {
                        val items = dao.getFlow(type, params.loadSize, offset = offset).first()
                        val subjectDetails =
                            subjectRepository.batchGetSubjectDetails(items.map { it.subjectId })
                        return LoadResult.Page(
                            items.mapNotNull { entity ->
                                val details =
                                    subjectDetails.find { it.subjectInfo.subjectId == entity.subjectId }
                                        ?: return@mapNotNull null

                                entity.toSubjectCollectionInfo(
                                    details.subjectInfo,
                                )
                            },
                            prevKey = offset,
                            nextKey = if (items.isEmpty()) null else offset + params.loadSize,
                        )
                    } catch (e: Exception) {
                        LoadResult.Error(e)
                    }
                }
            }
        },
    ).flow

    fun subjectCollectionInfoFlow(subjectId: Int): Flow<SubjectCollectionInfo> {
        return dao.findById(subjectId).map { entity ->
            if (entity == null) {
                val response = api.first().getUserCollection("-", subjectId).body()
                dao.upsert(
                    SubjectCollectionEntity(
                        response.subjectId,
                        response.type.toCollectionType()
                    )
                )
                SubjectCollectionInfo(
                    subjectId,
                    response.type.toCollectionType(),
                    subjectRepository.getSubjectDetails(subjectId).subjectInfo,
                )
            } else {
                val subjectInfo = subjectRepository.getSubjectDetails(subjectId)
                entity.toSubjectCollectionInfo(subjectInfo.subjectInfo)
            }
        }
    }

    fun getSubjectCollectionPager(
        subjectId: Int,
    ): Flow<PagingData<SubjectCollectionInfo>> {
        return Pager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = object : RemoteMediator<Unit, SubjectCollectionInfo>() {
                override suspend fun load(
                    loadType: LoadType,
                    state: PagingState<Unit, SubjectCollectionInfo>,
                ): MediatorResult {
                    when (loadType) {
                        LoadType.REFRESH -> {} // OK
                        LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                        LoadType.APPEND -> {
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )

                            // OK
                        }
                    }

                    return try {
                        val response = api.first().getUserCollection("-", subjectId).body()
                        dao.upsert(
                            SubjectCollectionEntity(
                                response.subjectId,
                                response.type.toCollectionType()
                            )
                        )
                        MediatorResult.Success(endOfPaginationReached = true)
                    } catch (e: Exception) {
                        MediatorResult.Error(e)
                    }
                }
            },
            pagingSourceFactory = {
                object : PagingSource<Unit, SubjectCollectionInfo>() {
                    override fun getRefreshKey(state: PagingState<Unit, SubjectCollectionInfo>): Unit? =
                        null

                    override suspend fun load(params: LoadParams<Unit>): LoadResult<Unit, SubjectCollectionInfo> {
                        return try {
                            val entity = dao.get(subjectId)
                            val subjectInfo = subjectRepository.getSubjectDetails(subjectId)
                            LoadResult.Page(
                                listOf(entity.toSubjectCollectionInfo(subjectInfo.subjectInfo)),
                                prevKey = null,
                                nextKey = null,
                            )
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                    }
                }
            },
        ).flow
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
                BangumiUserSubjectCollectionModifyPayload(type.toSubjectCollectionType())
            )
        }
    }

    private suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: BangumiUserSubjectCollectionModifyPayload,
    ) {
        api.first().postUserCollection(subjectId, payload)
        dao.updateType(subjectId, payload.type.toCollectionType())
    }

    private suspend fun deleteSubjectCollection(subjectId: Int) {
        // TODO: deleteSubjectCollection
    }
}

private fun BangumiUserSubjectCollection.toEntity() =
    SubjectCollectionEntity(subjectId, this.type.toCollectionType())

private fun SubjectCollectionEntity.toSubjectCollectionInfo(subjectInfo: SubjectInfo): SubjectCollectionInfo {
    return SubjectCollectionInfo(subjectId, type, subjectInfo)
}

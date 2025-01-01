/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import androidx.compose.ui.util.packInts
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.persistent.database.dao.EpisodeCommentDao
import me.him188.ani.app.data.persistent.database.dao.SubjectReviewDao
import me.him188.ani.app.data.persistent.database.entity.EpisodeCommentEntity
import me.him188.ani.app.data.persistent.database.entity.EpisodeCommentEntityWithReplies
import me.him188.ani.app.data.persistent.database.entity.SubjectReviewEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException

class BangumiCommentRepository(
    private val commentService: BangumiCommentService,
    private val episodeCommentDao: EpisodeCommentDao,
    private val subjectReviewDao: SubjectReviewDao,
) : Repository() {
    fun subjectEpisodeCommentsPager(episodeId: Int): Flow<PagingData<EpisodeComment>> {
        return Pager(
            config = defaultPagingConfig,
            initialKey = 0,
            remoteMediator = EpisodeCommentRemoteMediator(episodeId),
            pagingSourceFactory = {
                episodeCommentDao.filterByEpisodeIdPager(episodeId)
            },
        ).flow.map { page ->
            page.map { it.toInfo() }
        }
    }

    private inner class EpisodeCommentRemoteMediator<T : Any>(
        private val episodeId: Int,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = withContext(defaultDispatcher) {
            when (loadType) {
                LoadType.REFRESH -> {
                    try {
                        val items = commentService.getSubjectEpisodeComments(episodeId)
                            ?: return@withContext MediatorResult.Success(endOfPaginationReached = true)
                        episodeCommentDao.upsert(items.flatMap { it.toEntityWithReplies() }.toList())
                    } catch (e: Exception) {
                        return@withContext MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
                    }

                    return@withContext MediatorResult.Success(endOfPaginationReached = true)
                }

                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
            }
        }
    }


    fun subjectCommentsPager(subjectId: Int): Flow<PagingData<SubjectReview>> {
        return Pager(
            config = defaultPagingConfig,
            initialKey = 0,
            remoteMediator = SubjectReviewRemoteMediator(subjectId),
            pagingSourceFactory = {
                subjectReviewDao.filterBySubjectIdPager(subjectId)
            },
        ).flow.map { page ->
            page.map {
                it.toInfo()
            }
        }
    }

    suspend fun postEpisodeComment(
        episodeId: Int,
        content: String,
        cfTurnstileResponse: String,
        replyToCommentId: Int?,
    ) {
        try {
            commentService.postEpisodeComment(episodeId, content, cfTurnstileResponse, replyToCommentId)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private inner class SubjectReviewRemoteMediator<T : Any>(
        private val subjectId: Int,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = withContext(defaultDispatcher) {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastLoadedPage = state.pages.lastOrNull()
                    if (lastLoadedPage != null) {
                        lastLoadedPage.itemsBefore + lastLoadedPage.data.size
                    } else {
                        0
                    }
                }
            }

            try {
                val subjectReviews = commentService.getSubjectReviews(subjectId, offset, state.config.pageSize)
                    ?: return@withContext MediatorResult.Success(endOfPaginationReached = true)

                subjectReviewDao.upsert(subjectReviews.page.mapNotNull { it.toEntity(subjectId) })

                MediatorResult.Success(endOfPaginationReached = !subjectReviews.hasMore)
            } catch (e: Exception) {
                return@withContext MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }
    }
}

private fun EpisodeComment.toEntityWithReplies(): Sequence<EpisodeCommentEntity> {
    return sequence {
        yield(toEntity(null)) // 必须在 children 前面
        replies.forEach {
            yield(it.toEntity(commentId))
        }
    }.filterNotNull()
}

private fun EpisodeComment.toEntity(parentCommentId: Int?): EpisodeCommentEntity? {
    return EpisodeCommentEntity(
        episodeId,
        commentId = commentId,
        parentCommentId = parentCommentId,
        authorId = author?.id ?: return null,
        authorNickname = author.nickname ?: "",
        authorAvatarUrl = author.avatarUrl,
        createdAt = createdAt,
        content = content,
    )
}

private fun SubjectReview.toEntity(subjectId: Int): SubjectReviewEntity? {
    return SubjectReviewEntity(
        subjectId = subjectId,
        authorId = creator?.id ?: return null,
        content = content,
        updatedAt = updatedAt,
        rating = rating,
        authorNickname = creator.nickname ?: "",
        authorAvatarUrl = creator.avatarUrl,
    )
}

private fun SubjectReviewEntity.toInfo(): SubjectReview {
    return SubjectReview(
        id = packInts(subjectId, authorId),
        updatedAt = updatedAt,
        content = content,
        creator = UserInfo(
            id = authorId,
            username = null,
            nickname = authorNickname,
            avatarUrl = authorAvatarUrl,
        ),
        rating = rating,
    )
}

private fun EpisodeCommentEntityWithReplies.toInfo(): EpisodeComment {
    return entity.toInfo().copy(
        replies = replies.map { it.toInfo() },
    )
}

private fun EpisodeCommentEntity.toInfo(): EpisodeComment {
    return EpisodeComment(
        commentId = commentId,
        episodeId = episodeId,
        createdAt = createdAt,
        content = content,
        author = UserInfo(
            id = authorId,
            username = null,
            nickname = authorNickname,
            avatarUrl = authorAvatarUrl,
        ),
    )
}
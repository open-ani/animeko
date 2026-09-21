/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.person

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.person.PersonComment
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.app.data.network.toPersonComment
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult

/**
 * 人物/角色评论 (Ani 自有 + Bangumi 吐槽箱, 由服务端合并). 与 [me.him188.ani.app.data.repository.episode.EpisodeCommentRepository] 同构.
 */
class PersonCommentRepository(
    private val aniCommentService: AniPersonCommentService,
) : Repository() {
    /**
     * @param onBangumiUnavailable 首屏拿到了 Ani 评论但服务端没能取到 Bangumi 评论时调用一次
     */
    fun commentsPager(
        target: PersonCommentTarget,
        onBangumiUnavailable: () -> Unit = {},
    ): Flow<PagingData<PersonComment>> {
        return Pager(defaultPagingConfig) {
            PersonCommentPagingSource(
                target = target,
                aniCommentService = aniCommentService,
                onBangumiUnavailable = onBangumiUnavailable,
            )
        }.flow
    }

    suspend fun submitReaction(
        target: PersonCommentTarget,
        commentId: String,
        value: String,
        selected: Boolean,
    ) {
        if (selected) {
            aniCommentService.addReaction(target, commentId, value)
        } else {
            aniCommentService.removeReaction(target, commentId, value)
        }
    }

    /**
     * 对评论投票 (点赞/点踩). [vote] 为 `null` 表示取消投票.
     */
    suspend fun submitVote(
        target: PersonCommentTarget,
        commentId: String,
        vote: CommentVoteValue?,
    ) {
        aniCommentService.vote(target, commentId, vote)
    }
}

/**
 * 人物/角色评论翻页. Ani 与 Bangumi 的评论已由服务端合并排序好, 客户端只按游标顺序取.
 */
internal class PersonCommentPagingSource(
    private val target: PersonCommentTarget,
    private val aniCommentService: AniPersonCommentService,
    private val onBangumiUnavailable: () -> Unit = {},
) : PagingSource<String, PersonComment>() {
    override fun getRefreshKey(state: PagingState<String, PersonComment>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, PersonComment> {
        return runWrappingExceptionAsLoadResult {
            val response = aniCommentService.listComments(
                target = target,
                after = params.key,
                limit = params.loadSize.coerceAtMost(MAX_LIMIT),
            )
            // 只在首屏报一次, 免得每翻一页都提示
            if (response.bangumiUnavailable && params.key == null) {
                onBangumiUnavailable()
            }
            LoadResult.Page(
                data = response.items.map { it.toPersonComment() },
                prevKey = null,
                nextKey = response.nextCursor,
            )
        }
    }

    private companion object {
        /** 服务端 `limit` 的上限, 超过会 400 */
        const val MAX_LIMIT = 100
    }
}

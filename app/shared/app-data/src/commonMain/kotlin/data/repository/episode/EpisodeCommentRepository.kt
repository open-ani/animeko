/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.toEpisodeComment
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult

class EpisodeCommentRepository(
    private val aniCommentService: AniEpisodeCommentService,
) : Repository() {
    /**
     * @param onBangumiUnavailable 首屏拿到了 Ani 评论但服务端没能取到 Bangumi 评论时调用一次
     */
    fun subjectEpisodeCommentsPager(
        episodeId: Long,
        onBangumiUnavailable: () -> Unit = {},
    ): Flow<PagingData<EpisodeComment>> {
        return Pager(defaultPagingConfig) {
            EpisodeCommentPagingSource(
                episodeId = episodeId,
                aniCommentService = aniCommentService,
                onBangumiUnavailable = onBangumiUnavailable,
            )
        }.flow
    }

    /**
     * 评论详情 (thread) 的根评论, 不含回复 ([EpisodeComment.replies] 为空). [stableId] 是列表里的稳定 id.
     *
     * @return 评论不存在、已删除或不属于该剧集时为 `null`.
     */
    suspend fun getComment(episodeId: Long, stableId: String): EpisodeComment? {
        return aniCommentService.getEpisodeComment(episodeId, stableId)?.toEpisodeComment()
    }

    /**
     * 一条根评论的全部楼中楼回复, 发送时间正序.
     *
     * @param onTotal 加载首页时回调服务端给出的可见回复总数, 刷新时再次回调
     */
    fun repliesPager(
        episodeId: Long,
        stableId: String,
        onTotal: (Int) -> Unit = {},
    ): Flow<PagingData<EpisodeComment>> {
        return Pager(defaultPagingConfig) {
            EpisodeCommentRepliesPagingSource(
                episodeId = episodeId,
                rootStableId = stableId,
                aniCommentService = aniCommentService,
                onTotal = onTotal,
            )
        }.flow
    }

    suspend fun submitReaction(
        episodeId: Long,
        commentId: String,
        value: String,
        selected: Boolean,
    ) {
        if (selected) {
            aniCommentService.addEpisodeCommentReaction(episodeId, commentId, value)
        } else {
            aniCommentService.removeEpisodeCommentReaction(episodeId, commentId, value)
        }
    }

    /**
     * 对评论 (根评论或楼中楼回复) 投票 (点赞/点踩). [vote] 为 `null` 表示取消投票.
     */
    suspend fun submitVote(
        episodeId: Long,
        commentId: String,
        vote: CommentVoteValue?,
    ) {
        aniCommentService.voteEpisodeComment(episodeId, commentId, vote)
    }
}

/**
 * 一条根评论的回复翻页, 发送时间正序. 回复的来源与根评论相同, 由根评论稳定 id 的前缀得知 (回复的 DTO 没有来源字段).
 */
internal class EpisodeCommentRepliesPagingSource(
    private val episodeId: Long,
    private val rootStableId: String,
    private val aniCommentService: AniEpisodeCommentService,
    private val onTotal: (Int) -> Unit = {},
) : PagingSource<String, EpisodeComment>() {
    override fun getRefreshKey(state: PagingState<String, EpisodeComment>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, EpisodeComment> {
        return runWrappingExceptionAsLoadResult {
            val response = aniCommentService.listEpisodeCommentReplies(
                episodeId = episodeId,
                commentId = rootStableId,
                after = params.key,
                limit = params.loadSize.coerceAtMost(MAX_LIMIT),
            )
            if (params.key == null) {
                onTotal(response.total)
            }
            val source = if (rootStableId.startsWith(BANGUMI_PREFIX)) {
                EpisodeCommentSource.BANGUMI
            } else {
                EpisodeCommentSource.ANI
            }
            LoadResult.Page(
                data = response.items.map { it.toEpisodeComment(episodeId, source) },
                prevKey = null,
                nextKey = response.nextCursor,
            )
        }
    }

    private companion object {
        /** 服务端 `limit` 的上限, 超过会 400 */
        const val MAX_LIMIT = 100
        const val BANGUMI_PREFIX = "bangumi:"
    }
}

/**
 * 剧集评论翻页. Ani 与 Bangumi 的评论已由服务端合并排序好, 客户端只按游标顺序取.
 */
internal class EpisodeCommentPagingSource(
    private val episodeId: Long,
    private val aniCommentService: AniEpisodeCommentService,
    private val onBangumiUnavailable: () -> Unit = {},
) : PagingSource<String, EpisodeComment>() {
    override fun getRefreshKey(state: PagingState<String, EpisodeComment>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, EpisodeComment> {
        return runWrappingExceptionAsLoadResult {
            val response = aniCommentService.listEpisodeComments(
                episodeId = episodeId,
                after = params.key,
                limit = params.loadSize.coerceAtMost(MAX_LIMIT),
            )
            // 只在首屏报一次, 免得每翻一页都提示
            if (response.bangumiUnavailable && params.key == null) {
                onBangumiUnavailable()
            }
            LoadResult.Page(
                data = response.items.map { it.toEpisodeComment() },
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

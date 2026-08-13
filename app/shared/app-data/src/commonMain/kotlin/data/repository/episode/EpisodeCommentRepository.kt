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
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.BangumiReplyRelationService
import me.him188.ani.app.data.network.toEpisodeComment
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult

class EpisodeCommentRepository(
    private val aniCommentService: AniEpisodeCommentService,
    /** 补"回复了谁"的那层关系, 见 [BangumiReplyRelationService]; `null` 则只靠正文引用推断. */
    private val replyRelationService: BangumiReplyRelationService? = null,
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
                replyRelationService = replyRelationService,
                onBangumiUnavailable = onBangumiUnavailable,
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
}

/**
 * 剧集评论翻页. Ani 与 Bangumi 的评论已由服务端合并排序好, 客户端只按游标顺序取.
 */
internal class EpisodeCommentPagingSource(
    private val episodeId: Long,
    private val aniCommentService: AniEpisodeCommentService,
    private val replyRelationService: BangumiReplyRelationService? = null,
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
            val comments = response.items.map { it.toEpisodeComment() }
            LoadResult.Page(
                // "回复了谁"服务端不给, 能连上 Bangumi 就补真值, 补不上就用正文引用推断出来的那份
                data = replyRelationService?.fillInReplyTargets(episodeId, comments) ?: comments,
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

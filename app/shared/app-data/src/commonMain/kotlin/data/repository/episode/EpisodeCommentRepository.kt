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
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.toEpisodeComment
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult

class EpisodeCommentRepository(
    private val aniCommentService: AniEpisodeCommentService,
    private val bangumiCommentService: BangumiCommentService,
) : Repository() {
    fun subjectEpisodeCommentsPager(episodeId: Long): Flow<PagingData<EpisodeComment>> {
        return Pager(defaultPagingConfig) {
            DualSourceEpisodeCommentPagingSource(
                episodeId = episodeId,
                aniCommentService = aniCommentService,
                bangumiCommentService = bangumiCommentService,
                pageSize = defaultPagingConfig.pageSize,
            )
        }.flow
    }

    suspend fun submitReaction(
        episodeId: Long,
        source: EpisodeCommentSource,
        commentId: String,
        value: String,
        selected: Boolean,
    ) {
        when (source) {
            EpisodeCommentSource.ANI -> {
                if (selected) {
                    aniCommentService.addEpisodeCommentReaction(episodeId, commentId, value)
                } else {
                    aniCommentService.removeEpisodeCommentReaction(episodeId, commentId, value)
                }
            }

            EpisodeCommentSource.BANGUMI -> {
                bangumiCommentService.submitEpisodeCommentReaction(commentId, value, selected)
            }
        }
    }
}

internal class DualSourceEpisodeCommentPagingSource(
    private val episodeId: Long,
    private val aniCommentService: AniEpisodeCommentService,
    private val bangumiCommentService: BangumiCommentService,
    private val pageSize: Int,
) : PagingSource<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>() {
    private var initialized = false
    private var bangumiComments: List<EpisodeComment> = emptyList()
    private var bangumiOffset: Int = 0
    private var aniNextOffset: Int = 0
    private var aniTotal: Int = Int.MAX_VALUE
    private val aniBuffer = ArrayDeque<EpisodeComment>()

    override fun getRefreshKey(state: PagingState<Cursor, EpisodeComment>): Cursor? = null

    override suspend fun load(params: LoadParams<Cursor>): LoadResult<Cursor, EpisodeComment> {
        return runWrappingExceptionAsLoadResult {
            if (params is LoadParams.Refresh || !initialized) {
                reset()
                initialize()
            }

            val data = mutableListOf<EpisodeComment>()
            while (data.size < params.loadSize) {
                ensureAniCandidate(params.loadSize)

                val nextAni = aniBuffer.firstOrNull()
                val nextBangumi = bangumiComments.getOrNull(bangumiOffset)
                val nextComment = newerOf(nextAni, nextBangumi) ?: break

                if (nextComment === nextAni) {
                    data += aniBuffer.removeFirst()
                } else {
                    data += nextBangumi!!
                    bangumiOffset += 1
                }
            }

            val hasMore = aniBuffer.isNotEmpty() || aniNextOffset < aniTotal || bangumiOffset < bangumiComments.size
            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = if (hasMore) Cursor(aniOffset = aniNextOffset, bangumiOffset = bangumiOffset) else null,
            )
        }
    }

    private suspend fun initialize() {
        bangumiComments = bangumiCommentService.getSubjectEpisodeComments(episodeId)
            .orEmpty()
            .sortedWith(COMMENT_COMPARATOR)
        initialized = true
    }

    private suspend fun ensureAniCandidate(loadSize: Int) {
        while (aniBuffer.isEmpty() && aniNextOffset < aniTotal) {
            val response = aniCommentService.listEpisodeComments(
                episodeId = episodeId,
                offset = aniNextOffset,
                limit = maxOf(pageSize, loadSize),
            )
            aniTotal = response.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            val items = response.items
                .map { it.toEpisodeComment() }
                .sortedWith(COMMENT_COMPARATOR)
            aniBuffer.addAll(items)
            aniNextOffset += items.size

            if (items.isEmpty()) {
                aniNextOffset = aniTotal
                break
            }
        }
    }

    private fun newerOf(
        ani: EpisodeComment?,
        bangumi: EpisodeComment?,
    ): EpisodeComment? {
        return when {
            ani == null -> bangumi
            bangumi == null -> ani
            COMMENT_COMPARATOR.compare(ani, bangumi) <= 0 -> ani
            else -> bangumi
        }
    }

    private fun reset() {
        initialized = false
        bangumiComments = emptyList()
        bangumiOffset = 0
        aniNextOffset = 0
        aniTotal = Int.MAX_VALUE
        aniBuffer.clear()
    }

    data class Cursor(
        val aniOffset: Int,
        val bangumiOffset: Int,
    )

    private companion object {
        val COMMENT_COMPARATOR = compareByDescending<EpisodeComment> { it.createdAt }
            .thenBy { if (it.source == EpisodeCommentSource.ANI) 0 else 1 }
            .thenByDescending { it.stableId }
    }
}

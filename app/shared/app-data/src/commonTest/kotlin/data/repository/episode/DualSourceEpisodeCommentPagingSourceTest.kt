/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.models.AniEpisodeComment
import me.him188.ani.client.models.AniEpisodeCommentsResponse
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DualSourceEpisodeCommentPagingSourceTest {
    @Test
    fun `refresh merges both sources by timestamp then source then stable id`() = runTest {
        val source = createSource(
            aniPages = mapOf(
                0 to AniEpisodeCommentsResponse(
                    total = 3,
                    items = listOf(
                        aniComment(id = "ani:a", createdAt = 100),
                        aniComment(id = "ani:z", createdAt = 100),
                        aniComment(id = "ani:m", createdAt = 90),
                    ),
                ),
            ),
            bangumiComments = listOf(
                bangumiComment(id = "bangumi:2", createdAt = 100),
                bangumiComment(id = "bangumi:9", createdAt = 100),
                bangumiComment(id = "bangumi:1", createdAt = 80),
            ),
            pageSize = 10,
        )

        val result = source.load(refresh(loadSize = 10))
        val page = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(result)

        assertEquals(
            listOf("ani:z", "ani:a", "bangumi:9", "bangumi:2", "ani:m", "bangumi:1"),
            page.data.map { it.stableId },
        )
        assertNull(page.nextKey)
    }

    @Test
    fun `append advances both cursors and prefetches ani when current page needs more data`() = runTest {
        val aniCalls = mutableListOf<Pair<Int, Int>>()
        val bangumiCalls = mutableListOf<Long>()
        val source = createSource(
            aniCommentService = object : AniEpisodeCommentService(UnusedEpisodesApi) {
                override suspend fun listEpisodeComments(
                    episodeId: Long,
                    offset: Int,
                    limit: Int,
                ): AniEpisodeCommentsResponse {
                    aniCalls += offset to limit
                    return when (offset) {
                        0 -> AniEpisodeCommentsResponse(
                            total = 4,
                            items = listOf(
                                aniComment(id = "ani:100", createdAt = 100),
                                aniComment(id = "ani:090", createdAt = 90),
                            ),
                        )

                        2 -> AniEpisodeCommentsResponse(
                            total = 4,
                            items = listOf(
                                aniComment(id = "ani:080", createdAt = 80),
                                aniComment(id = "ani:070", createdAt = 70),
                            ),
                        )

                        else -> AniEpisodeCommentsResponse(total = 4, items = emptyList())
                    }
                }
            },
            bangumiCommentService = object : BangumiCommentService {
                override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int) = error("unused")

                override suspend fun getSubjectEpisodeComments(episodeId: Long): List<EpisodeComment> {
                    bangumiCalls += episodeId
                    return listOf(
                        bangumiComment(id = "bangumi:095", createdAt = 95),
                        bangumiComment(id = "bangumi:085", createdAt = 85),
                        bangumiComment(id = "bangumi:060", createdAt = 60),
                    )
                }

                override suspend fun postEpisodeComment(
                    episodeId: Long,
                    content: String,
                    cfTurnstileResponse: String,
                    replyToCommentId: Int?,
                ) = error("unused")

                override suspend fun submitEpisodeCommentReaction(
                    commentId: String,
                    value: String,
                    selected: Boolean,
                ) = error("unused")
            },
            pageSize = 2,
        )

        val first = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(refresh(loadSize = 2)),
        )
        assertEquals(listOf("ani:100", "bangumi:095"), first.data.map { it.stableId })
        assertEquals(DualSourceEpisodeCommentPagingSource.Cursor(aniOffset = 2, bangumiOffset = 1), first.nextKey)

        val second = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(append(first.nextKey!!, loadSize = 2)),
        )
        assertEquals(listOf("ani:090", "bangumi:085"), second.data.map { it.stableId })
        assertEquals(DualSourceEpisodeCommentPagingSource.Cursor(aniOffset = 4, bangumiOffset = 2), second.nextKey)

        val third = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(append(second.nextKey!!, loadSize = 2)),
        )
        assertEquals(listOf("ani:080", "ani:070"), third.data.map { it.stableId })
        assertEquals(DualSourceEpisodeCommentPagingSource.Cursor(aniOffset = 4, bangumiOffset = 2), third.nextKey)

        val fourth = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(append(third.nextKey!!, loadSize = 2)),
        )
        assertEquals(listOf("bangumi:060"), fourth.data.map { it.stableId })
        assertNull(fourth.nextKey)

        assertEquals(listOf(99L), bangumiCalls)
        assertEquals(listOf(0 to 2, 2 to 2), aniCalls)
    }

    @Test
    fun `refresh after append resets internal cursors and reinitializes both sources`() = runTest {
        val aniOffsets = mutableListOf<Int>()
        var bangumiRequestCount = 0
        val source = createSource(
            aniCommentService = object : AniEpisodeCommentService(UnusedEpisodesApi) {
                override suspend fun listEpisodeComments(
                    episodeId: Long,
                    offset: Int,
                    limit: Int,
                ): AniEpisodeCommentsResponse {
                    aniOffsets += offset
                    return when (offset) {
                        0 -> AniEpisodeCommentsResponse(
                            total = 3,
                            items = listOf(
                                aniComment(id = "ani:3", createdAt = 300),
                                aniComment(id = "ani:2", createdAt = 200),
                            ),
                        )

                        2 -> AniEpisodeCommentsResponse(
                            total = 3,
                            items = listOf(aniComment(id = "ani:1", createdAt = 100)),
                        )

                        else -> AniEpisodeCommentsResponse(total = 3, items = emptyList())
                    }
                }
            },
            bangumiCommentService = object : BangumiCommentService {
                override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int) = error("unused")

                override suspend fun getSubjectEpisodeComments(episodeId: Long): List<EpisodeComment> {
                    bangumiRequestCount += 1
                    return listOf(bangumiComment(id = "bangumi:250", createdAt = 250))
                }

                override suspend fun postEpisodeComment(
                    episodeId: Long,
                    content: String,
                    cfTurnstileResponse: String,
                    replyToCommentId: Int?,
                ) = error("unused")

                override suspend fun submitEpisodeCommentReaction(
                    commentId: String,
                    value: String,
                    selected: Boolean,
                ) = error("unused")
            },
            pageSize = 2,
        )

        val firstRefresh = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(refresh(loadSize = 2)),
        )
        assertEquals(listOf("ani:3", "bangumi:250"), firstRefresh.data.map { it.stableId })

        source.load(append(assertNotNull(firstRefresh.nextKey), loadSize = 2))

        val secondRefresh = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(
            source.load(refresh(loadSize = 2)),
        )
        assertEquals(listOf("ani:3", "bangumi:250"), secondRefresh.data.map { it.stableId })
        assertEquals(listOf(0, 2, 0), aniOffsets)
        assertEquals(2, bangumiRequestCount)
    }

    @Test
    fun `empty ani batch does not leave dangling next key`() = runTest {
        val source = createSource(
            aniPages = mapOf(
                0 to AniEpisodeCommentsResponse(total = 5, items = emptyList()),
            ),
            bangumiComments = null,
            pageSize = 3,
        )

        val result = source.load(refresh(loadSize = 3))
        val page = assertIs<PagingSource.LoadResult.Page<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(result)

        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }

    @Test
    fun `service exceptions are wrapped as repository errors`() = runTest {
        val source = createSource(
            aniCommentService = object : AniEpisodeCommentService(UnusedEpisodesApi) {
                override suspend fun listEpisodeComments(
                    episodeId: Long,
                    offset: Int,
                    limit: Int,
                ): AniEpisodeCommentsResponse {
                    throw IOException("boom")
                }
            },
            bangumiCommentService = object : BangumiCommentService {
                override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int) = error("unused")

                override suspend fun getSubjectEpisodeComments(episodeId: Long): List<EpisodeComment> = emptyList()

                override suspend fun postEpisodeComment(
                    episodeId: Long,
                    content: String,
                    cfTurnstileResponse: String,
                    replyToCommentId: Int?,
                ) = error("unused")

                override suspend fun submitEpisodeCommentReaction(
                    commentId: String,
                    value: String,
                    selected: Boolean,
                ) = error("unused")
            },
            pageSize = 2,
        )

        val result = source.load(refresh(loadSize = 2))
        val error = assertIs<PagingSource.LoadResult.Error<DualSourceEpisodeCommentPagingSource.Cursor, EpisodeComment>>(result)

        assertIs<RepositoryNetworkException>(error.throwable)
    }

    private fun createSource(
        aniPages: Map<Int, AniEpisodeCommentsResponse> = emptyMap(),
        bangumiComments: List<EpisodeComment>? = emptyList(),
        pageSize: Int = 2,
        aniCommentService: AniEpisodeCommentService = object : AniEpisodeCommentService(UnusedEpisodesApi) {
            override suspend fun listEpisodeComments(
                episodeId: Long,
                offset: Int,
                limit: Int,
            ): AniEpisodeCommentsResponse {
                return aniPages[offset] ?: AniEpisodeCommentsResponse(total = 0, items = emptyList())
            }
        },
        bangumiCommentService: BangumiCommentService = object : BangumiCommentService {
            override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int) = error("unused")

            override suspend fun getSubjectEpisodeComments(episodeId: Long): List<EpisodeComment>? = bangumiComments

            override suspend fun postEpisodeComment(
                episodeId: Long,
                content: String,
                cfTurnstileResponse: String,
                replyToCommentId: Int?,
            ) = error("unused")

            override suspend fun submitEpisodeCommentReaction(
                commentId: String,
                value: String,
                selected: Boolean,
            ) = error("unused")
        },
    ): DualSourceEpisodeCommentPagingSource {
        return DualSourceEpisodeCommentPagingSource(
            episodeId = 99L,
            aniCommentService = aniCommentService,
            bangumiCommentService = bangumiCommentService,
            pageSize = pageSize,
        )
    }

    private fun aniComment(id: String, createdAt: Long): AniEpisodeComment {
        return AniEpisodeComment(
            id = id,
            sourceCommentId = id.removePrefix("ani:"),
            episodeId = 99L,
            contentBbcode = id,
            createdAtMillis = createdAt,
            replyCount = 0,
            briefReplies = emptyList(),
            reactions = emptyList(),
            canReply = true,
        )
    }

    private fun bangumiComment(id: String, createdAt: Long): EpisodeComment {
        return EpisodeComment(
            stableId = id,
            source = EpisodeCommentSource.BANGUMI,
            sourceCommentId = id.removePrefix("bangumi:"),
            commentId = id.removePrefix("bangumi:"),
            episodeId = 99L,
            createdAt = createdAt,
            content = id,
            author = null,
            canReply = false,
        )
    }

    private fun refresh(loadSize: Int): PagingSource.LoadParams.Refresh<DualSourceEpisodeCommentPagingSource.Cursor> {
        return PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = loadSize,
            placeholdersEnabled = false,
        )
    }

    private fun append(
        key: DualSourceEpisodeCommentPagingSource.Cursor,
        loadSize: Int,
    ): PagingSource.LoadParams.Append<DualSourceEpisodeCommentPagingSource.Cursor> {
        return PagingSource.LoadParams.Append(
            key = key,
            loadSize = loadSize,
            placeholdersEnabled = false,
        )
    }
}

private object UnusedEpisodesApi : ApiInvoker<EpisodesAniApi> {
    override suspend fun <R> invoke(action: suspend EpisodesAniApi.() -> R): R {
        error("Unused in test")
    }
}

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
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.models.AniEpisodeComment
import me.him188.ani.client.models.AniEpisodeCommentSource
import me.him188.ani.client.models.AniEpisodeCommentsResponse
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 服务端已把 Ani 与 Bangumi 评论合并排序好, 客户端只负责按游标顺序取.
 */
class EpisodeCommentPagingSourceTest {
    @Test
    fun `refresh keeps server order and carries source through`() = runTest {
        val source = createSource(
            pages = mapOf(
                null to AniEpisodeCommentsResponse(
                    total = 2,
                    items = listOf(comment("ani:a", 100), comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
        )

        val page = assertIs<Page>(source.load(refresh(30)))
        assertEquals(listOf("ani:a", "bangumi:1"), page.data.map { it.stableId })
        assertEquals(
            listOf(EpisodeCommentSource.ANI, EpisodeCommentSource.BANGUMI),
            page.data.map { it.source },
        )
        assertNull(page.nextKey)
    }

    @Test
    fun `append passes the previous cursor as after`() = runTest {
        val requested = mutableListOf<String?>()
        val source = createSource(
            pages = mapOf(
                null to AniEpisodeCommentsResponse(
                    total = 3,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = false,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to AniEpisodeCommentsResponse(
                    total = 3,
                    items = listOf(comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onRequest = { requested += it },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals("100:ani:a", first.nextKey)

        val second = assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(listOf("bangumi:1"), second.data.map { it.stableId })
        assertNull(second.nextKey)
        assertEquals(listOf(null, "100:ani:a"), requested)
    }

    @Test
    fun `reports bangumi unavailable once on the first page only`() = runTest {
        var unavailableCount = 0
        val source = createSource(
            pages = mapOf(
                null to AniEpisodeCommentsResponse(
                    total = 2,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = true,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to AniEpisodeCommentsResponse(
                    total = 2,
                    items = listOf(comment("ani:b", 90)),
                    bangumiUnavailable = true,
                    nextCursor = null,
                ),
            ),
            onBangumiUnavailable = { unavailableCount++ },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals(1, unavailableCount)
        // Ani 评论照常返回, 不是加载失败
        assertEquals(listOf("ani:a"), first.data.map { it.stableId })

        source.load(append(first.nextKey!!, 30))
        // 翻页时不再重复提示
        assertEquals(1, unavailableCount)
    }

    @Test
    fun `clamps load size to the server limit`() = runTest {
        val limits = mutableListOf<Int>()
        val source = createSource(
            pages = mapOf(
                null to AniEpisodeCommentsResponse(
                    total = 0,
                    items = emptyList(),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onLimit = { limits += it },
        )

        source.load(refresh(300))
        assertEquals(listOf(100), limits)
    }

    private fun createSource(
        pages: Map<String?, AniEpisodeCommentsResponse>,
        onRequest: (String?) -> Unit = {},
        onLimit: (Int) -> Unit = {},
        onBangumiUnavailable: () -> Unit = {},
    ): EpisodeCommentPagingSource {
        return EpisodeCommentPagingSource(
            episodeId = 99L,
            aniCommentService = object : AniEpisodeCommentService(UnusedEpisodesApi) {
                override suspend fun listEpisodeComments(
                    episodeId: Long,
                    after: String?,
                    limit: Int,
                ): AniEpisodeCommentsResponse {
                    onRequest(after)
                    onLimit(limit)
                    return pages[after]
                        ?: AniEpisodeCommentsResponse(
                            total = 0,
                            items = emptyList(),
                            bangumiUnavailable = false,
                            nextCursor = null,
                        )
                }
            },
            onBangumiUnavailable = onBangumiUnavailable,
        )
    }

    private fun comment(id: String, createdAt: Long, bangumi: Boolean = false): AniEpisodeComment {
        return AniEpisodeComment(
            id = id,
            sourceCommentId = id.substringAfter(':'),
            episodeId = 99L,
            contentBbcode = id,
            createdAtMillis = createdAt,
            replyCount = 0,
            briefReplies = emptyList(),
            reactions = emptyList(),
            canReply = !bangumi,
            source = if (bangumi) AniEpisodeCommentSource.BANGUMI else AniEpisodeCommentSource.ANIMEKO,
            likeCount = 0,
        )
    }

    private fun refresh(loadSize: Int) = PagingSource.LoadParams.Refresh<String>(
        key = null,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )

    private fun append(key: String, loadSize: Int) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )
}

private typealias Page = PagingSource.LoadResult.Page<String, EpisodeComment>

private object UnusedEpisodesApi : ApiInvoker<EpisodesAniApi> {
    override suspend fun <R> invoke(action: suspend EpisodesAniApi.() -> R): R {
        error("Unused in test")
    }
}

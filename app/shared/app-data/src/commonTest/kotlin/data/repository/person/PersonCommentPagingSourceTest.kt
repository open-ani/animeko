/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.person

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.person.PersonComment
import me.him188.ani.app.data.models.person.PersonCommentSource
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.client.apis.CharactersAniApi
import me.him188.ani.client.apis.PersonsAniApi
import me.him188.ani.client.models.AniCommentVoteValue
import me.him188.ani.client.models.AniEpisodeCommentAuthor
import me.him188.ani.client.models.AniEpisodeCommentReaction
import me.him188.ani.client.models.AniPersonComment
import me.him188.ani.client.models.AniPersonCommentReply
import me.him188.ani.client.models.AniPersonCommentSource
import me.him188.ani.client.models.AniPersonCommentsResponse
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 服务端已把 Ani 与 Bangumi 的人物/角色评论合并排序好, 客户端只负责按游标顺序取.
 */
class PersonCommentPagingSourceTest {
    @Test
    fun `refresh keeps server order and maps fields`() = runTest {
        val source = createSource(
            pages = mapOf(
                null to AniPersonCommentsResponse(
                    total = 2,
                    items = listOf(
                        comment(
                            "ani:a", 100,
                            replies = listOf(reply("ani:a1", 110)),
                            reactions = listOf(AniEpisodeCommentReaction(value = "bgm1", count = 2, selected = true)),
                            likeCount = 3,
                            selfVote = AniCommentVoteValue.LIKE,
                        ),
                        comment("bangumi:1", 90, bangumi = true),
                    ),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
        )

        val page = assertIs<Page>(source.load(refresh(30)))
        assertEquals(listOf("ani:a", "bangumi:1"), page.data.map { it.stableId })
        assertEquals(listOf(PersonCommentSource.ANI, PersonCommentSource.BANGUMI), page.data.map { it.source })
        assertNull(page.nextKey)

        val ani = page.data[0]
        assertEquals("a", ani.sourceCommentId)
        assertEquals(true, ani.canReply)
        assertEquals(3, ani.likeCount)
        assertEquals(CommentVoteValue.LIKE, ani.selfVote)
        assertEquals("bgm1", ani.reactions.single().value)
        assertEquals(true, ani.reactions.single().selected)
        assertEquals("ani:a1", ani.replies.single().stableId)
        // 楼中楼继承根评论的来源, 且不可再回复
        assertEquals(PersonCommentSource.ANI, ani.replies.single().source)
        assertEquals(false, ani.replies.single().canReply)
        assertEquals("u", ani.author?.id)

        val bangumi = page.data[1]
        assertEquals(false, bangumi.canReply)
        assertEquals(0, bangumi.likeCount)
    }

    @Test
    fun `append passes the previous cursor as after and uses the character api for characters`() = runTest {
        val requested = mutableListOf<Pair<PersonCommentTarget, String?>>()
        val source = createSource(
            target = PersonCommentTarget.Character(5),
            pages = mapOf(
                null to AniPersonCommentsResponse(
                    total = 3,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = false,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to AniPersonCommentsResponse(
                    total = 3,
                    items = listOf(comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onRequest = { target, after -> requested += target to after },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals("100:ani:a", first.nextKey)

        val second = assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(listOf("bangumi:1"), second.data.map { it.stableId })
        assertNull(second.nextKey)
        assertEquals(
            listOf<Pair<PersonCommentTarget, String?>>(
                PersonCommentTarget.Character(5) to null,
                PersonCommentTarget.Character(5) to "100:ani:a",
            ),
            requested,
        )
    }

    @Test
    fun `reports bangumi unavailable once on the first page only`() = runTest {
        var unavailableCount = 0
        val source = createSource(
            pages = mapOf(
                null to AniPersonCommentsResponse(
                    total = 2,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = true,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to AniPersonCommentsResponse(
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
        assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(1, unavailableCount)
    }

    @Test
    fun `load size is capped to the server limit`() = runTest {
        var requestedLimit: Int? = null
        val source = createSource(
            pages = mapOf(
                null to AniPersonCommentsResponse(total = 0, items = emptyList(), bangumiUnavailable = false),
            ),
            onLimit = { requestedLimit = it },
        )

        assertIs<Page>(source.load(refresh(500)))
        assertEquals(100, requestedLimit)
    }

    @Test
    fun `network failure becomes a load error`() = runTest {
        val source = createSource(pages = emptyMap())
        assertIs<PagingSource.LoadResult.Error<String, PersonComment>>(source.load(refresh(30)))
    }

    private fun createSource(
        target: PersonCommentTarget = PersonCommentTarget.Person(1),
        pages: Map<String?, AniPersonCommentsResponse>,
        onRequest: (PersonCommentTarget, String?) -> Unit = { _, _ -> },
        onLimit: (Int) -> Unit = {},
        onBangumiUnavailable: () -> Unit = {},
    ): PersonCommentPagingSource {
        val service = object : AniPersonCommentService(UnusedPersonsApi, UnusedCharactersApi) {
            override suspend fun listComments(
                target: PersonCommentTarget,
                after: String?,
                limit: Int,
            ): AniPersonCommentsResponse {
                onRequest(target, after)
                onLimit(limit)
                return pages[after] ?: throw kotlinx.io.IOException("no page for $after")
            }
        }
        return PersonCommentPagingSource(target, service, onBangumiUnavailable)
    }

    private fun comment(
        id: String,
        createdAtMillis: Long,
        bangumi: Boolean = false,
        replies: List<AniPersonCommentReply> = emptyList(),
        reactions: List<AniEpisodeCommentReaction> = emptyList(),
        likeCount: Int = 0,
        selfVote: AniCommentVoteValue? = null,
    ) = AniPersonComment(
        id = id,
        sourceCommentId = id.substringAfter(':'),
        contentBbcode = "content $id",
        createdAtMillis = createdAtMillis,
        replyCount = replies.size,
        canReply = !bangumi,
        author = AniEpisodeCommentAuthor(id = "u", nickname = "nick", avatarUrl = null),
        briefReplies = replies,
        reactions = reactions,
        source = if (bangumi) AniPersonCommentSource.BANGUMI else AniPersonCommentSource.ANIMEKO,
        likeCount = likeCount,
        selfVote = selfVote,
    )

    private fun reply(id: String, createdAtMillis: Long) = AniPersonCommentReply(
        id = id,
        sourceCommentId = id.substringAfter(':'),
        contentBbcode = "reply $id",
        createdAtMillis = createdAtMillis,
        author = AniEpisodeCommentAuthor(id = "u2", nickname = "nick2", avatarUrl = null),
        reactions = emptyList(),
    )

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

private typealias Page = PagingSource.LoadResult.Page<String, PersonComment>

private object UnusedPersonsApi : ApiInvoker<PersonsAniApi> {
    override suspend fun <R> invoke(action: suspend PersonsAniApi.() -> R): R {
        error("Unused in test")
    }
}

private object UnusedCharactersApi : ApiInvoker<CharactersAniApi> {
    override suspend fun <R> invoke(action: suspend CharactersAniApi.() -> R): R {
        error("Unused in test")
    }
}

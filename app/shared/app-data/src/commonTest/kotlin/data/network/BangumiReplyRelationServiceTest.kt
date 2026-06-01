/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class BangumiReplyRelationServiceTest {
    private var nowMillis = 0L
    private var fetchCount = 0
    private var relations: Map<String, String>? = mapOf("r2" to "r1")

    private fun createService() = BangumiReplyRelationService(
        nowMillis = { nowMillis },
        fetchRelations = {
            fetchCount++
            relations
        },
    )

    @Test
    fun `补上关系`() = runTest {
        val result = createService().fillInReplyTargets(1, listOf(bangumiComment()))
        assertNull(result[0].replies[0].replyToCommentId)
        assertEquals("r1", result[0].replies[1].replyToCommentId)
    }

    @Test
    fun `不覆盖已经从正文引用认出来的`() = runTest {
        relations = mapOf("r2" to "r-别的")
        val comments = listOf(bangumiComment(secondReplyTarget = "r1"))
        val result = createService().fillInReplyTargets(1, comments)
        assertEquals("r1", result[0].replies[1].replyToCommentId)
    }

    @Test
    fun `没有需要补的就不发请求`() = runTest {
        val service = createService()
        // Ani 来源
        service.fillInReplyTargets(1, listOf(bangumiComment().copy(source = EpisodeCommentSource.ANI)))
        // 只有一条回复
        service.fillInReplyTargets(1, listOf(bangumiComment().let { it.copy(replies = it.replies.take(1)) }))
        // 全部已经认出来了
        service.fillInReplyTargets(1, listOf(bangumiComment(firstReplyTarget = "r0", secondReplyTarget = "r1")))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `同一集只取一次`() = runTest {
        val service = createService()
        service.fillInReplyTargets(1, listOf(bangumiComment()))
        service.fillInReplyTargets(1, listOf(bangumiComment()))
        assertEquals(1, fetchCount)
    }

    @Test
    fun `取不到时退避, 到期后还会再试`() = runTest {
        relations = null
        val service = createService()
        service.fillInReplyTargets(1, listOf(bangumiComment()))
        assertEquals(1, fetchCount)

        // 退避期内不再打扰它 (否则每翻一页都要等一次超时)
        nowMillis += 1.minutes.inWholeMilliseconds
        service.fillInReplyTargets(1, listOf(bangumiComment()))
        assertEquals(1, fetchCount)

        // 但失败不能被记成"这一集没有关系" —— 到期后必须重来一次
        nowMillis += 10.minutes.inWholeMilliseconds
        relations = mapOf("r2" to "r1")
        val result = service.fillInReplyTargets(1, listOf(bangumiComment()))
        assertEquals(2, fetchCount)
        assertEquals("r1", result[0].replies[1].replyToCommentId)
    }

    @Test
    fun `换一集重新取`() = runTest {
        val service = createService()
        service.fillInReplyTargets(1, listOf(bangumiComment()))
        service.fillInReplyTargets(2, listOf(bangumiComment()))
        assertEquals(2, fetchCount)
    }

    private fun bangumiComment(
        firstReplyTarget: String? = null,
        secondReplyTarget: String? = null,
    ) = EpisodeComment(
        stableId = "c1",
        source = EpisodeCommentSource.BANGUMI,
        sourceCommentId = "c1",
        commentId = "c1",
        episodeId = 1,
        createdAt = 0,
        content = "主楼",
        author = null,
        replies = listOf(
            reply("r1", firstReplyTarget),
            reply("r2", secondReplyTarget),
        ),
    )

    private fun reply(id: String, replyToCommentId: String?) = EpisodeComment(
        stableId = id,
        source = EpisodeCommentSource.BANGUMI,
        sourceCommentId = id,
        commentId = id,
        episodeId = 1,
        createdAt = 0,
        content = "回复",
        author = null,
        replyToCommentId = replyToCommentId,
    )
}

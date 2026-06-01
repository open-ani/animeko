/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import me.him188.ani.client.models.AniEpisodeComment
import me.him188.ani.client.models.AniEpisodeCommentAuthor
import me.him188.ani.client.models.AniEpisodeCommentReply
import me.him188.ani.client.models.AniEpisodeCommentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 楼内回复的"回复给谁"是从正文开头那条引用反推的 (接口不带回复关系), 见
 * [quotedAuthorNicknameOrNull] 与 `withReplyTargets`.
 */
class AniEpisodeCommentMappingTest {
    @Test
    fun `quoted author - bangumi 站点的标准形态`() {
        assertEquals("阿宅", quotedAuthorNicknameOrNull("[quote][b]阿宅[/b] 说: 这集神作[/quote]同意"))
    }

    @Test
    fun `quoted author - 全角冒号与多余空白`() {
        assertEquals("阿宅", quotedAuthorNicknameOrNull("\n [quote] [b] 阿宅 [/b] 说 ：原文[/quote]同意"))
    }

    @Test
    fun `quoted author - 昵称没有加粗`() {
        assertEquals("阿宅", quotedAuthorNicknameOrNull("[quote]阿宅 说: 原文[/quote]同意"))
    }

    @Test
    fun `quoted author - 引用不在开头的不算回复关系`() {
        assertNull(quotedAuthorNicknameOrNull("我来贴个原文 [quote][b]阿宅[/b] 说: 原文[/quote]"))
    }

    @Test
    fun `quoted author - 普通引用没有说冒号`() {
        assertNull(quotedAuthorNicknameOrNull("[quote]一段被引用的话[/quote]我的评论"))
    }

    @Test
    fun `quoted author - 没有引用`() {
        assertNull(quotedAuthorNicknameOrNull("单纯的一条回复"))
    }

    @Test
    fun `reply target - 指向同一楼里被引用的那条`() {
        val comment = comment(
            replies = listOf(
                reply(id = "r1", nickname = "阿宅", createdAt = 1_000, content = "这集神作"),
                reply(id = "r2", nickname = "路人", createdAt = 2_000, content = "[quote][b]阿宅[/b] 说: 这集神作[/quote]同意"),
            ),
        ).toEpisodeComment()

        assertNull(comment.replies[0].replyToCommentId)
        assertEquals("r1", comment.replies[1].replyToCommentId)
    }

    @Test
    fun `reply target - 被引用的人不在本楼时留空`() {
        val comment = comment(
            replies = listOf(
                reply(id = "r1", nickname = "路人", createdAt = 1_000, content = "先说一句"),
                reply(id = "r2", nickname = "路人乙", createdAt = 2_000, content = "[quote][b]不在本楼的人[/b] 说: 原文[/quote]同意"),
            ),
        ).toEpisodeComment()

        assertNull(comment.replies[1].replyToCommentId)
    }

    @Test
    fun `reply target - 同名的人各回一条时取本条之前最近的那个`() {
        val comment = comment(
            replies = listOf(
                reply(id = "r1", nickname = "阿宅", createdAt = 1_000, content = "第一条"),
                reply(id = "r2", nickname = "阿宅", createdAt = 2_000, content = "第二条"),
                reply(id = "r3", nickname = "路人", createdAt = 3_000, content = "[quote][b]阿宅[/b] 说: 第二条[/quote]同意"),
                reply(id = "r4", nickname = "阿宅", createdAt = 4_000, content = "后来又说了一条"),
            ),
        ).toEpisodeComment()

        assertEquals("r2", comment.replies[2].replyToCommentId)
    }

    private fun comment(replies: List<AniEpisodeCommentReply>) = AniEpisodeComment(
        id = "c1",
        sourceCommentId = "c1",
        episodeId = 1,
        contentBbcode = "主楼",
        createdAtMillis = 0,
        replyCount = replies.size,
        briefReplies = replies,
        reactions = emptyList(),
        canReply = true,
        source = AniEpisodeCommentSource.BANGUMI,
        likeCount = 0,
        author = AniEpisodeCommentAuthor(id = "u0", nickname = "楼主"),
    )

    private fun reply(
        id: String,
        nickname: String,
        createdAt: Long,
        content: String,
    ) = AniEpisodeCommentReply(
        id = id,
        sourceCommentId = id,
        episodeId = 1,
        contentBbcode = content,
        createdAtMillis = createdAt,
        reactions = emptyList(),
        author = AniEpisodeCommentAuthor(id = "u-$nickname", nickname = nickname),
    )
}

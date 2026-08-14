/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.richtext.UIRichElement
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 与 Figma 设计稿 "📐 定稿示例" 对齐的确定性测试数据.
 */
object CommentItemTestFixtures {
    /** 固定的 "当前时间", 保证相对时间文案确定. */
    val fixedNow: Instant = Instant.parse("2026-08-12T21:00:00+08:00")

    private fun text(content: String) = UIRichText(
        listOf(
            UIRichElement.AnnotatedText(
                listOf(UIRichElement.Annotated.Text(content)),
            ),
        ),
    )

    private fun author(id: String, nickname: String) = UserInfo(
        id = id,
        username = nickname,
        nickname = nickname,
        avatarUrl = null,
    )

    fun aniComment(
        reactions: List<UICommentReaction> = emptyList(),
        withReply: Boolean = false,
        likeCount: Int = 12,
        selfVote: UICommentVote? = null,
        content: String = "一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。运镜和作画都在燃烧经费。",
    ): UIComment = UIComment(
        id = 1,
        stableId = "ani:1",
        author = author("101", "凉山下"),
        content = text(content),
        createdAt = (fixedNow - 2.hours).toEpochMilliseconds(),
        reactions = reactions,
        briefReplies = if (withReply) {
            listOf(
                UIComment(
                    id = 2,
                    stableId = "ani:2",
                    author = author("102", "虹夏"),
                    content = text("这段的分镜是井上俊之画的，难怪。"),
                    createdAt = (fixedNow - 1.hours).toEpochMilliseconds(),
                    reactions = emptyList(),
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = UICommentSource.ANI,
                    sourceCommentId = "2",
                    canReply = true,
                ),
            )
        } else emptyList(),
        replyCount = if (withReply) 5 else 0,
        rating = null,
        source = UICommentSource.ANI,
        sourceCommentId = "1",
        canReply = true,
        likeCount = likeCount,
        selfVote = selfVote,
        rawContent = content,
    )

    fun bangumiComment(
        nickname: String = "结束乐队打工人",
        content: String = "片尾曲歌词翻译很用心，staff 表滚动那里还有彩蛋。",
        rating: Int? = null,
        reactions: List<UICommentReaction> = emptyList(),
        hoursAgo: Int = 23,
        withReply: Boolean = false,
    ): UIComment = UIComment(
        id = nickname.hashCode().toLong(),
        stableId = "bangumi:$nickname",
        author = author(nickname.hashCode().toString(), nickname),
        content = text(content),
        createdAt = (fixedNow - hoursAgo.hours).toEpochMilliseconds(),
        reactions = reactions,
        briefReplies = if (withReply) {
            listOf(
                UIComment(
                    id = 3,
                    stableId = "bangumi:$nickname:reply",
                    author = author("103", "波奇酱"),
                    content = text("同感，歌词书排版也很棒。"),
                    createdAt = (fixedNow - 1.hours).toEpochMilliseconds(),
                    reactions = emptyList(),
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = UICommentSource.BANGUMI,
                    sourceCommentId = "$nickname:reply",
                    canReply = false,
                ),
            )
        } else emptyList(),
        replyCount = if (withReply) 3 else 0,
        rating = rating,
        source = UICommentSource.BANGUMI,
        sourceCommentId = nickname,
        canReply = false,
        likeCount = 0,
        selfVote = null,
        rawContent = content,
    )

    fun personComment(nickname: String, content: String, daysAgo: Int): UIComment = UIComment(
        id = nickname.hashCode().toLong(),
        stableId = "bangumi:person:$nickname",
        author = author(nickname.hashCode().toString(), nickname),
        content = text(content),
        createdAt = (fixedNow - daysAgo.days).toEpochMilliseconds(),
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.BANGUMI,
        sourceCommentId = nickname,
        canReply = false,
        rawContent = content,
    )

    /** 对应 Figma "回应行": 😹 3 + 🎸 2 */
    val defaultReactions = listOf(
        UICommentReaction("bgm11", count = 3, selected = false),
        UICommentReaction("bgm16", count = 2, selected = false),
    )

    /** 对应 Figma "回应行 · 已贴": 😹 4 (toggled) + 🎸 2 */
    val toggledReactions = listOf(
        UICommentReaction("bgm11", count = 4, selected = true),
        UICommentReaction("bgm16", count = 2, selected = false),
    )

    /** 对应 Figma "列表模式": 大量贴纸, 一行放不下 */
    val overflowingReactions = listOf(
        UICommentReaction("bgm2", count = 24, selected = true),
        UICommentReaction("bgm5", count = 18, selected = false),
        UICommentReaction("bgm7", count = 12, selected = false),
        UICommentReaction("bgm10", count = 9, selected = false),
        UICommentReaction("bgm12", count = 6, selected = false),
        UICommentReaction("bgm15", count = 4, selected = false),
        UICommentReaction("bgm17", count = 3, selected = false),
        UICommentReaction("bgm20", count = 2, selected = false),
        UICommentReaction("bgm22", count = 1, selected = false),
    )
}

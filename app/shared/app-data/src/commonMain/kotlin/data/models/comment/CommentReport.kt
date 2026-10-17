/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.comment

/**
 * 被举报内容的类型. 只支持 Animeko 自有评论, Bangumi 源评论不可举报.
 */
enum class CommentReportTargetType {
    EPISODE_COMMENT,
    SUBJECT_REVIEW,

    /**
     * 人物 (声优/制作人员) 评论.
     */
    PERSON_COMMENT,

    /**
     * 角色评论.
     */
    CHARACTER_COMMENT,
}

/**
 * 举报理由分类, 与服务端一致.
 */
enum class CommentReportReason {
    SPAM,
    HARASSMENT,
    SPOILER,
    NSFW,
    ILLEGAL,
    OTHER,
}

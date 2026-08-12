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
 * 用户对一条评论 (剧集评论 / 条目评价) 的投票.
 * 同一用户对同一条评论只能持有一个值, 再次投票会覆盖前一个值.
 *
 * 点踩仅对投票者本人可见, 服务端不下发点踩总数.
 */
enum class CommentVoteValue {
    LIKE,
    DISLIKE,
}

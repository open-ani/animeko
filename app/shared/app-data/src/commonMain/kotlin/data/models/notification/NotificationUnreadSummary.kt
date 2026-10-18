/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

/**
 * 服务端返回的未读概况.
 *
 * @property unreadCounts 请求的分组中有未读通知的分组及其未读数. 游客为空.
 * @property unreadAnnouncementCount 未读公告数. 游客为 0.
 * @property latestAnnouncement 公告列表的第一条 (置顶优先, 然后最新). 游客也有, 此时 [Announcement.read] 为 `null`.
 */
data class NotificationUnreadSummary(
    val unreadCounts: Map<NotificationGroup, Int>,
    val unreadAnnouncementCount: Int,
    val latestAnnouncement: Announcement?,
)

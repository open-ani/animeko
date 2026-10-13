/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * 通知入口 (头像和头像菜单里的“通知”) 需要的未读状态.
 *
 * @property unreadCount 客户端能渲染的通知类型 ([NotificationType.SUPPORTED]) 的未读数. 公告不计入.
 * @property hasUnreadAnnouncements 是否有未读公告. 游客总是 `false`.
 * @property unreadAnnouncementCount 未读公告数. 游客总是 0.
 * @property latestAnnouncement 公告列表的第一条 (置顶优先, 然后最新), 没有公告时为 `null`. 游客也有.
 */
@Immutable
data class NotificationBadgeState(
    val unreadCount: Int,
    val hasUnreadAnnouncements: Boolean,
    val unreadAnnouncementCount: Int,
    val latestAnnouncement: Announcement?,
) {
    companion object {
        @Stable
        val Empty = NotificationBadgeState(
            unreadCount = 0,
            hasUnreadAnnouncements = false,
            unreadAnnouncementCount = 0,
            latestAnnouncement = null,
        )
    }
}

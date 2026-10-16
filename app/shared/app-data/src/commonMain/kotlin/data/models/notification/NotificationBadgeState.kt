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
 * 通知入口 (头像和头像菜单里的“通知”) 和通知中心标签页需要的未读状态.
 *
 * @property unreadCounts 通知中心各个标签页 ([NotificationGroup.TABS]) 的未读通知数, 不含本机关闭的类型.
 * 没有未读通知的分组不在其中. 公告不计入. 游客为空.
 * @property hasUnreadAnnouncements 是否有未读公告. 游客总是 `false`.
 * @property unreadAnnouncementCount 未读公告数. 游客总是 0.
 * @property latestAnnouncement 公告列表的第一条 (置顶优先, 然后最新), 没有公告时为 `null`. 游客也有.
 */
@Immutable
data class NotificationBadgeState(
    val unreadCounts: Map<NotificationGroup, Int>,
    val hasUnreadAnnouncements: Boolean,
    val unreadAnnouncementCount: Int,
    val latestAnnouncement: Announcement?,
) {
    /**
     * 所有标签页的未读通知数之和. 通知入口的红点依据它.
     */
    val unreadCount: Int get() = unreadCounts.values.sum()

    /**
     * [group] 的标签页上显示的未读数.
     */
    fun unreadCountOf(group: NotificationGroup): Int = unreadCounts[group] ?: 0

    companion object {
        @Stable
        val Empty = NotificationBadgeState(
            unreadCounts = emptyMap(),
            hasUnreadAnnouncements = false,
            unreadAnnouncementCount = 0,
            latestAnnouncement = null,
        )
    }
}

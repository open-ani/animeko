/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.preference.NotificationSettings

/**
 * 通知入口是否显示红点. 入口是主页面顶栏的头像和头像菜单里的“通知”, 两处总是一致.
 *
 * 有未读通知时显示. 只有未读公告时, 由 [NotificationSettings.showAnnouncementDot] 决定.
 * 入口只显示红点, 不显示未读数.
 */
fun NotificationBadgeState.showsEntryDot(settings: NotificationSettings): Boolean =
    unreadCount > 0 ||
            settings.showAnnouncementDot && (hasUnreadAnnouncements || unreadAnnouncementCount > 0)

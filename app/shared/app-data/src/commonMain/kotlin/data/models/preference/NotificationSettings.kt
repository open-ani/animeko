/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.notification.NotificationType

/**
 * 站内通知的本机设置.
 *
 * 关闭某类通知后, 本机请求列表、未读数和 "全部标为已读" 时都不带这个类型: 这类通知不在通知中心显示, 不计入未读,
 * 也不会被 "全部标为已读" 标记. 服务端的数据不受影响, 重新打开后照常显示.
 *
 * @property showAnnouncementDot 没有未读通知但有未读公告时, 头像和头像菜单里的“通知”是否显示红点. 公告列表自己的未读标记不受影响.
 * @property showReportResults 是否显示 [NotificationType.REPORT_RESULT].
 * @property showAccountSecurity 是否显示 [NotificationType.ACCOUNT_SECURITY].
 */
@Immutable
@Serializable
data class NotificationSettings(
    val showAnnouncementDot: Boolean = true,
    val showReportResults: Boolean = true,
    val showAccountSecurity: Boolean = true,
) {
    /**
     * 本机是否显示 [type]. 没有开关的类型总是显示.
     */
    fun isShown(type: NotificationType): Boolean = when (type) {
        NotificationType.REPORT_RESULT -> showReportResults
        NotificationType.ACCOUNT_SECURITY -> showAccountSecurity
        NotificationType.SYSTEM,
        NotificationType.COMMENT_REPLY,
        NotificationType.MENTION,
        NotificationType.REACTION,
        NotificationType.FOLLOW,
        NotificationType.DIRECT_MESSAGE,
            -> true
    }

    /**
     * [types] 中本机显示的类型, 保持原有顺序.
     */
    fun shownTypes(types: Set<NotificationType>): Set<NotificationType> =
        types.filterTo(LinkedHashSet()) { isShown(it) }

    companion object {
        @Stable
        val Default = NotificationSettings()
    }
}

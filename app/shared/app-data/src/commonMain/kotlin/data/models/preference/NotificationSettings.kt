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
 * 关闭某类通知后, 本机请求列表、未读数和 "全部标为已读" 时都排除这个类型 ([excludedTypes]): 这类通知不在通知中心显示,
 * 不计入未读, 也不会被 "全部标为已读" 标记. 服务端的数据不受影响, 重新打开后照常显示.
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
     * 本机关闭的类型. 其他类型 (包括没有开关的类型和客户端不认识的类型) 总是显示.
     */
    val excludedTypes: Set<NotificationType>
        get() = buildSet {
            if (!showReportResults) add(NotificationType.REPORT_RESULT)
            if (!showAccountSecurity) add(NotificationType.ACCOUNT_SECURITY)
        }

    companion object {
        @Stable
        val Default = NotificationSettings()
    }
}

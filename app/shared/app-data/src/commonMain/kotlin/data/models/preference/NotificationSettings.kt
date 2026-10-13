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

/**
 * 站内通知的本机设置.
 *
 * @property showAnnouncementDot 没有未读通知但有未读公告时, 头像和头像菜单里的“通知”是否显示红点. 公告列表自己的未读标记不受影响.
 */
@Immutable
@Serializable
data class NotificationSettings(
    val showAnnouncementDot: Boolean = true,
) {
    companion object {
        @Stable
        val Default = NotificationSettings()
    }
}

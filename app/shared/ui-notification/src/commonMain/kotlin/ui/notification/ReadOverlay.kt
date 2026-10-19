/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification

/**
 * 本地叠加的已读状态.
 *
 * 标记已读时立即更新列表的外观, 不等待服务端, 也不重新加载已经分页的数据. 刷新列表后服务端的状态与之一致.
 *
 * @property readIds 单独标为已读的 ID.
 * @property allReadUpToMillis "全部标为已读" 的时间点: 不晚于它的条目都视为已读.
 */
@Immutable
data class ReadOverlay(
    val readIds: Set<String> = emptySet(),
    val allReadUpToMillis: Long? = null,
) {
    fun isRead(id: String, timeMillis: Long): Boolean =
        id in readIds || (allReadUpToMillis != null && timeMillis <= allReadUpToMillis)

    fun withRead(id: String): ReadOverlay = copy(readIds = readIds + id)

    fun withoutRead(id: String): ReadOverlay = copy(readIds = readIds - id)

    companion object {
        val Empty = ReadOverlay()
    }
}

fun ReadOverlay.isRead(notification: AppNotification): Boolean =
    notification.read || isRead(notification.id, notification.createdAtMillis)

/**
 * @return 游客 (服务端没有已读状态) 且本地没有标记时为 `null`.
 */
fun ReadOverlay.isRead(announcement: Announcement): Boolean? =
    if (isRead(announcement.id, announcement.publishedAtMillis)) true else announcement.read

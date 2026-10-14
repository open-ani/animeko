/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.notification

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException

/**
 * 站内通知和公告.
 *
 * 所有 suspend 函数在失败时抛出 [RepositoryException] (例如游客调用需要登录的接口时为 [RepositoryAuthorizationException]),
 * 分页 flow 把错误作为 `LoadState.Error` 交给 UI, 其中的异常同样是 [RepositoryException].
 *
 * 已读操作不会修改已经发出的分页数据, 调用方自行在本地叠加已读状态, 刷新列表后以服务端为准.
 * 已读操作之后应调用 [me.him188.ani.app.domain.notification.NotificationBadgeManager.requestRefresh] 更新未读数.
 */
interface NotificationRepository {
    /**
     * [group] 中除 [excludeTypes] 以外的通知, 按创建时间倒序. 需要登录.
     *
     * 调用方传入本机设置关闭的类型 ([NotificationSettings.excludedTypes]).
     */
    fun notificationsPager(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
    ): Flow<PagingData<AppNotification>>

    /**
     * 获取一条通知. 通知不存在或不属于当前用户时返回 `null`. 列表中出现过的通知之后也可能不存在.
     */
    suspend fun getNotification(id: String): AppNotification?

    /**
     * 把 [ids] 标为已读. 不存在或不属于当前用户的 ID 会被忽略.
     */
    suspend fun markNotificationsRead(ids: List<String>)

    /**
     * 把 [group] 中除 [excludeTypes] 以外、创建时间不晚于 [beforeMillis] 的通知全部标为已读.
     * [beforeMillis] 为 `null` 时为服务端的当前时间.
     *
     * 传入列表中最新一条的创建时间和列表加载时排除的类型, 可以避免把用户还没看到的通知也标为已读.
     */
    suspend fun markAllNotificationsRead(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
        beforeMillis: Long?,
    )

    /**
     * 公告, 置顶优先, 然后按发布时间倒序. 游客也可以访问, 此时 [Announcement.read] 为 `null`.
     */
    fun announcementsPager(): Flow<PagingData<Announcement>>

    /**
     * 获取一条公告. 公告不存在, 未发布或已删除时返回 `null`. 游客也可以访问.
     */
    suspend fun getAnnouncement(id: String): Announcement?

    /**
     * 把 [ids] 标为已读. 需要登录.
     */
    suspend fun markAnnouncementsRead(ids: List<String>)

    /**
     * 把发布时间不晚于 [beforeMillis] 的公告全部标为已读, 为 `null` 时到现在为止. 需要登录.
     *
     * 传用户看到的最新一条公告的发布时间, 这样之后才发布、用户还没看到的公告仍是未读.
     */
    suspend fun markAllAnnouncementsRead(beforeMillis: Long?)
}

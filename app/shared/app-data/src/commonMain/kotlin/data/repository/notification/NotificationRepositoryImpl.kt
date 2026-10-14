/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.notification

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.CursorPage
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.network.AniAnnouncementService
import me.him188.ani.app.data.network.AniNotificationService
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult

class NotificationRepositoryImpl(
    private val notificationService: AniNotificationService,
    private val announcementService: AniAnnouncementService,
) : Repository(), NotificationRepository {
    override fun notificationsPager(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
    ): Flow<PagingData<AppNotification>> =
        Pager(defaultPagingConfig) { notificationsPagingSource(group, excludeTypes) }.flow

    internal fun notificationsPagingSource(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
    ): PagingSource<String, AppNotification> =
        CursorPagingSource(maxLimit = AniNotificationService.MAX_LIMIT, keyOf = { it.id }) { after, limit ->
            notificationService.listNotifications(group, excludeTypes, after, limit)
        }

    override suspend fun getNotification(id: String): AppNotification? =
        notificationService.getNotification(id)

    override suspend fun markNotificationsRead(ids: List<String>) {
        for (chunk in ids.distinct().chunked(AniNotificationService.MAX_LIMIT)) {
            notificationService.markNotificationsRead(chunk)
        }
    }

    override suspend fun markAllNotificationsRead(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
        beforeMillis: Long?,
    ) {
        notificationService.markAllNotificationsRead(group, excludeTypes, beforeMillis)
    }

    override fun announcementsPager(): Flow<PagingData<Announcement>> =
        Pager(defaultPagingConfig) { announcementsPagingSource() }.flow

    internal fun announcementsPagingSource(): PagingSource<String, Announcement> =
        CursorPagingSource(maxLimit = AniAnnouncementService.MAX_LIMIT, keyOf = { it.id }) { after, limit ->
            announcementService.listAnnouncements(after, limit)
        }

    override suspend fun getAnnouncement(id: String): Announcement? =
        announcementService.getAnnouncement(id)

    override suspend fun markAnnouncementsRead(ids: List<String>) {
        for (chunk in ids.distinct().chunked(AniAnnouncementService.MAX_LIMIT)) {
            announcementService.markAnnouncementsRead(chunk)
        }
    }

    override suspend fun markAllAnnouncementsRead(beforeMillis: Long?) {
        announcementService.markAllAnnouncementsRead(beforeMillis)
    }
}

/**
 * 按服务端游标顺序翻页. 列表以 ID 作为 key, 重复的 key 会崩溃.
 *
 * 用游标而非 offset, 是因为翻页期间新到的通知会让 offset 漂移. 但游标只保证排序不变的项不重复:
 * 排序依据会变化的项可能在翻页期间移到游标之后, 再出现一次, 例如已加载的置顶公告被取消置顶.
 * 所以同一次加载 (一个 [PagingSource] 实例) 中已经出现过的 key 会被丢弃.
 *
 * 刷新总是从第一页开始 ([getRefreshKey] 为 `null`): 游标只能向后翻.
 *
 * @param maxLimit 服务端接受的每页条数上限. 超过会返回 400.
 * @param keyOf 列表中使用的 key.
 * @param loadPage 加载 `after` 之后的一页, 每页最多 `limit` 条. 第一页的 `after` 为 `null`.
 */
internal class CursorPagingSource<T : Any>(
    private val maxLimit: Int,
    private val keyOf: (T) -> String,
    private val loadPage: suspend (after: String?, limit: Int) -> CursorPage<T>,
) : PagingSource<String, T>() {
    /**
     * 只向后翻页 (`prevKey` 总是 `null`), Paging 依次加载各页, 所以不需要同步.
     */
    private val loadedKeys = mutableSetOf<String>()

    override fun getRefreshKey(state: PagingState<String, T>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> {
        return runWrappingExceptionAsLoadResult {
            // 首次加载的 loadSize 默认是 pageSize 的三倍, 可能超过服务端上限
            val page = loadPage(params.key, params.loadSize.coerceIn(1, maxLimit))
            LoadResult.Page(
                data = page.items.filter { loadedKeys.add(keyOf(it)) },
                prevKey = null,
                nextKey = page.nextCursor,
            )
        }
    }
}

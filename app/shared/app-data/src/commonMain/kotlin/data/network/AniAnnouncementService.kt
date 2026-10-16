/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.CursorPage
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.AnnouncementsAniApi
import me.him188.ani.client.models.AniAnnouncement
import me.him188.ani.client.models.AniMarkAllAnnouncementsReadRequest
import me.him188.ani.client.models.AniMarkAnnouncementsReadRequest
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * 公告. 列表和详情游客也能访问, 标记已读需要登录.
 */
open class AniAnnouncementService(
    private val announcementsApi: ApiInvoker<AnnouncementsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 置顶的在前, 然后按发布时间倒序.
     *
     * @param after 上一页的 [CursorPage.nextCursor], `null` 表示第一页.
     * @param limit 每页条数, 服务端只接受 1 到 [MAX_LIMIT].
     */
    open suspend fun listAnnouncements(after: String?, limit: Int): CursorPage<Announcement> =
        withContext(ioDispatcher) {
            val page = try {
                announcementsApi.invoke { listAnnouncements(after = after, limit = limit).body() }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
            CursorPage(items = page.items.map { it.toAnnouncement() }, nextCursor = page.nextCursor)
        }

    /**
     * 获取一条公告. 不存在, 未发布或已删除时返回 `null`.
     */
    open suspend fun getAnnouncement(id: String): Announcement? = withContext(ioDispatcher) {
        try {
            announcementsApi.invoke { getAnnouncement(id).body() }.toAnnouncement()
        } catch (e: ClientRequestException) {
            if (e.meansNoSuchItem()) return@withContext null
            throw RepositoryException.wrapOrThrowCancellation(e)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * @param ids 最多 [MAX_LIMIT] 个.
     */
    open suspend fun markAnnouncementsRead(ids: List<String>): Unit = withContext(ioDispatcher) {
        try {
            announcementsApi.invoke {
                markAnnouncementsRead(AniMarkAnnouncementsReadRequest(ids))
                Unit
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * @param beforeMillis 只标记发布时间不晚于此时间的公告, 为 `null` 时到现在为止
     */
    open suspend fun markAllAnnouncementsRead(beforeMillis: Long?): Unit = withContext(ioDispatcher) {
        try {
            announcementsApi.invoke {
                // 服务端要求请求体至少是 `{}`, 所以总是传请求对象
                markAllAnnouncementsRead(AniMarkAllAnnouncementsReadRequest(beforeMillis = beforeMillis))
                Unit
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    companion object {
        /**
         * 服务端每页条数和一次标记已读的 ID 数的上限, 超过会返回 400.
         */
        const val MAX_LIMIT = 100
    }
}

internal fun AniAnnouncement.toAnnouncement(): Announcement = Announcement(
    id = id,
    tag = AnnouncementTag.fromWireValue(tag),
    rawTag = tag,
    title = title,
    body = body,
    linkUrl = linkUrl,
    pinned = pinned,
    publishedAtMillis = publishedAtMillis,
    read = read,
)

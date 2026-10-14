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
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.CursorPage
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationDisplay
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.data.models.notification.NotificationTarget
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.NotificationUnreadSummary
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.NotificationsAniApi
import me.him188.ani.client.models.AniCommentReportReason
import me.him188.ani.client.models.AniCommentReportTargetType
import me.him188.ani.client.models.AniMarkAllNotificationsReadRequest
import me.him188.ani.client.models.AniMarkNotificationsReadRequest
import me.him188.ani.client.models.AniNotification
import me.him188.ani.client.models.AniNotificationAccountSecurity
import me.him188.ani.client.models.AniNotificationReportResult
import me.him188.ani.client.models.AniNotificationSystem
import me.him188.ani.client.models.AniNotificationTarget
import me.him188.ani.client.models.AniNotificationUnreadSummary
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * 当前用户的站内通知.
 *
 * 通知按分组 ([NotificationGroup], 即服务端的分类) 请求. 服务端决定每种类型属于哪个分类, 所以分类中新增的类型
 * 同样出现在列表和未读数中, 由通用展示内容展示. 本机关闭的类型 ([NotificationSettings.excludedTypes]) 通过 `excludeTypes` 排除.
 */
open class AniNotificationService(
    private val notificationsApi: ApiInvoker<NotificationsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * [group] 中除 [excludeTypes] 以外的通知, 按创建时间倒序. 需要登录.
     *
     * @param after 上一页的 [CursorPage.nextCursor], `null` 表示第一页.
     * @param limit 每页条数, 服务端只接受 1 到 [MAX_LIMIT].
     */
    open suspend fun listNotifications(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
        after: String?,
        limit: Int,
    ): CursorPage<AppNotification> = withContext(ioDispatcher) {
        val page = try {
            notificationsApi.invoke {
                listNotifications(
                    category = group.wireValue,
                    excludeTypes = excludeTypes.toWireValuesOrNull(),
                    after = after,
                    limit = limit,
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        CursorPage(
            items = page.items.map { it.toAppNotification() },
            nextCursor = page.nextCursor,
        )
    }

    /**
     * 获取一条通知. 不存在或不属于当前用户时返回 `null`.
     */
    open suspend fun getNotification(id: String): AppNotification? = withContext(ioDispatcher) {
        val notification = try {
            notificationsApi.invoke { getNotification(id).body() }
        } catch (e: ClientRequestException) {
            if (e.meansNoSuchItem()) return@withContext null
            throw RepositoryException.wrapOrThrowCancellation(e)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        notification.toAppNotification()
    }

    /**
     * [groups] 中除 [excludeTypes] 以外的未读数, 以及公告的未读数和第一条公告. 游客也可以调用, 此时没有通知未读数.
     *
     * [NotificationUnreadSummary.unreadCounts] 只包含 [groups] 中有未读通知的分组. [groups] 为空时没有通知未读数.
     */
    open suspend fun getUnreadSummary(
        groups: List<NotificationGroup>,
        excludeTypes: Set<NotificationType>,
    ): NotificationUnreadSummary = withContext(ioDispatcher) {
        try {
            notificationsApi.invoke {
                getNotificationUnreadSummary(
                    categories = groups.takeIf { it.isNotEmpty() }?.map { it.wireValue },
                    excludeTypes = excludeTypes.toWireValuesOrNull(),
                ).body()
            }.toNotificationUnreadSummary(groups)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 把 [ids] 标为已读. 不存在或不属于当前用户的 ID 会被忽略.
     *
     * @param ids 最多 [MAX_LIMIT] 个.
     */
    open suspend fun markNotificationsRead(ids: List<String>): Unit = withContext(ioDispatcher) {
        try {
            notificationsApi.invoke {
                markNotificationsRead(AniMarkNotificationsReadRequest(ids))
                Unit
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 把 [group] 中除 [excludeTypes] 以外、创建时间不晚于 [beforeMillis] 的通知标为已读.
     * [beforeMillis] 为 `null` 时为服务端的当前时间.
     */
    open suspend fun markAllNotificationsRead(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
        beforeMillis: Long?,
    ): Unit = withContext(ioDispatcher) {
        try {
            notificationsApi.invoke {
                markAllNotificationsRead(
                    AniMarkAllNotificationsReadRequest(
                        category = group.wireValue,
                        excludeTypes = excludeTypes.map { it.wireValue },
                        beforeMillis = beforeMillis,
                    ),
                )
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

/**
 * 按 ID 获取一条通知或公告时, 服务端对不存在的 ID 返回 404, 对不是 UUID 的 ID 返回 400. 两者都表示没有这一条.
 */
internal fun ClientRequestException.meansNoSuchItem(): Boolean =
    response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest

/**
 * 客户端认识的类型映射为各自的内容. 不认识的类型, 以及缺少该类型内容的通知, 映射为 [AppNotificationContent.Unknown].
 */
internal fun AniNotification.toAppNotification(): AppNotification {
    val content: AppNotificationContent = when (NotificationType.fromWireValue(type)) {
        NotificationType.SYSTEM -> system?.toSystem()
        NotificationType.REPORT_RESULT -> reportResult?.toReportResult()
        NotificationType.ACCOUNT_SECURITY -> accountSecurity?.toAccountSecurity()
        null -> null
    } ?: AppNotificationContent.Unknown
    return AppNotification(
        id = id,
        rawType = type,
        rawCategory = category,
        createdAtMillis = createdAtMillis,
        read = read,
        display = NotificationDisplay(
            title = title,
            body = body,
            overline = overline,
            icon = NotificationIcon.fromWireValue(icon),
            imageUrl = imageUrl,
            target = target?.toNotificationTarget(),
        ),
        content = content,
    )
}

private fun AniNotificationTarget.toNotificationTarget() = NotificationTarget(
    subjectId = subjectId,
    episodeId = episodeId,
    personId = personId,
    characterId = characterId,
    url = url,
)

private fun AniNotificationSystem.toSystem() = AppNotificationContent.System(
    event = SystemNotificationEvent.fromWireValue(event),
)

private fun AniNotificationReportResult.toReportResult() = AppNotificationContent.ReportResult(
    targetType = AniCommentReportTargetType.entries.firstOrNull { it.value == targetType }?.toCommentReportTargetType(),
    reason = AniCommentReportReason.entries.firstOrNull { it.value == reason }?.toCommentReportReason()
        ?: CommentReportReason.OTHER,
    reportedAtMillis = reportedAtMillis,
    handledAtMillis = handledAtMillis,
    contentRemoved = contentRemoved,
    contentSnapshot = contentSnapshot,
    contentCreatedAtMillis = contentCreatedAtMillis,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectNameCn = subjectNameCn,
    episodeId = episodeId,
    episodeSort = episodeSort,
    personId = personId,
    characterId = characterId,
    targetName = targetName,
)

private fun AniNotificationAccountSecurity.toAccountSecurity() = AppNotificationContent.AccountSecurity(
    event = AccountSecurityEvent.fromWireValue(event),
    rawEvent = event,
    occurredAtMillis = occurredAtMillis,
    provider = provider,
)

/**
 * @param groups 请求的分组. 其他分类的计数被丢弃.
 */
internal fun AniNotificationUnreadSummary.toNotificationUnreadSummary(
    groups: List<NotificationGroup>,
): NotificationUnreadSummary = NotificationUnreadSummary(
    unreadCounts = buildMap {
        for (count in counts) {
            val group = NotificationGroup.fromWireValue(count.category) ?: continue
            if (group !in groups || count.count <= 0) continue
            put(group, (get(group) ?: 0) + count.count)
        }
    },
    unreadAnnouncementCount = unreadAnnouncementCount,
    latestAnnouncement = latestAnnouncement?.toAnnouncement(),
)

/**
 * 空集合返回 `null`, 即不传这个参数.
 */
private fun Set<NotificationType>.toWireValuesOrNull(): List<String>? =
    takeIf { it.isNotEmpty() }?.map { it.wireValue }

private fun AniCommentReportTargetType.toCommentReportTargetType(): CommentReportTargetType = when (this) {
    AniCommentReportTargetType.EPISODE_COMMENT -> CommentReportTargetType.EPISODE_COMMENT
    AniCommentReportTargetType.SUBJECT_REVIEW -> CommentReportTargetType.SUBJECT_REVIEW
    AniCommentReportTargetType.PERSON_COMMENT -> CommentReportTargetType.PERSON_COMMENT
    AniCommentReportTargetType.CHARACTER_COMMENT -> CommentReportTargetType.CHARACTER_COMMENT
}

private fun AniCommentReportReason.toCommentReportReason(): CommentReportReason = when (this) {
    AniCommentReportReason.SPAM -> CommentReportReason.SPAM
    AniCommentReportReason.HARASSMENT -> CommentReportReason.HARASSMENT
    AniCommentReportReason.SPOILER -> CommentReportReason.SPOILER
    AniCommentReportReason.NSFW -> CommentReportReason.NSFW
    AniCommentReportReason.ILLEGAL -> CommentReportReason.ILLEGAL
    AniCommentReportReason.OTHER -> CommentReportReason.OTHER
}

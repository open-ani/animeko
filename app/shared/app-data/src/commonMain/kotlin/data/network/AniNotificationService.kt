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
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.NotificationUnreadSummary
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.client.apis.NotificationsAniApi
import me.him188.ani.client.models.AniCommentReportReason
import me.him188.ani.client.models.AniCommentReportTargetType
import me.him188.ani.client.models.AniMarkAllNotificationsReadRequest
import me.him188.ani.client.models.AniMarkNotificationsReadRequest
import me.him188.ani.client.models.AniNotification
import me.him188.ani.client.models.AniNotificationAccountSecurity
import me.him188.ani.client.models.AniNotificationReportResult
import me.him188.ani.client.models.AniNotificationSystem
import me.him188.ani.client.models.AniNotificationType
import me.him188.ani.client.models.AniNotificationUnreadSummary
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * 当前用户的站内通知.
 *
 * 列表和未读数接口必须带上 `types`: 服务端只返回和统计这些类型, 而不传表示全部类型, 会包含本客户端不能渲染的新类型.
 * 所以这里的 `types` 参数都不可省略, 调用方传 [NotificationType.SUPPORTED] 或其中一个分组的类型, 再去掉本机关闭的类型.
 * `types` 为空时不会请求全部类型: 列表为空, 未读数为空, "全部标为已读" 什么也不做.
 */
open class AniNotificationService(
    private val notificationsApi: ApiInvoker<NotificationsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private val logger = thisLogger()

    /**
     * [types] 中的通知, 按创建时间倒序. 需要登录.
     *
     * 内容与类型不符的通知 (例如缺少该类型的内容) 会被跳过, 所以一页的条数可能少于 [limit].
     *
     * @param after 上一页的 [CursorPage.nextCursor], `null` 表示第一页.
     * @param limit 每页条数, 服务端只接受 1 到 [MAX_LIMIT].
     */
    open suspend fun listNotifications(
        types: Set<NotificationType>,
        after: String?,
        limit: Int,
    ): CursorPage<AppNotification> = withContext(ioDispatcher) {
        if (types.isEmpty()) return@withContext CursorPage(emptyList(), nextCursor = null)
        val page = try {
            notificationsApi.invoke {
                listNotifications(types = types.toAniNotificationTypes(), after = after, limit = limit).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        CursorPage(
            items = page.items.mapNotNull { notification ->
                notification.toAppNotification().also {
                    if (it == null) logger.warn { "Skipping notification ${notification.id}: no content for type ${notification.type}" }
                }
            },
            nextCursor = page.nextCursor,
        )
    }

    /**
     * 获取一条通知. 不存在或不属于当前用户时返回 `null`.
     * 本客户端不能渲染这条通知时抛出 [RepositoryUnknownException].
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
            ?: throw RepositoryUnknownException(
                IllegalStateException("Notification $id has no content for type ${notification.type}"),
            )
    }

    /**
     * [types] 的未读数, 以及公告的未读数和第一条公告. 游客也可以调用, 此时没有通知未读数.
     *
     * [NotificationUnreadSummary.unreadCounts] 只包含 [types] 中的类型.
     */
    open suspend fun getUnreadSummary(types: Set<NotificationType>): NotificationUnreadSummary =
        withContext(ioDispatcher) {
            try {
                notificationsApi.invoke {
                    getNotificationUnreadSummary(types = types.toAniNotificationTypes()).body()
                }.toNotificationUnreadSummary(types)
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
     * 把 [types] 中创建时间不晚于 [beforeMillis] 的通知标为已读. [beforeMillis] 为 `null` 时为服务端的当前时间.
     */
    open suspend fun markAllNotificationsRead(
        types: Set<NotificationType>,
        beforeMillis: Long?,
    ): Unit = withContext(ioDispatcher) {
        if (types.isEmpty()) return@withContext
        try {
            notificationsApi.invoke {
                // 服务端要求请求体至少是 `{}`, 所以总是传请求对象
                markAllNotificationsRead(
                    AniMarkAllNotificationsReadRequest(
                        types = types.toAniNotificationTypes(),
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
 * @return 本客户端不能渲染时返回 `null`: 类型属于尚未实现的分组, 或缺少该类型的内容.
 */
internal fun AniNotification.toAppNotification(): AppNotification? {
    val content: AppNotificationContent = when (type) {
        AniNotificationType.SYSTEM -> system?.toSystem()
        AniNotificationType.REPORT_RESULT -> reportResult?.toReportResult()
        AniNotificationType.ACCOUNT_SECURITY -> accountSecurity?.toAccountSecurity()
        AniNotificationType.COMMENT_REPLY,
        AniNotificationType.MENTION,
        AniNotificationType.REACTION,
        AniNotificationType.FOLLOW,
        AniNotificationType.DIRECT_MESSAGE,
            -> null
    } ?: return null
    return AppNotification(
        id = id,
        createdAtMillis = createdAtMillis,
        read = read,
        content = content,
    )
}

private fun AniNotificationSystem.toSystem() = AppNotificationContent.System(
    title = title,
    body = body,
    linkUrl = linkUrl,
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
 * @param types 请求的类型. 不在其中的计数被丢弃.
 */
internal fun AniNotificationUnreadSummary.toNotificationUnreadSummary(
    types: Set<NotificationType>,
): NotificationUnreadSummary = NotificationUnreadSummary(
    unreadCounts = buildMap {
        for (count in counts) {
            val type = count.type.toNotificationType()
            if (type !in types) continue
            put(type, (get(type) ?: 0) + count.count)
        }
    },
    unreadAnnouncementCount = unreadAnnouncementCount,
    latestAnnouncement = latestAnnouncement?.toAnnouncement(),
)

private fun Set<NotificationType>.toAniNotificationTypes(): List<AniNotificationType> =
    map { it.toAniNotificationType() }

private fun NotificationType.toAniNotificationType(): AniNotificationType = when (this) {
    NotificationType.SYSTEM -> AniNotificationType.SYSTEM
    NotificationType.REPORT_RESULT -> AniNotificationType.REPORT_RESULT
    NotificationType.ACCOUNT_SECURITY -> AniNotificationType.ACCOUNT_SECURITY
    NotificationType.COMMENT_REPLY -> AniNotificationType.COMMENT_REPLY
    NotificationType.MENTION -> AniNotificationType.MENTION
    NotificationType.REACTION -> AniNotificationType.REACTION
    NotificationType.FOLLOW -> AniNotificationType.FOLLOW
    NotificationType.DIRECT_MESSAGE -> AniNotificationType.DIRECT_MESSAGE
}

private fun AniNotificationType.toNotificationType(): NotificationType = when (this) {
    AniNotificationType.SYSTEM -> NotificationType.SYSTEM
    AniNotificationType.REPORT_RESULT -> NotificationType.REPORT_RESULT
    AniNotificationType.ACCOUNT_SECURITY -> NotificationType.ACCOUNT_SECURITY
    AniNotificationType.COMMENT_REPLY -> NotificationType.COMMENT_REPLY
    AniNotificationType.MENTION -> NotificationType.MENTION
    AniNotificationType.REACTION -> NotificationType.REACTION
    AniNotificationType.FOLLOW -> NotificationType.FOLLOW
    AniNotificationType.DIRECT_MESSAGE -> NotificationType.DIRECT_MESSAGE
}

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

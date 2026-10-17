/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationDisplay
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.data.models.notification.NotificationTarget
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 预览和测试使用的固定时间点: 2026-10-13 12:00:00 UTC.
 */
@TestOnly
const val TestNotificationNowMillis = 1_791_892_800_000L

/**
 * 把 "现在" 固定为 [TestNotificationNowMillis], 时区固定为 UTC+8 (当地时间 2026-10-13 20:00).
 */
@TestOnly
val TestNotificationTimeContext = NotificationTimeContext(
    clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(TestNotificationNowMillis)
    },
    timeZoneOverride = UtcOffset(hours = 8).asTimeZone(),
)

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS

@TestOnly
fun createTestReportResult(
    id: String = "report-1",
    read: Boolean = false,
    targetType: CommentReportTargetType? = CommentReportTargetType.EPISODE_COMMENT,
    reason: CommentReportReason = CommentReportReason.SPOILER,
    createdAtMillis: Long = TestNotificationNowMillis - 2 * HOUR_MILLIS,
    contentRemoved: Boolean = true,
    contentSnapshot: String? = "星野：最后一集芙莉莲会和费伦分开",
    contentCreatedAtMillis: Long? = createdAtMillis - DAY_MILLIS - 22 * MINUTE_MILLIS,
    subjectId: Long? = 400602,
    subjectName: String? = "葬送のフリーレン",
    subjectNameCn: String? = "葬送的芙莉莲",
    episodeId: Long? = 1227087,
    episodeSort: String? = "12",
    personId: Long? = null,
    characterId: Long? = null,
    targetName: String? = null,
): AppNotification = AppNotification(
    id = id,
    rawType = NotificationType.REPORT_RESULT.wireValue,
    rawCategory = NotificationGroup.NOTICE.wireValue,
    createdAtMillis = createdAtMillis,
    read = read,
    display = NotificationDisplay(
        title = "你举报的评论已被处理",
        body = listOfNotNull(subjectNameCn ?: subjectName, episodeSort?.let { "第 $it 话" }).joinToString(" "),
        icon = NotificationIcon.REPORT,
        target = NotificationTarget(subjectId = subjectId, episodeId = episodeId),
    ),
    content = AppNotificationContent.ReportResult(
        targetType = targetType,
        reason = reason,
        reportedAtMillis = createdAtMillis - DAY_MILLIS,
        handledAtMillis = createdAtMillis,
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
    ),
)

@TestOnly
fun createTestAccountSecurity(
    id: String = "security-1",
    read: Boolean = false,
    event: AccountSecurityEvent = AccountSecurityEvent.THIRD_PARTY_UNBOUND,
    createdAtMillis: Long = TestNotificationNowMillis - DAY_MILLIS - 3 * HOUR_MILLIS,
    provider: String? = "github",
): AppNotification = AppNotification(
    id = id,
    rawType = NotificationType.ACCOUNT_SECURITY.wireValue,
    rawCategory = NotificationGroup.NOTICE.wireValue,
    createdAtMillis = createdAtMillis,
    read = read,
    display = NotificationDisplay(
        title = when (event) {
            AccountSecurityEvent.THIRD_PARTY_UNBOUND -> "${providerDisplayName(provider) ?: "第三方"} 账号已解绑"
            AccountSecurityEvent.UNKNOWN -> "账号安全提醒"
        },
        body = "该账号已登录另一个 Animeko 账号".takeIf { event == AccountSecurityEvent.THIRD_PARTY_UNBOUND },
        icon = NotificationIcon.SECURITY,
    ),
    content = AppNotificationContent.AccountSecurity(
        event = event,
        rawEvent = event.wireValue ?: "future_event",
        occurredAtMillis = createdAtMillis,
        provider = provider,
    ),
)

@TestOnly
fun createTestSystemNotification(
    id: String = "system-1",
    read: Boolean = true,
    createdAtMillis: Long = TestNotificationNowMillis - 40 * DAY_MILLIS,
    title: String = "欢迎加入 Animeko",
    body: String = "绑定 Bangumi 账号后可同步追番进度与收藏。\n详情见 https://myani.org/help",
    linkUrl: String? = "https://myani.org",
    event: SystemNotificationEvent? = null,
): AppNotification = AppNotification(
    id = id,
    rawType = NotificationType.SYSTEM.wireValue,
    rawCategory = NotificationGroup.NOTICE.wireValue,
    createdAtMillis = createdAtMillis,
    read = read,
    display = NotificationDisplay(
        title = title,
        body = body,
        icon = NotificationIcon.INFO,
        target = linkUrl?.let { NotificationTarget(url = it) },
    ),
    content = AppNotificationContent.System(event),
)

/**
 * 当前版本没有专门展示方式的通知, 例如互动. 用通用展示内容展示.
 */
@TestOnly
fun createTestGenericNotification(
    id: String = "reply-1",
    read: Boolean = false,
    rawType: String = "comment_reply",
    rawCategory: String = NotificationGroup.INTERACTION.wireValue,
    createdAtMillis: Long = TestNotificationNowMillis - 30 * MINUTE_MILLIS,
    overline: String? = "葬送的芙莉莲 第 1 话",
    title: String = "Bob 回复了你的评论",
    body: String? = "同意，这一集的作画太好了",
    icon: NotificationIcon? = NotificationIcon.REPLY,
    imageUrl: String? = null,
    target: NotificationTarget? = NotificationTarget(subjectId = 400602, episodeId = 1227087),
): AppNotification = AppNotification(
    id = id,
    rawType = rawType,
    rawCategory = rawCategory,
    createdAtMillis = createdAtMillis,
    read = read,
    display = NotificationDisplay(
        title = title,
        body = body,
        overline = overline,
        icon = icon,
        imageUrl = imageUrl,
        target = target,
    ),
    content = AppNotificationContent.Unknown,
)

/**
 * Bangumi 授权过期的系统消息. 标题和正文是服务端为不认识这个事件的客户端准备的.
 */
@TestOnly
fun createTestBangumiExpired(
    id: String = "bangumi-expired",
    read: Boolean = false,
    createdAtMillis: Long = TestNotificationNowMillis - 2 * DAY_MILLIS,
): AppNotification = createTestSystemNotification(
    id = id,
    read = read,
    createdAtMillis = createdAtMillis,
    title = "Bangumi 授权已过期",
    body = "追番进度已停止同步，重新授权后自动恢复。",
    linkUrl = null,
    event = SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED,
)

@TestOnly
fun createTestAnnouncement(
    id: String = "announcement-1",
    tag: AnnouncementTag = AnnouncementTag.MAINTENANCE,
    rawTag: String = tag.wireValue ?: "future_tag",
    title: String = "10 月 20 日 02:00–04:00 服务器维护",
    body: String = "期间评论、弹幕发送与一起看不可用，播放与追番进度不受影响。",
    pinned: Boolean = false,
    publishedAtMillis: Long = TestNotificationNowMillis - DAY_MILLIS,
    read: Boolean? = false,
    linkUrl: String? = null,
): Announcement = Announcement(
    id = id,
    tag = tag,
    rawTag = rawTag,
    title = title,
    body = body,
    linkUrl = linkUrl,
    pinned = pinned,
    publishedAtMillis = publishedAtMillis,
    read = read,
)

@TestOnly
val TestNotifications: List<AppNotification>
    get() = listOf(
        createTestReportResult(),
        createTestAccountSecurity(),
        createTestBangumiExpired(),
        createTestReportResult(
            id = "report-2",
            read = true,
            createdAtMillis = TestNotificationNowMillis - 20 * DAY_MILLIS,
            subjectNameCn = "孤独摇滚！",
            subjectName = "ぼっち・ざ・ろっく！",
            episodeSort = "5",
        ),
        createTestAccountSecurity(
            id = "security-2",
            read = true,
            event = AccountSecurityEvent.UNKNOWN,
            createdAtMillis = TestNotificationNowMillis - 25 * DAY_MILLIS,
            provider = null,
        ),
        createTestSystemNotification(),
    )

/**
 * 互动标签页的通知. 当前版本没有互动的专门展示方式, 都用通用展示内容展示.
 */
@TestOnly
val TestInteractionNotifications: List<AppNotification>
    get() = listOf(
        createTestGenericNotification(),
        createTestGenericNotification(
            id = "mention-1",
            read = true,
            rawType = "mention",
            createdAtMillis = TestNotificationNowMillis - 5 * HOUR_MILLIS,
            overline = "孤独摇滚！ 第 8 话",
            title = "阿九 在评论中提到了你",
            body = "阿九：@你 之前说的那个伏笔，这集回收了",
            icon = NotificationIcon.MENTION,
            imageUrl = "https://example.com/avatar/9.png",
            target = NotificationTarget(subjectId = 328609, episodeId = 1144392),
        ),
        createTestGenericNotification(
            id = "follow-1",
            read = true,
            rawType = "follow",
            createdAtMillis = TestNotificationNowMillis - 3 * DAY_MILLIS,
            overline = null,
            title = "Alice 关注了你",
            body = null,
            icon = NotificationIcon.FOLLOW,
            target = null,
        ),
    )

@TestOnly
val TestAnnouncements: List<Announcement>
    get() = listOf(
        createTestAnnouncement(pinned = true),
        createTestAnnouncement(
            id = "announcement-2",
            tag = AnnouncementTag.RELEASE,
            title = "新版本发布：评论回应与一起看改进",
            body = "评论支持表情回应；一起看房间支持跟随主持人进度。",
            publishedAtMillis = TestNotificationNowMillis - 7 * DAY_MILLIS,
        ),
        createTestAnnouncement(
            id = "announcement-3",
            tag = AnnouncementTag.COMMUNITY,
            title = "评论区规则更新：剧透需打码",
            body = "未打码的剧透评论会被折叠，多次违规将限制评论功能。",
            publishedAtMillis = TestNotificationNowMillis - 15 * DAY_MILLIS,
            read = true,
        ),
    )

@TestOnly
val TestNotificationBadgeState: NotificationBadgeState
    get() = NotificationBadgeState(
        unreadCounts = mapOf(NotificationGroup.NOTICE to 3),
        hasUnreadAnnouncements = true,
        unreadAnnouncementCount = 2,
        latestAnnouncement = TestAnnouncements.first(),
    )

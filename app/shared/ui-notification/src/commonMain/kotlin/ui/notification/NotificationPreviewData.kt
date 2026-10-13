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
import me.him188.ani.app.data.models.notification.LoginMethod
import me.him188.ani.app.data.models.notification.NotificationBadgeState
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

private const val HOUR_MILLIS = 3_600_000L
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
    createdAtMillis = createdAtMillis,
    read = read,
    content = AppNotificationContent.ReportResult(
        targetType = targetType,
        reason = reason,
        reportedAtMillis = createdAtMillis - DAY_MILLIS,
        handledAtMillis = createdAtMillis,
        contentRemoved = contentRemoved,
        contentSnapshot = contentSnapshot,
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
    event: AccountSecurityEvent = AccountSecurityEvent.NEW_LOGIN,
    createdAtMillis: Long = TestNotificationNowMillis - DAY_MILLIS - 3 * HOUR_MILLIS,
    loginMethod: LoginMethod? = LoginMethod.QR_CODE,
    clientPlatform: String? = "macos-aarch64",
    email: String? = null,
    provider: String? = null,
): AppNotification = AppNotification(
    id = id,
    createdAtMillis = createdAtMillis,
    read = read,
    content = AppNotificationContent.AccountSecurity(
        event = event,
        rawEvent = event.wireValue ?: "future_event",
        occurredAtMillis = createdAtMillis,
        loginMethod = loginMethod,
        clientPlatform = clientPlatform,
        clientVersion = "5.3.0",
        deviceName = null,
        email = email,
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
): AppNotification = AppNotification(
    id = id,
    createdAtMillis = createdAtMillis,
    read = read,
    content = AppNotificationContent.System(title, body, linkUrl),
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
        createTestAccountSecurity(
            id = "security-2",
            event = AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED,
            createdAtMillis = TestNotificationNowMillis - 2 * DAY_MILLIS,
            loginMethod = null,
            clientPlatform = null,
        ),
        createTestReportResult(
            id = "report-2",
            read = true,
            createdAtMillis = TestNotificationNowMillis - 20 * DAY_MILLIS,
            subjectNameCn = "孤独摇滚！",
            subjectName = "ぼっち・ざ・ろっく！",
            episodeSort = "5",
        ),
        createTestAccountSecurity(
            id = "security-3",
            read = true,
            event = AccountSecurityEvent.EMAIL_CHANGED,
            createdAtMillis = TestNotificationNowMillis - 25 * DAY_MILLIS,
            loginMethod = null,
            clientPlatform = null,
            email = "a***@example.com",
        ),
        createTestSystemNotification(),
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
        unreadCount = 3,
        hasUnreadAnnouncements = true,
        unreadAnnouncementCount = 2,
        latestAnnouncement = TestAnnouncements.first(),
    )

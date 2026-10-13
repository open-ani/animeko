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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.LoginMethod
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_reason_harassment
import me.him188.ani.app.ui.lang.comment_report_reason_illegal
import me.him188.ani.app.ui.lang.comment_report_reason_nsfw
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spam
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.notification_account_bangumi_expired
import me.him188.ani.app.ui.lang.notification_account_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_account_email_bound
import me.him188.ani.app.ui.lang.notification_account_email_changed
import me.him188.ani.app.ui.lang.notification_account_email_unbound
import me.him188.ani.app.ui.lang.notification_account_new_login
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_generic
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_supporting
import me.him188.ani.app.ui.lang.notification_account_unknown
import me.him188.ani.app.ui.lang.notification_announcement_tag_community
import me.him188.ani.app.ui.lang.notification_announcement_tag_general
import me.him188.ani.app.ui.lang.notification_announcement_tag_maintenance
import me.him188.ani.app.ui.lang.notification_announcement_tag_release
import me.him188.ani.app.ui.lang.notification_login_method_bangumi
import me.him188.ani.app.ui.lang.notification_login_method_email_otp
import me.him188.ani.app.ui.lang.notification_login_method_github
import me.him188.ani.app.ui.lang.notification_login_method_qr_code
import me.him188.ani.app.ui.lang.notification_report_result_row_title
import me.him188.ani.app.ui.lang.notification_report_target_character_comment
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_report_target_episode_comment
import me.him188.ani.app.ui.lang.notification_report_target_person_comment
import me.him188.ani.app.ui.lang.notification_report_target_subject_review
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * 通知列表一行的两行文本: [headline] 说明发生了什么, [supporting] 说明对象或细节.
 *
 * 两部分分开展示, 不拼接成一行; 也从不引用用户自己的评论内容.
 *
 * @property supporting 没有可展示的细节时为 `null`, 此时只显示一行.
 */
@Immutable
data class NotificationRowText(
    val headline: NotificationText,
    val supporting: NotificationText?,
)

/**
 * 计算通知列表一行的文本.
 *
 * @param useOriginalTitle 用户是否偏好显示条目原名, 见 `SubjectAppearanceSettings.useOriginalTitle`.
 */
fun notificationRowText(
    content: AppNotificationContent,
    useOriginalTitle: Boolean,
): NotificationRowText = when (content) {
    is AppNotificationContent.ReportResult -> NotificationRowText(
        headline = NotificationText.Res(Lang.notification_report_result_row_title),
        supporting = reportTargetText(content, useOriginalTitle),
    )

    is AppNotificationContent.AccountSecurity -> accountSecurityRowText(content)

    is AppNotificationContent.System -> NotificationRowText(
        headline = NotificationText.Plain(content.title),
        supporting = firstLineOf(content.body)?.let { NotificationText.Plain(it) },
    )
}

/**
 * 被举报评论所在的对象: 剧集评论为 "条目名 第 N 话", 条目评论为条目名, 人物和角色评论为人物或角色名.
 * 缺少这些信息时为评论类型 (例如 "剧集评论").
 *
 * 不认识的评论类型不知道哪些字段描述了它的对象, 总是显示为通用的 "评论".
 */
fun reportTargetText(
    content: AppNotificationContent.ReportResult,
    useOriginalTitle: Boolean,
): NotificationText {
    val subjectName = preferredSubjectName(content.subjectName, content.subjectNameCn, useOriginalTitle)
    val targetName = content.targetName?.takeIf { it.isNotBlank() }
    val fallback = NotificationText.Res(content.targetType?.labelRes ?: Lang.notification_report_target_comment)
    return when (content.targetType) {
        CommentReportTargetType.EPISODE_COMMENT -> {
            if (subjectName == null) return fallback
            val episode = content.episodeSort?.takeIf { it.isNotBlank() }?.let { episodeLabelText(it) }
                ?: return NotificationText.Plain(subjectName)
            NotificationText.Words(listOf(NotificationText.Plain(subjectName), episode))
        }

        CommentReportTargetType.SUBJECT_REVIEW -> subjectName?.let { NotificationText.Plain(it) } ?: fallback

        CommentReportTargetType.PERSON_COMMENT,
        CommentReportTargetType.CHARACTER_COMMENT,
            -> targetName?.let { NotificationText.Plain(it) } ?: fallback

        null -> fallback
    }
}

/**
 * 按用户偏好选择条目名: 默认中文名, 偏好原名时用原名. 偏好的名字为空时用另一个. 两者都为空时返回 `null`.
 */
fun preferredSubjectName(
    name: String?,
    nameCn: String?,
    useOriginalTitle: Boolean,
): String? {
    val cn = nameCn?.takeIf { it.isNotBlank() }
    val original = name?.takeIf { it.isNotBlank() }
    return if (useOriginalTitle) original ?: cn else cn ?: original
}

/**
 * 剧集序号的展示方式.
 */
@Immutable
sealed interface EpisodeSortLabel {
    /**
     * 数字序号, 展示为 "第 [number] 话". [number] 不补零, 例如 `12`, `12.5`.
     */
    @Immutable
    data class Numbered(val number: String) : EpisodeSortLabel

    /**
     * 非数字序号 (例如 `SP1`), 原样展示.
     */
    @Immutable
    data class Raw(val text: String) : EpisodeSortLabel
}

private val NumericEpisodeSort = Regex("""\d+(\.\d+)?""")

fun parseEpisodeSortLabel(sort: String): EpisodeSortLabel {
    val trimmed = sort.trim()
    if (!NumericEpisodeSort.matches(trimmed)) return EpisodeSortLabel.Raw(trimmed)
    val withoutPadding = trimmed.trimStart('0')
    return EpisodeSortLabel.Numbered(
        if (withoutPadding.isEmpty() || withoutPadding.startsWith('.')) "0$withoutPadding" else withoutPadding,
    )
}

fun episodeLabelText(sort: String): NotificationText = when (val label = parseEpisodeSortLabel(sort)) {
    is EpisodeSortLabel.Numbered -> NotificationText.Res(
        Lang.exploration_schedule_episode,
        listOf(NotificationText.Plain(label.number)),
    )

    is EpisodeSortLabel.Raw -> NotificationText.Plain(label.text)
}

/**
 * 举报时保存的评论快照, 由 `昵称：正文` 在第一个全角冒号处拆开.
 *
 * @property author 评论作者的昵称. 快照中没有全角冒号或冒号前为空时为 `null`.
 */
@Immutable
data class ReportSnapshot(
    val author: String?,
    val text: String,
)

fun parseReportSnapshot(snapshot: String): ReportSnapshot {
    val index = snapshot.indexOf('：')
    if (index < 0) return ReportSnapshot(author = null, text = snapshot.trim())
    val author = snapshot.substring(0, index).trim()
    return ReportSnapshot(
        author = author.ifEmpty { null },
        text = snapshot.substring(index + 1).trim(),
    )
}

/**
 * 账号安全通知的行文本. 不认识的事件为通用的 "账号安全提醒".
 */
fun accountSecurityRowText(content: AppNotificationContent.AccountSecurity): NotificationRowText {
    val email = content.email?.takeIf { it.isNotBlank() }?.let { NotificationText.Plain(it) }
    return when (content.event) {
        AccountSecurityEvent.NEW_LOGIN -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_new_login),
            supporting = loginSummaryText(content.clientPlatform, content.loginMethod),
        )

        AccountSecurityEvent.EMAIL_BOUND -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_email_bound),
            supporting = email,
        )

        AccountSecurityEvent.EMAIL_CHANGED -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_email_changed),
            supporting = email,
        )

        AccountSecurityEvent.EMAIL_UNBOUND -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_email_unbound),
            supporting = email,
        )

        AccountSecurityEvent.THIRD_PARTY_UNBOUND -> NotificationRowText(
            headline = thirdPartyUnboundTitle(content.provider),
            supporting = NotificationText.Res(Lang.notification_account_third_party_unbound_supporting),
        )

        AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_bangumi_expired),
            supporting = NotificationText.Res(Lang.notification_account_bangumi_expired_supporting),
        )

        AccountSecurityEvent.UNKNOWN -> NotificationRowText(
            headline = NotificationText.Res(Lang.notification_account_unknown),
            supporting = null,
        )
    }
}

/**
 * 账号安全事件的标题, 与列表行的标题相同.
 */
fun accountSecurityTitle(content: AppNotificationContent.AccountSecurity): NotificationText =
    accountSecurityRowText(content).headline

private fun thirdPartyUnboundTitle(provider: String?): NotificationText {
    val name = providerDisplayName(provider)
        ?: return NotificationText.Res(Lang.notification_account_third_party_unbound_generic)
    return NotificationText.Res(
        Lang.notification_account_third_party_unbound,
        listOf(NotificationText.Plain(name)),
    )
}

/**
 * "平台 + 登录方式", 例如 "macOS 扫码登录". 不认识或缺少的部分省略, 都没有时返回 `null`.
 * 只有登录方式时与 [loginMethodText] 相同.
 */
fun loginSummaryText(clientPlatform: String?, loginMethod: LoginMethod?): NotificationText? {
    val platform = clientPlatformDisplayName(clientPlatform)?.let { NotificationText.Plain(it) }
    val method = loginMethod?.labelRes?.let { NotificationText.Res(it) }
    return when {
        platform != null && method != null -> NotificationText.Words(listOf(platform, method))
        platform != null -> platform
        else -> loginMethodText(loginMethod)
    }
}

/**
 * 单独显示的登录方式. 英文的登录方式跟在平台名后面时小写, 单独显示时首字母大写. 不认识或缺少时返回 `null`.
 */
fun loginMethodText(loginMethod: LoginMethod?): NotificationText? =
    loginMethod?.labelRes?.let { NotificationText.Capitalized(NotificationText.Res(it)) }

/**
 * 新设备登录的平台名, 例如 `macos-aarch64` 为 "macOS", `android-tv-arm64-v8a` 为 "Android TV".
 *
 * [platform] 是服务端下发的 `<系统>-<架构>`: 小写, 空白换成 `-`. 不认识的系统返回 `null`.
 */
fun clientPlatformDisplayName(platform: String?): String? {
    val normalized = platform?.trim()?.lowercase()?.replace(Whitespaces, "-")?.takeIf { it.isNotEmpty() }
        ?: return null
    return ClientPlatformNames.firstOrNull { (system, _) ->
        normalized == system || normalized.startsWith("$system-")
    }?.second
}

private val Whitespaces = Regex("""\s+""")

/**
 * 系统与平台名. 按顺序匹配, `android-tv` 在 `android` 之前.
 */
private val ClientPlatformNames = listOf(
    "android-tv" to "Android TV",
    "android" to "Android",
    "ios" to "iOS",
    "macos" to "macOS",
    "windows" to "Windows",
    "linux" to "Linux",
)

/**
 * 第三方平台名. 不认识的平台返回 `null`, 由调用方显示通用文案或省略.
 */
fun providerDisplayName(provider: String?): String? =
    when (provider?.trim()?.lowercase()) {
        "github" -> "GitHub"
        "bangumi" -> "Bangumi"
        else -> null
    }

/**
 * 正文的第一个非空行, 用作列表行的辅助文本.
 */
fun firstLineOf(body: String): String? =
    body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

val LoginMethod.labelRes: StringResource?
    get() = when (this) {
        LoginMethod.EMAIL_OTP -> Lang.notification_login_method_email_otp
        LoginMethod.GITHUB -> Lang.notification_login_method_github
        LoginMethod.BANGUMI -> Lang.notification_login_method_bangumi
        LoginMethod.QR_CODE -> Lang.notification_login_method_qr_code
        LoginMethod.UNKNOWN -> null
    }

val CommentReportTargetType.labelRes: StringResource
    get() = when (this) {
        CommentReportTargetType.EPISODE_COMMENT -> Lang.notification_report_target_episode_comment
        CommentReportTargetType.SUBJECT_REVIEW -> Lang.notification_report_target_subject_review
        CommentReportTargetType.PERSON_COMMENT -> Lang.notification_report_target_person_comment
        CommentReportTargetType.CHARACTER_COMMENT -> Lang.notification_report_target_character_comment
    }

/**
 * 与举报评论时的选项文字相同.
 */
val CommentReportReason.labelRes: StringResource
    get() = when (this) {
        CommentReportReason.SPAM -> Lang.comment_report_reason_spam
        CommentReportReason.HARASSMENT -> Lang.comment_report_reason_harassment
        CommentReportReason.SPOILER -> Lang.comment_report_reason_spoiler
        CommentReportReason.NSFW -> Lang.comment_report_reason_nsfw
        CommentReportReason.ILLEGAL -> Lang.comment_report_reason_illegal
        CommentReportReason.OTHER -> Lang.comment_report_reason_other
    }

/**
 * 公告分类名. 普通公告和不认识的分类都显示为 "公告".
 */
val AnnouncementTag.labelRes: StringResource
    get() = when (this) {
        AnnouncementTag.MAINTENANCE -> Lang.notification_announcement_tag_maintenance
        AnnouncementTag.RELEASE -> Lang.notification_announcement_tag_release
        AnnouncementTag.COMMUNITY -> Lang.notification_announcement_tag_community
        AnnouncementTag.GENERAL,
        AnnouncementTag.UNKNOWN,
            -> Lang.notification_announcement_tag_general
    }

private val AbsoluteDateTimeFormat = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

/**
 * 详情页使用的绝对时间, 例如 `2026-10-12 21:30`.
 */
fun formatAbsoluteDateTime(
    millis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = AbsoluteDateTimeFormat.format(Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone))

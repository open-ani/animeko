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
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationDisplay
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_reason_harassment
import me.him188.ani.app.ui.lang.comment_report_reason_illegal
import me.him188.ani.app.ui.lang.comment_report_reason_nsfw
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spam
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_generic
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_supporting
import me.him188.ani.app.ui.lang.notification_announcement_tag_community
import me.him188.ani.app.ui.lang.notification_announcement_tag_general
import me.him188.ani.app.ui.lang.notification_announcement_tag_maintenance
import me.him188.ani.app.ui.lang.notification_announcement_tag_release
import me.him188.ani.app.ui.lang.notification_report_result_row_title
import me.him188.ani.app.ui.lang.notification_report_target_character_comment
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_report_target_episode_comment
import me.him188.ani.app.ui.lang.notification_report_target_person_comment
import me.him188.ani.app.ui.lang.notification_report_target_subject_review
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_system_bangumi_reauthorize
import org.jetbrains.compose.resources.StringResource

/**
 * 通知列表一行的文本: [headline] 说明发生了什么, [supporting] 说明对象或细节.
 *
 * 各部分分开展示, 不拼接成一行. 客户端自己的文案从不引用用户自己的评论内容.
 *
 * @property supporting 没有可展示的细节时为 `null`, 此时只显示一行.
 * @property overline 标题上方的一行, 例如条目与剧集. 只有通用展示内容 ([NotificationDisplay.overline]) 有.
 */
@Immutable
data class NotificationRowText(
    val headline: NotificationText,
    val supporting: NotificationText?,
    val overline: NotificationText? = null,
)

/**
 * 计算通知列表一行的文本.
 *
 * @param useOriginalTitle 用户是否偏好显示条目原名, 见 `SubjectAppearanceSettings.useOriginalTitle`.
 */
fun notificationRowText(
    notification: AppNotification,
    useOriginalTitle: Boolean,
): NotificationRowText = when (val content = notification.content) {
    is AppNotificationContent.ReportResult -> NotificationRowText(
        headline = NotificationText.Res(Lang.notification_report_result_row_title),
        supporting = reportTargetText(content, notification.display, useOriginalTitle),
    )

    is AppNotificationContent.AccountSecurity -> accountSecurityRowText(content, notification.display)

    is AppNotificationContent.System -> systemRowText(content, notification.display)

    AppNotificationContent.Unknown -> displayRowText(notification.display)
}

/**
 * 系统消息的行文本. 认识的事件使用客户端的文案, 否则为服务端的通用展示内容.
 */
fun systemRowText(
    content: AppNotificationContent.System,
    display: NotificationDisplay,
): NotificationRowText = when (content.event) {
    SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED -> NotificationRowText(
        headline = NotificationText.Res(Lang.notification_system_bangumi_expired),
        supporting = NotificationText.Res(Lang.notification_system_bangumi_expired_supporting),
    )

    null -> displayRowText(display)
}

/**
 * 通用展示内容的行文本: 标题上方的一行、标题和正文的第一行.
 */
fun displayRowText(display: NotificationDisplay): NotificationRowText = NotificationRowText(
    headline = NotificationText.Plain(display.title),
    supporting = display.body?.let { firstLineOf(it) }?.let { NotificationText.Plain(it) },
    overline = display.overline?.trim()?.takeIf { it.isNotEmpty() }?.let { NotificationText.Plain(it) },
)

/**
 * 客户端不认识这条通知的类型. 它只能用通用展示内容展示, 当前版本可能无法完整显示, 所以列表和详情提示用户更新.
 *
 * 认识的类型缺少自己的内容时同样用通用展示内容展示, 但更新也不会改变什么, 所以不提示.
 */
val AppNotification.isUnknownType: Boolean get() = type == null

/**
 * 列表行中可以直接执行的操作, 显示在行文本下方.
 */
enum class NotificationInlineAction {
    /**
     * 重新授权 Bangumi.
     */
    REAUTHORIZE_BANGUMI,
}

val NotificationInlineAction.labelRes: StringResource
    get() = when (this) {
        NotificationInlineAction.REAUTHORIZE_BANGUMI -> Lang.notification_system_bangumi_reauthorize
    }

/**
 * 列表行的操作. 没有操作的行返回空列表.
 */
fun notificationInlineActions(content: AppNotificationContent): List<NotificationInlineAction> = when (content) {
    is AppNotificationContent.System -> when (content.event) {
        SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED -> listOf(NotificationInlineAction.REAUTHORIZE_BANGUMI)
        null -> emptyList()
    }

    is AppNotificationContent.ReportResult,
    is AppNotificationContent.AccountSecurity,
    AppNotificationContent.Unknown,
        -> emptyList()
}

/**
 * 点击列表行时打开的位置.
 */
enum class NotificationOpenTarget {
    /**
     * 通知详情. 窄屏为单独的页面, 宽屏显示在列表右侧.
     */
    DETAIL,

    /**
     * 账号设置. 账号安全通知没有详情, 相关的操作都在账号设置中.
     */
    ACCOUNT_SETTINGS,
}

fun notificationOpenTarget(content: AppNotificationContent): NotificationOpenTarget = when (content) {
    is AppNotificationContent.ReportResult,
    is AppNotificationContent.System,
    AppNotificationContent.Unknown,
        -> NotificationOpenTarget.DETAIL

    is AppNotificationContent.AccountSecurity -> NotificationOpenTarget.ACCOUNT_SETTINGS
}

/**
 * 被举报评论所在的对象: 剧集评论为 "条目名 第 N 话", 条目评论为条目名, 人物和角色评论为人物或角色名.
 * 缺少这些信息时为评论类型 (例如 "剧集评论").
 *
 * 不认识的评论类型使用通用展示内容中正文 ([NotificationDisplay.body]) 的第一行, 没有正文时显示为通用的 "评论".
 */
fun reportTargetText(
    content: AppNotificationContent.ReportResult,
    display: NotificationDisplay,
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

        null -> display.body?.let { firstLineOf(it) }?.let { NotificationText.Plain(it) } ?: fallback
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
 * 账号安全通知的行文本. 认识的事件使用客户端的文案, 否则为服务端的通用展示内容.
 */
fun accountSecurityRowText(
    content: AppNotificationContent.AccountSecurity,
    display: NotificationDisplay,
): NotificationRowText =
    when (content.event) {
        AccountSecurityEvent.THIRD_PARTY_UNBOUND -> NotificationRowText(
            headline = thirdPartyUnboundTitle(content.provider),
            supporting = NotificationText.Res(Lang.notification_account_third_party_unbound_supporting),
        )

        AccountSecurityEvent.UNKNOWN -> displayRowText(display)
    }

private fun thirdPartyUnboundTitle(provider: String?): NotificationText {
    val name = providerDisplayName(provider)
        ?: return NotificationText.Res(Lang.notification_account_third_party_unbound_generic)
    return NotificationText.Res(
        Lang.notification_account_third_party_unbound,
        listOf(NotificationText.Plain(name)),
    )
}

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

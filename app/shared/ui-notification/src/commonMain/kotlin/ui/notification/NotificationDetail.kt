/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_report_go_to_character
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_report_go_to_person
import me.him188.ani.app.ui.lang.notification_report_go_to_subject
import me.him188.ani.app.ui.lang.notification_report_handled_at
import me.him188.ani.app.ui.lang.notification_report_outcome
import me.him188.ani.app.ui.lang.notification_report_outcome_handled
import me.him188.ani.app.ui.lang.notification_report_outcome_removed
import me.him188.ani.app.ui.lang.notification_report_privacy_footnote
import me.him188.ani.app.ui.lang.notification_report_reason
import me.him188.ani.app.ui.lang.notification_report_reported_at
import me.him188.ani.app.ui.lang.notification_report_result_description_handled
import me.him188.ani.app.ui.lang.notification_report_result_description_removed
import me.him188.ani.app.ui.lang.notification_report_result_detail_title
import me.him188.ani.app.ui.lang.notification_report_result_upheld
import me.him188.ani.app.ui.lang.notification_report_snapshot_footnote
import me.him188.ani.app.ui.lang.notification_report_snapshot_title
import me.him188.ani.app.ui.lang.notification_report_target
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_system_bangumi_reauthorize
import me.him188.ani.app.ui.lang.notification_system_detail_title
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.lang.notification_view_details
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 详情内容的最大宽度.
 */
private val DetailContentMaxWidth = 640.dp

/**
 * 详情内容的宽度: 最宽 [DetailContentMaxWidth], 可用宽度更大时 (宽屏) 水平居中, 否则 (手机) 填满.
 *
 * 通知和公告的详情、加载失败、找不到和未选择时的提示都使用这个宽度, 让它们在宽屏中位于同一列.
 */
internal fun Modifier.detailContentWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = DetailContentMaxWidth)
        .fillMaxWidth()

/**
 * 可纵向滚动的详情内容. 整个区域都可以滚动, 内容列的宽度由 [detailContentWidth] 决定.
 *
 * @param testTag 内容列的测试标签.
 */
@Composable
internal fun DetailContentColumn(
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .detailContentWidth()
                .testTag(testTag)
                .padding(top = 4.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/**
 * 举报结果详情的跳转按钮的目标. 缺少目标的 ID 时没有按钮.
 *
 * 按钮通常在详情底部; 宽屏双栏中是详情栏顶栏的图标按钮, 见 [NotificationDetailTopBarActions].
 */
@Immutable
sealed interface ReportResultAction {
    @Immutable
    data class OpenEpisode(val subjectId: Int, val episodeId: Int) : ReportResultAction

    @Immutable
    data class OpenSubject(val subjectId: Int) : ReportResultAction

    @Immutable
    data class OpenPerson(val personId: Int) : ReportResultAction

    @Immutable
    data class OpenCharacter(val characterId: Int) : ReportResultAction
}

fun reportResultAction(content: AppNotificationContent.ReportResult): ReportResultAction? =
    when (content.targetType) {
        null -> null

        CommentReportTargetType.EPISODE_COMMENT -> {
            val subjectId = content.subjectId
            val episodeId = content.episodeId
            if (subjectId != null && episodeId != null) {
                ReportResultAction.OpenEpisode(subjectId.toInt(), episodeId.toInt())
            } else {
                null
            }
        }

        CommentReportTargetType.SUBJECT_REVIEW -> content.subjectId?.let { ReportResultAction.OpenSubject(it.toInt()) }
        CommentReportTargetType.PERSON_COMMENT -> content.personId?.let { ReportResultAction.OpenPerson(it.toInt()) }
        CommentReportTargetType.CHARACTER_COMMENT ->
            content.characterId?.let { ReportResultAction.OpenCharacter(it.toInt()) }
    }

val ReportResultAction.labelRes: StringResource
    get() = when (this) {
        is ReportResultAction.OpenEpisode -> Lang.notification_report_go_to_episode
        is ReportResultAction.OpenSubject -> Lang.notification_report_go_to_subject
        is ReportResultAction.OpenPerson -> Lang.notification_report_go_to_person
        is ReportResultAction.OpenCharacter -> Lang.notification_report_go_to_character
    }

/**
 * 执行 [action]. 打开条目时用 [content] 中的条目名作为页面加载完成前的占位.
 */
fun NotificationActions.perform(
    action: ReportResultAction,
    content: AppNotificationContent.ReportResult,
) {
    when (action) {
        is ReportResultAction.OpenEpisode -> onOpenEpisode(action.subjectId, action.episodeId)
        is ReportResultAction.OpenSubject -> onOpenSubject(
            action.subjectId,
            SubjectDetailPlaceholder(
                id = action.subjectId,
                name = content.subjectName.orEmpty(),
                nameCN = content.subjectNameCn.orEmpty(),
            ),
        )

        is ReportResultAction.OpenPerson -> onOpenPerson(action.personId)
        is ReportResultAction.OpenCharacter -> onOpenCharacter(action.characterId)
    }
}

/**
 * 详情页顶栏的标题.
 */
@Composable
fun notificationDetailTitle(content: AppNotificationContent): String = when (content) {
    is AppNotificationContent.ReportResult -> stringResource(Lang.notification_report_result_detail_title)
    is AppNotificationContent.AccountSecurity -> stringResource(Lang.notification_title)
    is AppNotificationContent.System -> stringResource(Lang.notification_system_detail_title)
}

/**
 * 一条通知的详情内容 (不含顶栏). 自带纵向滚动.
 *
 * 账号安全通知没有详情 (见 [NotificationOpenTarget]), 不显示内容.
 *
 * @param reportActionInTopBar 举报结果的跳转按钮由顶栏的 [NotificationDetailTopBarActions] 显示, 此时内容底部没有这个按钮.
 * 用于宽屏双栏.
 */
@Composable
fun NotificationDetailContent(
    notification: AppNotification,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
    reportActionInTopBar: Boolean = false,
) {
    DetailContentColumn(NotificationTestTags.DETAIL, modifier) {
        when (val content = notification.content) {
            is AppNotificationContent.ReportResult -> ReportResultDetail(content, actions, reportActionInTopBar)
            is AppNotificationContent.AccountSecurity -> {}
            is AppNotificationContent.System -> SystemNotificationDetail(content, notification.createdAtMillis, actions)
        }
    }
}

/**
 * 详情栏顶栏中的操作: 举报结果的跳转按钮 (见 [ReportResultAction]), 显示为图标按钮. 其他通知没有.
 *
 * 与 `NotificationDetailContent(reportActionInTopBar = true)` 一起使用.
 */
@Composable
fun NotificationDetailTopBarActions(
    notification: AppNotification,
    actions: NotificationActions,
) {
    val content = notification.content as? AppNotificationContent.ReportResult ?: return
    val action = reportResultAction(content) ?: return
    IconButton(
        onClick = { actions.perform(action, content) },
        Modifier.testTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = stringResource(action.labelRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailHeader(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        Modifier.padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * 详情中的一行 "名称 - 值", 上方有分隔线.
 */
@Composable
private fun DetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    DetailDivider()
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Medium else null,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailFootnote(text: String) {
    DetailDivider()
    Row(
        Modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * 详情底部的文字按钮. 左移 12dp 让文字与内容对齐.
 */
@Composable
private fun DetailActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    TextButton(onClick, modifier.offset(x = (-12).dp)) {
        Text(text)
        if (icon != null) {
            Spacer(Modifier.size(8.dp))
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReportResultDetail(
    content: AppNotificationContent.ReportResult,
    actions: NotificationActions,
    actionInTopBar: Boolean,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    DetailHeader(
        Icons.Outlined.TaskAlt,
        title = stringResource(Lang.notification_report_result_upheld),
        description = stringResource(
            if (content.contentRemoved) Lang.notification_report_result_description_removed
            else Lang.notification_report_result_description_handled,
        ),
    )

    DetailField(
        stringResource(Lang.notification_report_reason),
        stringResource(content.reason.labelRes),
        Modifier.testTag(NotificationTestTags.REPORT_REASON),
    )
    DetailField(
        stringResource(Lang.notification_report_target),
        reportTargetText(content, useOriginalTitle).resolve(),
        Modifier.testTag(NotificationTestTags.REPORT_TARGET),
    )
    DetailField(
        stringResource(Lang.notification_report_reported_at),
        formatNotificationDetailTime(content.reportedAtMillis),
    )
    DetailField(
        stringResource(Lang.notification_report_handled_at),
        formatNotificationDetailTime(content.handledAtMillis),
    )
    DetailField(
        stringResource(Lang.notification_report_outcome),
        stringResource(
            if (content.contentRemoved) Lang.notification_report_outcome_removed
            else Lang.notification_report_outcome_handled,
        ),
        Modifier.testTag(NotificationTestTags.REPORT_OUTCOME),
        emphasize = true,
    )

    // 举报时可以不附带快照
    val snapshot = content.contentSnapshot?.takeIf { it.isNotBlank() }
    if (snapshot != null) {
        ReportSnapshotSection(remember(snapshot) { parseReportSnapshot(snapshot) }, content.contentCreatedAtMillis)
    }

    val privacyFootnote = stringResource(Lang.notification_report_privacy_footnote)
    DetailFootnote(
        if (snapshot != null) {
            stringResource(Lang.notification_report_snapshot_footnote, privacyFootnote)
        } else {
            privacyFootnote
        },
    )

    val action = reportResultAction(content)
    if (action != null && !actionInTopBar) {
        DetailActionButton(
            stringResource(action.labelRes),
            onClick = { actions.perform(action, content) },
            Modifier.testTag(NotificationTestTags.DETAIL_ACTION),
        )
    }
}

/**
 * @param createdAtMillis 被举报评论的发表时间, 显示在昵称后面.
 */
@Composable
private fun ReportSnapshotSection(snapshot: ReportSnapshot, createdAtMillis: Long?) {
    DetailDivider()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Lang.notification_report_snapshot_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val author = snapshot.author
            if (author != null) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        author.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (author != null || createdAtMillis != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (author != null) {
                            Text(
                                author,
                                Modifier.weight(1f, fill = false).testTag(NotificationTestTags.REPORT_SNAPSHOT_AUTHOR),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (createdAtMillis != null) {
                            Text(
                                formatNotificationDetailTime(createdAtMillis),
                                Modifier.testTag(NotificationTestTags.REPORT_SNAPSHOT_TIME),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Text(
                    snapshot.text,
                    Modifier.testTag(NotificationTestTags.REPORT_SNAPSHOT_TEXT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 系统消息. 认识的事件使用客户端的文案和操作, 否则为服务端的标题、正文和链接.
 */
@Composable
private fun SystemNotificationDetail(
    content: AppNotificationContent.System,
    createdAtMillis: Long,
    actions: NotificationActions,
) {
    when (content.event) {
        SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED -> {
            LinkedTextDetail(
                title = stringResource(Lang.notification_system_bangumi_expired),
                timeMillis = createdAtMillis,
                body = stringResource(Lang.notification_system_bangumi_expired_supporting),
                linkUrl = null,
                linkTestTag = NotificationTestTags.DETAIL_LINK,
            )
            Spacer(Modifier.height(4.dp))
            DetailActionButton(
                stringResource(Lang.notification_system_bangumi_reauthorize),
                onClick = actions.onReauthorizeBangumi,
                Modifier.testTag(NotificationTestTags.DETAIL_ACTION),
            )
        }

        null -> LinkedTextDetail(
            title = content.title,
            timeMillis = createdAtMillis,
            body = content.body,
            linkUrl = content.linkUrl,
            linkTestTag = NotificationTestTags.DETAIL_LINK,
        )
    }
}

/**
 * 标题、时间、正文和可选的 "查看详情" 链接. 用于系统通知和公告.
 *
 * 正文可选择复制, 其中的网址可点击, 点击后在浏览器中打开.
 *
 * @param overline 标题上方的内容, 例如公告分类.
 */
@Composable
internal fun LinkedTextDetail(
    title: String,
    timeMillis: Long,
    body: String,
    linkUrl: String?,
    linkTestTag: String,
    overline: (@Composable () -> Unit)? = null,
) {
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedBody = remember(body, linkColor, browserNavigator, context) {
        linkifyText(
            body,
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            onClickUrl = { browserNavigator.openBrowser(context, it) },
        )
    }

    Column(
        Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        overline?.invoke()
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            formatNotificationDetailTime(timeMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(annotatedBody, style = MaterialTheme.typography.bodyLarge)
        }
        if (linkUrl != null) {
            Spacer(Modifier.height(4.dp))
            DetailActionButton(
                stringResource(Lang.notification_view_details),
                onClick = { browserNavigator.openBrowser(context, linkUrl) },
                Modifier.testTag(linkTestTag),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        }
    }
}

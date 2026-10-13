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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_overline
import me.him188.ani.app.ui.lang.notification_announcement_pinned
import me.him188.ani.app.ui.lang.notification_unread
import org.jetbrains.compose.resources.stringResource

/**
 * 通知和公告列表项的尺寸. 遵循 M3 列表项: 两行项最小高度 72dp, 三行项 88dp, 上下 10dp, 左右 16dp, 元素间距 12dp.
 */
@Stable
object NotificationListItemDefaults {
    val TwoLineMinHeight = 72.dp
    val ThreeLineMinHeight = 88.dp
    val VerticalPadding = 10.dp
    val HorizontalPadding = 16.dp
    val Spacing = 12.dp
    val LeadingSize = 40.dp
    val LeadingIconSize = 20.dp
    val UnreadDotSize = 8.dp

    /**
     * 宽屏双栏时选中项的圆角.
     */
    val SelectedShape = RoundedCornerShape(16.dp)

    /**
     * 宽屏双栏时列表项之间的空隙. 双栏不使用分隔线.
     */
    val TwoPaneItemSpacing = 2.dp

    fun iconFor(type: NotificationType): ImageVector = when (type) {
        NotificationType.REPORT_RESULT -> Icons.Rounded.Flag
        NotificationType.ACCOUNT_SECURITY -> Icons.Rounded.Shield
        NotificationType.SYSTEM,
        NotificationType.COMMENT_REPLY,
        NotificationType.MENTION,
        NotificationType.REACTION,
        NotificationType.FOLLOW,
        NotificationType.DIRECT_MESSAGE,
            -> Icons.Rounded.Info
    }
}

/**
 * 列表项各部分的颜色. 未读项强调, 已读项整体弱化为 `outline`; 宽屏中选中的项使用 `secondaryContainer` 背景.
 */
@Immutable
private data class ListItemColorSet(
    val container: Color,
    val headline: Color,
    val supporting: Color,
    val trailing: Color,
    val leadingContainer: Color,
    val leadingContent: Color,
)

@Composable
private fun listItemColors(read: Boolean, selected: Boolean): ListItemColorSet {
    val scheme = MaterialTheme.colorScheme
    return when {
        selected -> ListItemColorSet(
            container = scheme.secondaryContainer,
            headline = scheme.onSecondaryContainer,
            supporting = scheme.onSecondaryContainer,
            trailing = scheme.onSecondaryContainer,
            leadingContainer = scheme.primaryContainer,
            leadingContent = scheme.onPrimaryContainer,
        )

        read -> ListItemColorSet(
            container = Color.Transparent,
            headline = scheme.outline,
            supporting = scheme.outline,
            trailing = scheme.outline,
            leadingContainer = scheme.surfaceContainerHigh,
            leadingContent = scheme.outline,
        )

        else -> ListItemColorSet(
            container = Color.Transparent,
            headline = scheme.onSurface,
            supporting = scheme.onSurfaceVariant,
            trailing = scheme.onSurfaceVariant,
            leadingContainer = scheme.secondaryContainer,
            leadingContent = scheme.onSecondaryContainer,
        )
    }
}

/**
 * 未读标记.
 */
@Composable
fun UnreadDot(
    modifier: Modifier = Modifier,
    size: Dp = NotificationListItemDefaults.UnreadDotSize,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/**
 * 通知列表的一行: 左侧类型图标, 第一行为标题、时间和未读标记, 第二行为辅助文本.
 *
 * @param supporting 为 `null` 时只显示标题行.
 * @param read 已读项整体弱化, 不显示未读标记.
 * @param selected 宽屏双栏时当前正在右侧展示的项.
 */
@Composable
fun NotificationListItem(
    headline: String,
    supporting: String?,
    time: String,
    icon: ImageVector,
    read: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = listItemColors(read = read, selected = selected)
    val unreadText = stringResource(Lang.notification_unread)
    Row(
        modifier
            .fillMaxWidth()
            .clip(NotificationListItemDefaults.SelectedShape)
            .background(colors.container)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                if (!read) stateDescription = unreadText
            }
            .heightIn(min = NotificationListItemDefaults.TwoLineMinHeight)
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.VerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NotificationListItemDefaults.LeadingSize)
                .clip(CircleShape)
                .background(colors.leadingContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(NotificationListItemDefaults.LeadingIconSize),
                tint = colors.leadingContent,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    headline,
                    Modifier.weight(1f),
                    color = colors.headline,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    time,
                    color = colors.trailing,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!read) {
                    UnreadDot(Modifier.testTag(NotificationTestTags.UNREAD_DOT))
                }
            }
            if (supporting != null) {
                Text(
                    supporting,
                    color = colors.supporting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 首次加载时的占位行.
 */
@Composable
fun NotificationListItemPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NotificationListItemDefaults.TwoLineMinHeight)
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.VerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NotificationListItemDefaults.LeadingSize)
                .placeholder(true, shape = CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "",
                Modifier.fillMaxWidth(0.6f).placeholder(true),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "",
                Modifier.fillMaxWidth(0.4f).placeholder(true),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 列表项之间的分隔线, 左右各缩进 16dp.
 */
@Composable
fun NotificationListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier.padding(horizontal = NotificationListItemDefaults.HorizontalPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 两个列表项之间的间隔.
 *
 * 单栏时列表项贴边, 用 [NotificationListDivider] 分隔. 双栏时列表项是带圆角选中背景的块, 分隔线会贴着选中背景,
 * 因此只留 2dp 空隙.
 */
@Composable
fun NotificationListItemSeparator(
    singlePane: Boolean,
    modifier: Modifier = Modifier,
) {
    if (singlePane) {
        NotificationListDivider(modifier)
    } else {
        Spacer(modifier.height(NotificationListItemDefaults.TwoPaneItemSpacing))
    }
}

/**
 * 通知中心顶部的公告入口, 显示最新一条公告的标题.
 *
 * @param hasUnread 有未读公告时显示红点.
 */
@Composable
fun AnnouncementEntryItem(
    announcement: Announcement,
    hasUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val unreadText = stringResource(Lang.notification_unread)
    Row(
        modifier
            .fillMaxWidth()
            .clip(NotificationListItemDefaults.SelectedShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                if (hasUnread) stateDescription = unreadText
            }
            .testTag(NotificationTestTags.ANNOUNCEMENT_ENTRY)
            .heightIn(min = NotificationListItemDefaults.TwoLineMinHeight)
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.VerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NotificationListItemDefaults.LeadingSize)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Campaign,
                contentDescription = null,
                Modifier.size(NotificationListItemDefaults.LeadingIconSize),
                tint = scheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Lang.notification_announcement_overline),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                announcement.title,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (hasUnread) {
            UnreadDot(
                Modifier.testTag(NotificationTestTags.ANNOUNCEMENT_ENTRY_DOT),
                size = 6.dp,
                color = scheme.error,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            Modifier.size(20.dp),
            tint = scheme.outline,
        )
    }
}

/**
 * 公告列表的一行 (三行): 第一行为置顶图标、分类、日期和未读标记, 第二行标题, 第三行正文的第一行.
 *
 * @param read 游客没有已读状态, 为 `null`: 不弱化也不显示未读标记.
 */
@Composable
fun AnnouncementListItem(
    announcement: Announcement,
    read: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = listItemColors(read = read == true, selected = selected)
    val scheme = MaterialTheme.colorScheme
    val tagColor = when {
        selected -> colors.headline
        read == true -> colors.trailing
        else -> scheme.primary
    }
    val unreadText = stringResource(Lang.notification_unread)
    Column(
        modifier
            .fillMaxWidth()
            .clip(NotificationListItemDefaults.SelectedShape)
            .background(colors.container)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                if (read == false) stateDescription = unreadText
            }
            .heightIn(min = NotificationListItemDefaults.ThreeLineMinHeight)
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.VerticalPadding,
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (announcement.pinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(Lang.notification_announcement_pinned),
                    Modifier.size(14.dp).testTag(AnnouncementTestTags.PIN),
                    tint = tagColor,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                stringResource(announcement.tag.labelRes),
                Modifier.testTag(AnnouncementTestTags.TAG),
                color = tagColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f).width(NotificationListItemDefaults.Spacing))
            Text(
                formatNotificationRowTime(announcement.publishedAtMillis),
                Modifier.padding(start = NotificationListItemDefaults.Spacing),
                color = colors.trailing,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
            if (read == false) {
                UnreadDot(
                    Modifier
                        .padding(start = NotificationListItemDefaults.Spacing)
                        .testTag(AnnouncementTestTags.UNREAD_DOT),
                )
            }
        }
        Text(
            announcement.title,
            color = colors.headline,
            fontWeight = if (read == true) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        firstLineOf(announcement.body)?.let { firstLine ->
            Text(
                firstLine,
                color = colors.supporting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

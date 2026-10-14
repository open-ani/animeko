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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_overline
import me.him188.ani.app.ui.lang.notification_announcement_pinned
import me.him188.ani.app.ui.lang.notification_unread
import me.him188.ani.app.ui.lang.notification_update_banner
import me.him188.ani.app.ui.lang.notification_update_banner_action
import org.jetbrains.compose.resources.stringResource

/**
 * 通知和公告列表项的尺寸. 遵循 M3 列表项: 单行项最小高度 56dp, 上下 8dp; 两行项 72dp, 三行项 88dp, 上下 10dp.
 * 左右 16dp, 元素间距 12dp.
 */
@Stable
object NotificationListItemDefaults {
    val OneLineMinHeight = 56.dp
    val TwoLineMinHeight = 72.dp
    val ThreeLineMinHeight = 88.dp
    val OneLineVerticalPadding = 8.dp
    val VerticalPadding = 10.dp
    val HorizontalPadding = 16.dp
    val Spacing = 12.dp
    val LeadingSize = 40.dp
    val LeadingIconSize = 20.dp
    val UnreadDotSize = 8.dp

    /**
     * 行内操作按钮: 高 40dp, 左右内边距 12dp. 按钮之间相隔 4dp, 与上方文本相隔 4dp.
     */
    val InlineActionHeight = 40.dp
    val InlineActionHorizontalPadding = 12.dp
    val InlineActionSpacing = 4.dp

    /**
     * 宽屏双栏时选中项的圆角.
     */
    val SelectedShape = RoundedCornerShape(16.dp)

    /**
     * 宽屏双栏时列表项之间的空隙. 双栏不使用分隔线.
     */
    val TwoPaneItemSpacing = 2.dp

    /**
     * 有 [lines] 行文本 (行内操作按钮也算一行) 的列表项的最小高度.
     */
    fun minHeight(lines: Int): Dp = when {
        lines <= 1 -> OneLineMinHeight
        lines == 2 -> TwoLineMinHeight
        else -> ThreeLineMinHeight
    }

    /**
     * 有 [lines] 行文本 (行内操作按钮也算一行) 的列表项的上下内边距.
     */
    fun verticalPadding(lines: Int): Dp = if (lines <= 1) OneLineVerticalPadding else VerticalPadding

    /**
     * 列表行的图标. 客户端认识的类型使用固定的图标, 其他通知使用通用展示内容中的图标 ([iconFor]).
     */
    fun iconFor(notification: AppNotification): ImageVector = when (notification.content) {
        is AppNotificationContent.ReportResult -> Icons.Rounded.Flag
        is AppNotificationContent.AccountSecurity -> Icons.Rounded.Shield
        is AppNotificationContent.System -> Icons.Rounded.Info
        AppNotificationContent.Unknown -> iconFor(notification.display.icon)
    }

    /**
     * 列表行左侧代替图标显示的图片, 例如对方的头像. 只有用通用展示内容展示的通知有.
     */
    fun imageUrlFor(notification: AppNotification): String? = when (notification.content) {
        AppNotificationContent.Unknown -> notification.display.imageUrl?.takeIf { it.isNotBlank() }
        is AppNotificationContent.ReportResult,
        is AppNotificationContent.AccountSecurity,
        is AppNotificationContent.System,
            -> null
    }

    /**
     * 通用展示内容的图标. 没有或不认识的图标为通用的通知图标.
     */
    fun iconFor(icon: NotificationIcon?): ImageVector = when (icon) {
        NotificationIcon.REPORT -> Icons.Rounded.Flag
        NotificationIcon.SECURITY -> Icons.Rounded.Shield
        NotificationIcon.INFO -> Icons.Rounded.Info
        NotificationIcon.REPLY -> Icons.AutoMirrored.Rounded.Reply
        NotificationIcon.MENTION -> Icons.Rounded.AlternateEmail
        NotificationIcon.REACTION -> Icons.Rounded.AddReaction
        NotificationIcon.FOLLOW -> Icons.Rounded.PersonAdd
        null -> Icons.Rounded.Notifications
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
 * 通知列表的一行: 左侧类型图标或图片, 第一行为标题、时间和未读标记, 第二行为辅助文本.
 *
 * 有 [overline] 时它在标题上方, 时间和未读标记随之移到这一行, 例如互动的 "条目与剧集 / 谁做了什么 / 内容预览".
 * 有 [actions] 时最后一行为操作按钮. 有操作按钮或有三行及以上的项顶部对齐. 点击按钮只执行按钮的操作, 点击行的其他位置调用 [onClick].
 * 最小高度和上下内边距按行数取 [NotificationListItemDefaults.minHeight] 和 [NotificationListItemDefaults.verticalPadding].
 *
 * @param supporting 为 `null` 时没有辅助文本行.
 * @param read 已读项整体弱化, 不显示未读标记.
 * @param selected 宽屏双栏时当前正在右侧展示的项.
 * @param overline 标题上方的一行.
 * @param imageUrl 代替 [icon] 显示的圆形图片, 例如对方的头像. 加载完成前和加载失败时显示 [icon].
 * @param actions 行内操作, 通常是几个 [NotificationInlineActionButton]. 按钮文字与辅助文本左对齐.
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
    overline: String? = null,
    imageUrl: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = listItemColors(read = read, selected = selected)
    val unreadText = stringResource(Lang.notification_unread)
    val lines = (if (overline != null) 1 else 0) + 1 +
            (if (supporting != null) 1 else 0) + (if (actions != null) 1 else 0)
    Row(
        modifier
            .fillMaxWidth()
            .clip(NotificationListItemDefaults.SelectedShape)
            .background(colors.container)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                if (!read) stateDescription = unreadText
            }
            .heightIn(min = NotificationListItemDefaults.minHeight(lines))
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.verticalPadding(lines),
            ),
        horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
        verticalAlignment = if (actions != null || lines >= 3) Alignment.Top else Alignment.CenterVertically,
    ) {
        NotificationLeading(
            icon,
            imageUrl,
            containerColor = colors.leadingContainer,
            contentColor = colors.leadingContent,
        )
        Column(Modifier.weight(1f)) {
            @Composable
            fun HeadlineText(modifier: Modifier = Modifier) {
                Text(
                    headline,
                    modifier,
                    color = colors.headline,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.Spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (overline != null) {
                    Text(
                        overline,
                        Modifier.weight(1f).testTag(NotificationTestTags.ROW_OVERLINE),
                        color = colors.supporting,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    HeadlineText(Modifier.weight(1f))
                }
                Text(
                    time,
                    color = colors.trailing,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!read) {
                    UnreadDot(Modifier.testTag(NotificationTestTags.UNREAD_DOT))
                }
            }
            if (overline != null) {
                HeadlineText()
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
            if (actions != null) {
                Row(
                    Modifier
                        .padding(top = NotificationListItemDefaults.InlineActionSpacing)
                        // 抵消按钮的内边距, 让文字与上方文本对齐
                        .offset(x = -NotificationListItemDefaults.InlineActionHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(NotificationListItemDefaults.InlineActionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/**
 * 列表行和详情左侧的圆形图标. 有 [imageUrl] 时显示图片, 图片加载完成前和加载失败时显示 [icon].
 */
@Composable
fun NotificationLeading(
    icon: ImageVector,
    imageUrl: String?,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(NotificationListItemDefaults.LeadingSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier.size(NotificationListItemDefaults.LeadingIconSize),
            tint = contentColor,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                Modifier
                    .matchParentSize()
                    .testTag(NotificationTestTags.LEADING_IMAGE),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * 列表顶部的提示: 列表中有当前版本不认识类型的通知, 它们只用通用展示内容显示, 更新后才能完整显示.
 *
 * @param onUpdate 点击 "更新", 前往软件更新设置.
 */
@Composable
fun NotificationUpdateBanner(
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .testTag(NotificationTestTags.UPDATE_BANNER)
            // 读屏把提示作为一个整体读出, 按钮仍可单独操作
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Update,
            contentDescription = null,
            Modifier.size(20.dp),
            tint = scheme.onSurfaceVariant,
        )
        Text(
            stringResource(Lang.notification_update_banner),
            Modifier.weight(1f).padding(vertical = 12.dp),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onUpdate, Modifier.testTag(NotificationTestTags.UPDATE_BANNER_ACTION)) {
            Text(stringResource(Lang.notification_update_banner_action))
        }
    }
}

/**
 * 通知列表行内的文字按钮, 用于 [NotificationListItem] 的 `actions`.
 */
@Composable
fun NotificationInlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick,
        modifier.height(NotificationListItemDefaults.InlineActionHeight),
        contentPadding = PaddingValues(horizontal = NotificationListItemDefaults.InlineActionHorizontalPadding),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
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
                fontWeight = FontWeight.Normal,
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
 * 公告列表的一行 (三行): 第一行为置顶图标、分类、日期和未读标记, 第二行标题, 第三行正文的第一行. 正文为空时只有两行.
 * 日期不用相对时间, 见 [announcementRowTime].
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
    val firstLine = remember(announcement.body) { firstLineOf(announcement.body) }
    val lines = if (firstLine != null) 3 else 2
    Column(
        modifier
            .fillMaxWidth()
            .clip(NotificationListItemDefaults.SelectedShape)
            .background(colors.container)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                if (read == false) stateDescription = unreadText
            }
            .heightIn(min = NotificationListItemDefaults.minHeight(lines))
            .padding(
                horizontal = NotificationListItemDefaults.HorizontalPadding,
                vertical = NotificationListItemDefaults.verticalPadding(lines),
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
                formatAnnouncementRowTime(announcement.publishedAtMillis),
                Modifier.padding(start = NotificationListItemDefaults.Spacing),
                color = colors.trailing,
                fontWeight = FontWeight.Normal,
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
        if (firstLine != null) {
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

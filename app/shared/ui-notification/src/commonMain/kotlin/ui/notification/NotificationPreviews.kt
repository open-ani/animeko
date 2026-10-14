/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.notification

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 预览环境, 并固定列表中相对时间的 "现在", 与预览数据一致.
 */
@Composable
private fun PreviewHost(content: @Composable () -> Unit) = ProvideCompositionLocalsForPreview {
    CompositionLocalProvider(LocalNotificationTimeContext provides TestNotificationTimeContext) {
        content()
    }
}

/**
 * 每个标签页一个. [NotificationGroup.NOTICE] 显示 [notices], 预留的分组没有通知.
 */
@Composable
private fun rememberPreviewPages(notices: List<AppNotification>): List<NotificationCenterPage> =
    NotificationGroup.TABS.map { group ->
        NotificationCenterPage(
            group = group,
            items = rememberTestLazyPagingItems(if (group == NotificationGroup.NOTICE) notices else emptyList()),
            readOverlay = ReadOverlay.Empty,
        )
    }

@Composable
private fun PreviewNotificationCenter(
    selectedGroup: NotificationGroup,
    access: NotificationAccess = NotificationAccess.AUTHORIZED,
    notices: List<AppNotification> = TestNotifications,
    badgeState: NotificationBadgeState = TestNotificationBadgeState,
) = PreviewHost {
    NotificationCenterScreen(
        pages = rememberPreviewPages(notices),
        selectedGroup = selectedGroup,
        onSelectGroup = {},
        access = access,
        badgeState = badgeState,
        onOpenNotification = {},
        onMarkAllRead = { _, _ -> },
        onRefresh = {},
        onNavigateToDetail = {},
        onNavigateToAnnouncements = {},
        onNavigateToSettings = {},
        onLogin = {},
        actions = NotificationActions.Noop,
    )
}

@Composable
@Preview
private fun PreviewNotificationCenterNotice() = PreviewNotificationCenter(NotificationGroup.NOTICE)

@Composable
@Preview
private fun PreviewNotificationCenterInteraction() = PreviewNotificationCenter(NotificationGroup.INTERACTION)

@Composable
@Preview
private fun PreviewNotificationCenterGuest() = PreviewNotificationCenter(
    NotificationGroup.NOTICE,
    access = NotificationAccess.GUEST,
    notices = emptyList(),
    badgeState = TestNotificationBadgeState.copy(
        unreadCount = 0,
        hasUnreadAnnouncements = false,
        unreadAnnouncementCount = 0,
    ),
)

@Composable
@Preview
private fun PreviewReportResultDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestReportResult()),
            actions = NotificationActions.Noop,
            onRetry = {},
        )
    }
}

@Composable
@Preview
private fun PreviewBangumiExpiredDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestBangumiExpired()),
            actions = NotificationActions.Noop,
            onRetry = {},
        )
    }
}

@Composable
@Preview
private fun PreviewSystemDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestSystemNotification()),
            actions = NotificationActions.Noop,
            onRetry = {},
        )
    }
}

@Composable
@Preview
private fun PreviewAnnouncements() = PreviewHost {
    AnnouncementsScreen(
        items = rememberTestLazyPagingItems(TestAnnouncements),
        access = NotificationAccess.AUTHORIZED,
        readOverlay = ReadOverlay.Empty,
        onOpenAnnouncement = {},
        onRefresh = {},
        onNavigateToDetail = {},
        onLogin = {},
    )
}

@Composable
@Preview
private fun PreviewAnnouncementDetail() = PreviewHost {
    AnnouncementDetailScreen(
        state = DetailLoadState.Loaded(TestAnnouncements.first()),
        onRetry = {},
    )
}

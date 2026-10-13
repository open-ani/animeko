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

@Composable
@Preview
private fun PreviewNotificationCenter() = PreviewHost {
    NotificationCenterScreen(
        items = rememberTestLazyPagingItems(TestNotifications),
        access = NotificationAccess.AUTHORIZED,
        readOverlay = ReadOverlay.Empty,
        badgeState = TestNotificationBadgeState,
        groups = NotificationGroup.SUPPORTED,
        selectedGroup = NotificationGroup.NOTICE,
        onSelectGroup = {},
        onOpenNotification = {},
        onMarkAllRead = {},
        onRefresh = {},
        onNavigateToDetail = {},
        onNavigateToAnnouncements = {},
        onNavigateToSettings = {},
        onLogin = {},
        detailActions = NotificationDetailActions.Noop,
    )
}

@Composable
@Preview
private fun PreviewNotificationCenterGuest() = PreviewHost {
    NotificationCenterScreen(
        items = rememberTestLazyPagingItems(emptyList()),
        access = NotificationAccess.GUEST,
        readOverlay = ReadOverlay.Empty,
        badgeState = TestNotificationBadgeState.copy(
            unreadCount = 0,
            hasUnreadAnnouncements = false,
            unreadAnnouncementCount = 0,
        ),
        groups = NotificationGroup.SUPPORTED,
        selectedGroup = NotificationGroup.NOTICE,
        onSelectGroup = {},
        onOpenNotification = {},
        onMarkAllRead = {},
        onRefresh = {},
        onNavigateToDetail = {},
        onNavigateToAnnouncements = {},
        onNavigateToSettings = {},
        onLogin = {},
        detailActions = NotificationDetailActions.Noop,
    )
}

@Composable
@Preview
private fun PreviewReportResultDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestReportResult()),
            actions = NotificationDetailActions.Noop,
            onRetry = {},
        )
    }
}

@Composable
@Preview
private fun PreviewAccountSecurityDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestAccountSecurity()),
            actions = NotificationDetailActions.Noop,
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
            actions = NotificationDetailActions.Noop,
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
        onMarkAllRead = {},
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

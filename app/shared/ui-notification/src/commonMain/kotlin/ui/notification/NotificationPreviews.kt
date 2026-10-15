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
 * 每个标签页一个. [NotificationGroup.NOTICE] 显示 [notices], [NotificationGroup.INTERACTION] 显示 [interactions].
 */
@Composable
private fun rememberPreviewPages(
    notices: List<AppNotification>,
    interactions: List<AppNotification>,
): List<NotificationCenterPage> =
    NotificationGroup.TABS.map { group ->
        NotificationCenterPage(
            group = group,
            items = rememberTestLazyPagingItems(
                when (group) {
                    NotificationGroup.INTERACTION -> interactions
                    NotificationGroup.NOTICE -> notices
                },
            ),
            readOverlay = ReadOverlay.Empty,
        )
    }

/**
 * 没有未读的通知和公告, 公告入口显示最新的公告.
 */
private val ReadBadgeState: NotificationBadgeState
    get() = TestNotificationBadgeState.copy(
        unreadCounts = emptyMap(),
        hasUnreadAnnouncements = false,
        unreadAnnouncementCount = 0,
    )

@Composable
private fun PreviewNotificationCenter(
    selectedGroup: NotificationGroup,
    access: NotificationAccess = NotificationAccess.AUTHORIZED,
    notices: List<AppNotification> = TestNotifications,
    interactions: List<AppNotification> = TestInteractionNotifications,
    badgeState: NotificationBadgeState = TestNotificationBadgeState,
) = PreviewHost {
    NotificationCenterScreen(
        pages = rememberPreviewPages(notices, interactions),
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
    badgeState = ReadBadgeState,
)

@Composable
@Preview
private fun PreviewNotificationCenterNoticeEmpty() = PreviewNotificationCenter(
    NotificationGroup.NOTICE,
    notices = emptyList(),
    badgeState = ReadBadgeState,
)

@Composable
@Preview
private fun PreviewNotificationCenterInteractionEmpty() = PreviewNotificationCenter(
    NotificationGroup.INTERACTION,
    interactions = emptyList(),
    badgeState = ReadBadgeState,
)

/**
 * 宽屏双栏, 尚未选择通知.
 */
@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
private fun PreviewNotificationCenterTwoPane() = PreviewNotificationCenter(
    NotificationGroup.NOTICE,
    badgeState = ReadBadgeState,
)

@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
private fun PreviewNotificationCenterTwoPaneEmpty() = PreviewNotificationCenter(
    NotificationGroup.NOTICE,
    notices = emptyList(),
    badgeState = ReadBadgeState,
)

@Composable
@Preview
private fun PreviewNotificationNotFound() = PreviewHost {
    NotificationDetailScreen(
        state = DetailLoadState.NotFound,
        actions = NotificationActions.Noop,
        onRetry = {},
    )
}

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

/**
 * 当前版本不认识类型的通知: 通用展示内容和更新提示.
 */
@Composable
@Preview
private fun PreviewGenericDetail() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(createTestGenericNotification()),
            actions = NotificationActions.Noop,
            onRetry = {},
        )
    }
}

@Composable
@Preview
private fun PreviewGenericDetailWithAvatar() = PreviewHost {
    Surface {
        NotificationDetailScreen(
            state = DetailLoadState.Loaded(TestInteractionNotifications[1]),
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
private fun PreviewAnnouncementsEmpty() = PreviewHost {
    AnnouncementsScreen(
        items = rememberTestLazyPagingItems(emptyList()),
        access = NotificationAccess.AUTHORIZED,
        readOverlay = ReadOverlay.Empty,
        onOpenAnnouncement = {},
        onRefresh = {},
        onNavigateToDetail = {},
        onLogin = {},
    )
}

/**
 * 宽屏双栏, 尚未选择公告.
 */
@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
private fun PreviewAnnouncementsTwoPane() = PreviewHost {
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
private fun PreviewAnnouncementNotFound() = PreviewHost {
    AnnouncementDetailScreen(
        state = DetailLoadState.NotFound,
        onRetry = {},
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

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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.notification_empty
import me.him188.ani.app.ui.lang.notification_unread
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NotificationCenterScreenTest {
    private class Opened {
        val details = mutableListOf<String>()
        var announcements = 0
        var settings = 0
        var login = 0
    }

    private fun ComposeUiTest.showCenter(
        vm: NotificationCenterViewModel,
        opened: Opened = Opened(),
        twoPane: Boolean = false,
    ): Opened {
        setContent {
            NotificationTestHost {
                NotificationCenterScreen(
                    vm = vm,
                    onNavigateToDetail = { opened.details += it },
                    onNavigateToAnnouncements = { opened.announcements++ },
                    onNavigateToSettings = { opened.settings++ },
                    onLogin = { opened.login++ },
                    detailActions = NotificationDetailActions.Noop,
                    navigator = if (twoPane) rememberTwoPaneNavigator() else rememberSinglePaneNavigator(),
                )
            }
        }
        return opened
    }

    private val newerNotification = createTestSystemNotification(
        id = "system-new",
        read = false,
        createdAtMillis = TestNotificationNowMillis - 60_000,
        title = "新的系统消息",
    )

    private fun ComposeUiTest.waitForRow(id: String) {
        waitUntil { onAllNodesWithTag(NotificationTestTags.item(id)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.row(id: String) =
        onNodeWithTag(NotificationTestTags.CENTER_LIST).performScrollToNode(hasTestTag(NotificationTestTags.item(id)))
            .let { onNodeWithTag(NotificationTestTags.item(id)) }

    private fun ComposeUiTest.rowTexts(id: String): List<String> {
        row(id)
        return onNodeWithTag(NotificationTestTags.item(id)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun ComposeUiTest.hasUnreadDot(id: String): Boolean {
        row(id)
        return onAllNodesWithTag(NotificationTestTags.UNREAD_DOT, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { node ->
                hasAnyAncestor(hasTestTag(NotificationTestTags.item(id))).matches(node)
            }
    }

    @Test
    fun `rows follow the text rules`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = NotificationCenterViewModel(notificationTestKoin())
            showCenter(vm)
            waitForRow("report-1")

            // 公告入口显示最新公告的标题
            onNodeWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).assertIsDisplayed()
                .assertTextContains(TestAnnouncements.first().title)

            assertEquals(listOf("你举报的评论已处理", "2 小时前", "葬送的芙莉莲 第 12 话"), rowTexts("report-1"))
            assertEquals(listOf("新设备登录", "昨天", "macOS 扫码登录"), rowTexts("security-1"))
            assertEquals(listOf("Bangumi 授权已过期", "10 月 11 日", "重新授权后才能继续同步收藏"), rowTexts("security-2"))
            assertEquals(listOf("你举报的评论已处理", "9 月 23 日", "孤独摇滚！ 第 5 话"), rowTexts("report-2"))
            assertEquals(listOf("登录邮箱已更换", "9 月 18 日", "a***@example.com"), rowTexts("security-3"))
            assertEquals(
                listOf("欢迎加入 Animeko", "9 月 3 日", "绑定 Bangumi 账号后可同步追番进度与收藏。"),
                rowTexts("system-1"),
            )

            for (notification in TestNotifications) {
                val texts = rowTexts(notification.id)
                assertTrue(texts.none { it.contains("·") }, "Row ${notification.id} must not join parts with ·: $texts")
                assertTrue(texts.none { it.contains("最后一集") }, "Row ${notification.id} must not quote the comment")
            }

        }
    }

    @Test
    fun `unread rows have a dot and read rows do not`() = runAniComposeUiTest {
        val unreadText = runBlocking { getString(Lang.notification_unread) }
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("report-1")

        for (notification in TestNotifications) {
            assertEquals(!notification.read, hasUnreadDot(notification.id), notification.id)
            val stateDescription = onNodeWithTag(NotificationTestTags.item(notification.id)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.StateDescription)
            assertEquals(if (notification.read) null else unreadText, stateDescription, notification.id)
        }
    }

    @Test
    fun `clicking an unread row marks it read and opens the detail`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val badgeManager = FakeNotificationBadgeManager()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        val opened = showCenter(vm)
        waitForRow("report-1")
        val refreshesBefore = badgeManager.refreshRequests.get()

        row("report-1").performClick()

        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("report-1")), repository.markedRead.toList())
        assertEquals(listOf("report-1"), opened.details)
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitUntil { !hasUnreadDot("report-1") }

        // 已读的通知不再请求标记
        row("report-2").performClick()
        waitForIdle()
        assertEquals(listOf("report-1", "report-2"), opened.details)
        assertEquals(listOf(listOf("report-1")), repository.markedRead.toList())
    }

    @Test
    fun `two pane layout shows the detail beside the list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        val opened = showCenter(vm, twoPane = true)
        waitForRow("report-1")
        onNodeWithTag(NotificationTestTags.DETAIL_PLACEHOLDER).assertIsDisplayed()

        row("report-1").performClick()

        waitUntil { onAllNodesWithTag(NotificationTestTags.DETAIL).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(NotificationTestTags.REPORT_TARGET).assertIsDisplayed()
        assertTrue(opened.details.isEmpty())
        waitUntil { repository.markedRead.isNotEmpty() }
    }

    @Test
    fun `mark all read uses the newest loaded item and clears unread dots`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val badgeManager = FakeNotificationBadgeManager()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")
        val refreshesBefore = badgeManager.refreshRequests.get()

        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled().performClick()

        waitUntil { repository.markAllCalls.isNotEmpty() }
        val newest = TestNotifications.maxOf { it.createdAtMillis }
        assertEquals(listOf(NotificationGroup.NOTICE to newest), repository.markAllCalls.toList())
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitUntil { TestNotifications.none { hasUnreadDot(it.id) } }
    }

    @Test
    fun `mark all read reloads the list so newer notifications appear`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")
        val loadsBefore = repository.notificationLoads.get()

        // 列表加载之后到达的通知
        repository.notifications = listOf(newerNotification) + TestNotifications
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()

        waitUntil { repository.markAllCalls.isNotEmpty() }
        // 只标记已加载的通知
        val newestLoaded = TestNotifications.maxOf { it.createdAtMillis }
        assertEquals(listOf(NotificationGroup.NOTICE to newestLoaded), repository.markAllCalls.toList())
        waitForRow(newerNotification.id)
        assertTrue(repository.notificationLoads.get() > loadsBefore)
        assertTrue(hasUnreadDot(newerNotification.id))
        assertFalse(hasUnreadDot("report-1"))
    }

    @Test
    fun `a rising unread count reloads the list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications.map { it.copy(read = true) })
        val badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCount = 0))
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        repository.notifications = listOf(newerNotification) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(unreadCount = 1)

        waitForRow(newerNotification.id)
        assertTrue(hasUnreadDot(newerNotification.id))
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled()
    }

    @Test
    fun `local read marks are dropped when the account changes`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val sessionStateProvider = FakeSessionStateProvider()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, sessionStateProvider = sessionStateProvider))
        showCenter(vm)
        waitForRow("report-1")
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        waitUntil { TestNotifications.none { hasUnreadDot(it.id) } }

        // 退出登录, 再登录另一个账号. 服务端仍认为这些通知未读
        sessionStateProvider.stateFlow.value = SessionState.Invalid(InvalidSessionReason.NO_TOKEN)
        waitUntil { vm.readOverlay.value == ReadOverlay.Empty }
        sessionStateProvider.stateFlow.value = SessionState.Valid(bangumiConnected = false)
        waitForRow("report-1")
        waitUntil { hasUnreadDot("report-1") }

        row("report-1").performClick()
        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("report-1")), repository.markedRead.toList())
    }

    @Test
    fun `guest sees a login prompt and the announcement entry`() = runAniComposeUiTest {
        val signInText = runBlocking { getString(Lang.login_sign_in) }
        val badgeManager = FakeNotificationBadgeManager(
            TestNotificationBadgeState.copy(unreadCount = 0, unreadAnnouncementCount = 0, hasUnreadAnnouncements = false),
        )
        val vm = NotificationCenterViewModel(
            notificationTestKoin(badgeManager = badgeManager, sessionStateProvider = FakeSessionStateProvider.Guest),
        )
        val opened = showCenter(vm)

        waitUntil { onAllNodesWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(signInText).assertIsDisplayed().performClick()
        assertEquals(1, opened.login)
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()
        assertTrue(onAllNodesWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY_DOT, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertFalse(onAllNodesWithTag(NotificationTestTags.item("report-1")).fetchSemanticsNodes().isNotEmpty())

        onNodeWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).performClick()
        assertEquals(1, opened.announcements)
    }

    @Test
    fun `empty list shows the empty state`() = runAniComposeUiTest {
        val emptyText = runBlocking { getString(Lang.notification_empty) }
        val vm = NotificationCenterViewModel(
            notificationTestKoin(
                repository = FakeNotificationRepository(notifications = emptyList()),
                badgeManager = FakeNotificationBadgeManager(NotificationBadgeState.Empty),
            ),
        )
        showCenter(vm)

        waitUntil { onAllNodesWithTag(NotificationTestTags.EMPTY).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(emptyText).assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()
        assertTrue(onAllNodesWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `unread announcements show a dot on the announcement entry`() = runAniComposeUiTest {
        val unreadText = runBlocking { getString(Lang.notification_unread) }
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitUntil { onAllNodesWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY_DOT, useUnmergedTree = true).assertExists()
        assertEquals(
            unreadText,
            onNodeWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun `settings action opens notification settings`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        val opened = showCenter(vm)
        onNodeWithTag(NotificationTestTags.SETTINGS).performClick()
        assertEquals(1, opened.settings)
    }
}

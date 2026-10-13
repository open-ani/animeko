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
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.notification_empty
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_system_bangumi_reauthorize
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
        var accountSettings = 0
        var reauthorize = 0
        val episodes = mutableListOf<String>()

        val actions = NotificationActions.Noop.let { noop ->
            NotificationActions(
                onOpenEpisode = { subjectId, episodeId -> episodes += "$subjectId:$episodeId" },
                onOpenSubject = noop.onOpenSubject,
                onOpenPerson = noop.onOpenPerson,
                onOpenCharacter = noop.onOpenCharacter,
                onReauthorizeBangumi = { reauthorize++ },
                onOpenAccountSettings = { accountSettings++ },
            )
        }
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
                    actions = opened.actions,
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
            assertEquals(listOf("GitHub 账号已解绑", "昨天", "该账号已登录另一个 Animeko 账号"), rowTexts("security-1"))
            assertEquals(
                listOf("Bangumi 授权已过期", "10 月 11 日", "追番进度已停止同步，重新授权后自动恢复。"),
                rowTexts("bangumi-expired"),
            )
            assertEquals(listOf("你举报的评论已处理", "9 月 23 日", "孤独摇滚！ 第 5 话"), rowTexts("report-2"))
            assertEquals(listOf("账号安全提醒", "9 月 18 日"), rowTexts("security-2"))
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
    fun `row heights follow the number of lines`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("report-1")

        fun heightDp(id: String) = row(id).fetchSemanticsNode().boundsInRoot.height / density.density

        // 没有辅助文本的单行项
        assertEquals(56f, heightDp("security-2"), 0.5f)
        assertEquals(72f, heightDp("report-1"), 0.5f)
        // 有行内操作的三行项
        assertTrue(heightDp("bangumi-expired") >= 88f, "three-line row height ${heightDp("bangumi-expired")}")
    }

    @Test
    fun `row times and the announcement overline use regular weight`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = NotificationCenterViewModel(notificationTestKoin())
            showCenter(vm)
            waitForRow("report-1")

            fun textIn(ancestorTag: String, text: String) =
                onNode(hasText(text) and hasAnyAncestor(hasTestTag(ancestorTag)), useUnmergedTree = true)

            val unreadRow = NotificationTestTags.item("report-1")
            assertEquals(FontWeight.Normal, textIn(unreadRow, "2 小时前").textFontWeight())
            assertEquals(FontWeight.Medium, textIn(unreadRow, "你举报的评论已处理").textFontWeight())
            assertEquals(FontWeight.Normal, textIn(NotificationTestTags.ANNOUNCEMENT_ENTRY, "公告").textFontWeight())
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
    fun `clicking an account security row marks it read and opens account settings`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        val opened = showCenter(vm)
        waitForRow("security-1")

        row("security-1").performClick()

        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("security-1")), repository.markedRead.toList())
        assertEquals(1, opened.accountSettings)
        assertTrue(opened.details.isEmpty())
        waitUntil { !hasUnreadDot("security-1") }
    }

    @Test
    fun `two pane layout opens account settings instead of a detail for account security`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        val opened = showCenter(vm, twoPane = true)
        waitForRow("security-1")

        row("security-1").performClick()

        assertEquals(1, opened.accountSettings)
        onNodeWithTag(NotificationTestTags.DETAIL_PLACEHOLDER).assertIsDisplayed()
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL).fetchSemanticsNodes().isEmpty())
        waitUntil { repository.markedRead.isNotEmpty() }
    }

    @Test
    fun `bangumi expiry row offers reauthorization inline`() = runAniComposeUiTest {
        val reauthorizeText = runBlocking { getString(Lang.notification_system_bangumi_reauthorize) }
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        val opened = showCenter(vm)
        waitForRow("bangumi-expired")
        row("bangumi-expired")

        // 只有这一行有行内操作
        val action = onNode(
            hasTestTag(NotificationTestTags.INLINE_ACTION) and
                    hasAnyAncestor(hasTestTag(NotificationTestTags.item("bangumi-expired"))),
        )
        action.assertTextContains(reauthorizeText)
        assertEquals(1, onAllNodesWithTag(NotificationTestTags.INLINE_ACTION).fetchSemanticsNodes().size)

        action.performClick()

        assertEquals(1, opened.reauthorize)
        assertTrue(opened.details.isEmpty())
        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("bangumi-expired")), repository.markedRead.toList())
        waitUntil { !hasUnreadDot("bangumi-expired") }

        // 点击行的其他位置打开详情
        row("bangumi-expired").performClick()
        assertEquals(listOf("bangumi-expired"), opened.details)
        assertEquals(1, opened.reauthorize)
    }

    @Test
    fun `rows with inline actions are top aligned`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("bangumi-expired")
        row("bangumi-expired")

        val rowBounds = onNodeWithTag(NotificationTestTags.item("bangumi-expired")).fetchSemanticsNode().boundsInRoot
        val actionBounds = onNode(hasTestTag(NotificationTestTags.INLINE_ACTION)).fetchSemanticsNode().boundsInRoot
        val scale = density.density
        // 比两行项的 72dp 高, 按钮高 40dp, 位于两行文本下方
        assertTrue(rowBounds.height / scale > 72f + 1f, "row height ${rowBounds.height / scale}")
        assertEquals(40f, actionBounds.height / scale, 1f)
        assertTrue(actionBounds.bottom <= rowBounds.bottom)
        assertTrue(actionBounds.top - rowBounds.top > 40f * scale, "action should be below the text")

        // 按钮左移内边距的宽度, 让按钮文字与辅助文本对齐
        val supportingBounds = onNode(
            hasText(runBlocking { getString(Lang.notification_system_bangumi_expired_supporting) }) and
                    hasAnyAncestor(hasTestTag(NotificationTestTags.item("bangumi-expired"))),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertEquals(supportingBounds.left / scale - 12f, actionBounds.left / scale, 1f)
    }

    @Test
    fun `switched off types are neither loaded nor marked`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val settingsRepository = createTestSettingsRepository()
        runBlocking { settingsRepository.notificationSettings.set(NotificationSettings(showReportResults = false)) }
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, settingsRepository = settingsRepository))
        showCenter(vm)
        waitForRow("security-1")

        val shownTypes = setOf(NotificationType.SYSTEM, NotificationType.ACCOUNT_SECURITY)
        assertEquals(listOf(shownTypes), repository.notificationLoadTypes.distinct())
        assertTrue(onAllNodesWithTag(NotificationTestTags.item("report-1")).fetchSemanticsNodes().isEmpty())

        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        assertEquals(shownTypes, repository.markAllCalls.single().first)
    }

    @Test
    fun `changing the settings reloads the list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val settingsRepository = createTestSettingsRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, settingsRepository = settingsRepository))
        showCenter(vm)
        waitForRow("report-1")

        runBlocking { settingsRepository.notificationSettings.set(NotificationSettings(showAccountSecurity = false)) }

        waitUntil { onAllNodesWithTag(NotificationTestTags.item("security-1")).fetchSemanticsNodes().isEmpty() }
        assertEquals(setOf(NotificationType.SYSTEM, NotificationType.REPORT_RESULT), repository.notificationLoadTypes.last())
        waitForRow("report-1")

        runBlocking { settingsRepository.notificationSettings.set(NotificationSettings.Default) }
        waitForRow("security-1")
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
    fun `two pane detail shows the report action in its top bar`() = runAniComposeUiTest {
        val goToEpisode = runBlocking { getString(Lang.notification_report_go_to_episode) }
        val vm = NotificationCenterViewModel(notificationTestKoin())
        val opened = showCenter(vm, twoPane = true)
        waitForRow("report-1")

        row("report-1").performClick()

        waitUntil { onAllNodesWithTag(NotificationTestTags.DETAIL).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_ACTION).fetchSemanticsNodes().isEmpty())
        onNodeWithTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(goToEpisode)
            .performClick()
        assertEquals(listOf("400602:1227087"), opened.episodes)
        assertTrue(opened.details.isEmpty())

        // 系统消息的操作仍在详情底部
        row("bangumi-expired").performClick()
        waitUntil { onAllNodesWithTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION).fetchSemanticsNodes().isEmpty() }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertIsDisplayed()
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
        assertEquals(listOf(NotificationGroup.NOTICE.types to newest), repository.markAllCalls.toList())
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitUntil { TestNotifications.none { hasUnreadDot(it.id) } }
    }

    @Test
    fun `mark all read reloads the list so newer notifications appear`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")
        val loadsBefore = repository.notificationLoads

        // 列表加载之后到达的通知
        repository.notifications = listOf(newerNotification) + TestNotifications
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()

        waitUntil { repository.markAllCalls.isNotEmpty() }
        // 只标记已加载的通知
        val newestLoaded = TestNotifications.maxOf { it.createdAtMillis }
        assertEquals(listOf(NotificationGroup.NOTICE.types to newestLoaded), repository.markAllCalls.toList())
        waitForRow(newerNotification.id)
        assertTrue(repository.notificationLoads > loadsBefore)
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

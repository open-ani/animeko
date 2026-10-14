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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.test.centerLeft
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.topCenter
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
import me.him188.ani.app.ui.lang.notification_group_interaction
import me.him188.ani.app.ui.lang.notification_group_notice
import me.him188.ani.app.ui.lang.notification_interaction_empty
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_system_bangumi_reauthorize
import me.him188.ani.app.ui.lang.notification_unread
import me.him188.ani.app.ui.lang.notification_unread_count
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.NOTICE))
            .performScrollToNode(hasTestTag(NotificationTestTags.item(id)))
            .let { onNodeWithTag(NotificationTestTags.item(id)) }

    private fun ComposeUiTest.tab(group: NotificationGroup) = onNodeWithTag(NotificationTestTags.tab(group))

    private fun ComposeUiTest.selectTab(vm: NotificationCenterViewModel, group: NotificationGroup) {
        tab(group).performClick()
        waitUntil { vm.selectedGroup.value == group }
        waitUntil { onAllNodesWithTag(NotificationTestTags.centerList(group)).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }

    private fun ComposeUiTest.hasNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) =
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.boundsDp(tag: String): Rect =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.let { bounds ->
            Rect(
                bounds.left / density.density,
                bounds.top / density.density,
                bounds.right / density.density,
                bounds.bottom / density.density,
            )
        }

    private fun ComposeUiTest.noticeBadge() = onNode(
        hasTestTag(NotificationTestTags.TAB_BADGE) and hasAnyAncestor(hasTestTag(NotificationTestTags.tab(NotificationGroup.NOTICE))),
        useUnmergedTree = true,
    )

    private fun ComposeUiTest.tabBadgeCount() =
        onAllNodesWithTag(NotificationTestTags.TAB_BADGE, useUnmergedTree = true).fetchSemanticsNodes().size

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
        waitUntil { vm.readOverlays.value.values.all { it == ReadOverlay.Empty } }
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

    @Test
    fun `tabs show interaction then notice with notice selected`() = runAniComposeUiTest {
        val interactionLabel = runBlocking { getString(Lang.notification_group_interaction) }
        val noticeLabel = runBlocking { getString(Lang.notification_group_notice) }
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("report-1")

        assertEquals(NotificationGroup.NOTICE, vm.selectedGroup.value)
        tab(NotificationGroup.INTERACTION).assertIsDisplayed().assertTextEquals(interactionLabel).assertIsNotSelected()
        tab(NotificationGroup.NOTICE).assertIsDisplayed().assertTextEquals(noticeLabel).assertIsSelected()
        val interactionBounds = boundsDp(NotificationTestTags.tab(NotificationGroup.INTERACTION))
        val noticeBounds = boundsDp(NotificationTestTags.tab(NotificationGroup.NOTICE))
        assertTrue(interactionBounds.right <= noticeBounds.left, "$interactionBounds should be left of $noticeBounds")
        assertEquals(1, onAllNodesWithTag(NotificationTestTags.centerList(NotificationGroup.NOTICE)).fetchSemanticsNodes().size)
    }

    @Test
    fun `announcement entry and tab row sit between the top bar and the list`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("report-1")

        val entry = boundsDp(NotificationTestTags.ANNOUNCEMENT_ENTRY)
        val tabRow = boundsDp(NotificationTestTags.TAB_ROW)
        val list = boundsDp(NotificationTestTags.centerList(NotificationGroup.NOTICE))
        assertEquals(48f, tabRow.height, 0.5f)
        // 公告入口下没有分隔线, 由标签页底部的分隔线隔开
        assertEquals(entry.bottom, tabRow.top, 0.5f)
        assertEquals(tabRow.bottom, list.top, 0.5f)
        // 单栏时标签页占满宽度
        assertEquals(list.width, tabRow.width, 0.5f)
        assertEquals(tabRow.bottom, boundsDp(NotificationTestTags.item(TestNotifications.first().id)).top, 0.5f)
    }

    @Test
    fun `two pane list leaves space below the tab row`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm, twoPane = true)
        waitForRow("report-1")

        val tabRow = boundsDp(NotificationTestTags.TAB_ROW)
        assertEquals(boundsDp(NotificationTestTags.ANNOUNCEMENT_ENTRY).bottom, tabRow.top, 0.5f)
        assertEquals(tabRow.bottom + 8f, boundsDp(NotificationTestTags.item(TestNotifications.first().id)).top, 0.5f)
    }

    @Test
    fun `announcement entry and tab row stay fixed while the list scrolls`() = runAniComposeUiTest {
        val many = TestNotifications + List(30) {
            createTestSystemNotification(id = "system-extra-$it", createdAtMillis = TestNotificationNowMillis - (60L + it) * 86_400_000L)
        }
        val vm = NotificationCenterViewModel(notificationTestKoin(FakeNotificationRepository(notifications = many)))
        showCenter(vm)
        waitForRow("report-1")
        val entryBefore = boundsDp(NotificationTestTags.ANNOUNCEMENT_ENTRY)
        val tabRowBefore = boundsDp(NotificationTestTags.TAB_ROW)

        row("system-extra-29")
        waitForIdle()

        assertTrue(onAllNodesWithTag(NotificationTestTags.item("report-1")).fetchSemanticsNodes().isEmpty())
        assertEquals(entryBefore, boundsDp(NotificationTestTags.ANNOUNCEMENT_ENTRY))
        assertEquals(tabRowBefore, boundsDp(NotificationTestTags.TAB_ROW))
        onNodeWithTag(NotificationTestTags.ANNOUNCEMENT_ENTRY).assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.TAB_ROW).assertIsDisplayed()
    }

    @Test
    fun `notice tab badge shows the unread count`() = runAniComposeUiTest {
        val noticeLabel = runBlocking { getString(Lang.notification_group_notice) }
        val threeUnread = runBlocking { getString(Lang.notification_unread_count, 3) }
        val manyUnread = runBlocking { getString(Lang.notification_unread_count, 120) }
        val badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCount = 3))
        val vm = NotificationCenterViewModel(notificationTestKoin(badgeManager = badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        // 读屏读出标签和未读数, 而不是徽标上的数字
        noticeBadge().assertContentDescriptionEquals(threeUnread)
        tab(NotificationGroup.NOTICE).assertTextEquals(noticeLabel).assertContentDescriptionEquals(threeUnread)
        // 互动没有未读数
        assertEquals(1, tabBadgeCount())
        assertNull(
            tab(NotificationGroup.INTERACTION).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )

        val badge = noticeBadge().fetchSemanticsNode().boundsInRoot
        val label = onNode(
            hasText(noticeLabel) and hasAnyAncestor(hasTestTag(NotificationTestTags.tab(NotificationGroup.NOTICE))),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertEquals(16f, badge.height / density.density, 0.5f)
        assertEquals(6f, (badge.left - label.right) / density.density, 0.5f)

        badgeManager.state.value = badgeManager.state.value.copy(unreadCount = 0)
        waitUntil { tabBadgeCount() == 0 }
        tab(NotificationGroup.NOTICE).assertTextEquals(noticeLabel)
        assertNull(tab(NotificationGroup.NOTICE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))

        badgeManager.state.value = badgeManager.state.value.copy(unreadCount = 120)
        waitUntil { tabBadgeCount() == 1 }
        noticeBadge().assertContentDescriptionEquals(manyUnread)
        assertEquals("99+", notificationBadgeText(120))
    }

    @Test
    fun `interaction tab shows its empty state and never requests interaction types`() = runAniComposeUiTest {
        val interactionEmpty = runBlocking { getString(Lang.notification_interaction_empty) }
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled()

        selectTab(vm, NotificationGroup.INTERACTION)
        onNode(
            hasText(interactionEmpty) and
                    hasAnyAncestor(hasTestTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION))),
        ).assertIsDisplayed()
        tab(NotificationGroup.INTERACTION).assertIsSelected()
        tab(NotificationGroup.NOTICE).assertIsNotSelected()
        // 互动没有可以标记的通知
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled().performClick()

        selectTab(vm, NotificationGroup.NOTICE)
        waitForRow("report-1")
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled().performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        selectTab(vm, NotificationGroup.INTERACTION)
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()

        assertEquals(listOf(NotificationGroup.NOTICE.types), repository.markAllCalls.map { it.first })
        assertTrue(repository.notificationLoadTypes.isNotEmpty())
        for (types in repository.notificationLoadTypes) {
            assertTrue(types.isNotEmpty(), "requested no types")
            assertTrue(NotificationType.SUPPORTED.containsAll(types), "requested $types")
        }
    }

    @Test
    fun `pull to refresh is disabled on the interaction tab`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val badgeManager = FakeNotificationBadgeManager()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        fun pull(group: NotificationGroup) {
            onNodeWithTag(NotificationTestTags.centerList(group)).performTouchInput {
                swipe(
                    start = topCenter + Offset(0f, 20f),
                    end = bottomCenter - Offset(0f, 20f),
                    durationMillis = 1_000,
                )
            }
            waitForIdle()
        }

        // 通知可以下拉刷新
        val refreshesBefore = badgeManager.refreshRequests.get()
        val loadsBefore = repository.notificationLoads
        pull(NotificationGroup.NOTICE)
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitUntil { repository.notificationLoads > loadsBefore }
        waitForRow("report-1")

        selectTab(vm, NotificationGroup.INTERACTION)
        val refreshes = badgeManager.refreshRequests.get()
        val loads = repository.notificationLoads
        pull(NotificationGroup.INTERACTION)
        assertEquals(refreshes, badgeManager.refreshRequests.get())
        assertEquals(loads, repository.notificationLoads)
        assertEquals(NotificationGroup.INTERACTION, vm.selectedGroup.value)
    }

    @Test
    fun `tab clicks and touch swipes keep the selected group in sync`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        showCenter(vm)
        waitForRow("report-1")

        selectTab(vm, NotificationGroup.INTERACTION)
        tab(NotificationGroup.INTERACTION).assertIsSelected()
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION)).assertIsDisplayed()

        // 鼠标拖动不翻页
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION)).performMouseInput {
            moveTo(centerRight - Offset(10f, 0f))
            press()
            moveTo(centerLeft + Offset(10f, 0f))
            release()
            // 切到触摸前必须先退出 hover
            exit()
        }
        waitForIdle()
        assertEquals(NotificationGroup.INTERACTION, vm.selectedGroup.value)

        // 触摸滑动翻页, 选中的标签跟着变
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION)).performTouchInput { swipeLeft() }
        waitUntil { vm.selectedGroup.value == NotificationGroup.NOTICE }
        waitForRow("report-1")
        waitForIdle()
        tab(NotificationGroup.NOTICE).assertIsSelected()
        tab(NotificationGroup.INTERACTION).assertIsNotSelected()
        row("report-1").assertIsDisplayed()

        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.NOTICE)).performTouchInput { swipeRight() }
        waitUntil { vm.selectedGroup.value == NotificationGroup.INTERACTION }
        waitForIdle()
        tab(NotificationGroup.INTERACTION).assertIsSelected()
    }

    @Test
    fun `an interrupted tab animation keeps the selection on the page shown`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        val opened = showCenter(vm)
        waitForRow("report-1")

        /**
         * 点击 [target] 后在动画走到一半之前按下再抬起, 拖动打断动画, 列表回弹到 [shown].
         */
        fun interruptTabAnimation(shown: NotificationGroup, target: NotificationGroup) {
            mainClock.autoAdvance = false
            tab(target).performClick()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            // 动画已经开始, 两页都在组合中
            assertTrue(hasNode(hasTestTag(NotificationTestTags.centerList(target))))
            onNodeWithTag(NotificationTestTags.centerList(shown)).performTouchInput { down(center); up() }
            mainClock.autoAdvance = true
            waitForIdle()
        }

        interruptTabAnimation(shown = NotificationGroup.NOTICE, target = NotificationGroup.INTERACTION)
        assertEquals(NotificationGroup.NOTICE, vm.selectedGroup.value)
        tab(NotificationGroup.NOTICE).assertIsSelected()
        tab(NotificationGroup.INTERACTION).assertIsNotSelected()
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.NOTICE)).assertIsDisplayed()
        assertFalse(hasNode(hasTestTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION))))
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled()

        selectTab(vm, NotificationGroup.INTERACTION)
        interruptTabAnimation(shown = NotificationGroup.INTERACTION, target = NotificationGroup.NOTICE)
        assertEquals(NotificationGroup.INTERACTION, vm.selectedGroup.value)
        tab(NotificationGroup.INTERACTION).assertIsSelected()
        tab(NotificationGroup.NOTICE).assertIsNotSelected()
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION)).assertIsDisplayed()
        assertFalse(hasNode(hasTestTag(NotificationTestTags.centerList(NotificationGroup.NOTICE))))
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()

        // 按下没有变成点击
        assertTrue(opened.details.isEmpty())
    }

    @Test
    fun `selected tab survives recreating the screen`() = runAniComposeUiTest {
        val vm = NotificationCenterViewModel(notificationTestKoin())
        var shown by mutableStateOf(true)
        setContent {
            NotificationTestHost {
                if (shown) {
                    NotificationCenterScreen(
                        vm = vm,
                        onNavigateToDetail = {},
                        onNavigateToAnnouncements = {},
                        onNavigateToSettings = {},
                        onLogin = {},
                        actions = NotificationActions.Noop,
                        navigator = rememberSinglePaneNavigator(),
                    )
                }
            }
        }
        waitForRow("report-1")
        selectTab(vm, NotificationGroup.INTERACTION)

        shown = false
        waitForIdle()
        assertFalse(hasNode(hasTestTag(NotificationTestTags.TAB_ROW)))
        shown = true
        waitUntil { hasNode(hasTestTag(NotificationTestTags.TAB_ROW)) }
        waitForIdle()

        tab(NotificationGroup.INTERACTION).assertIsSelected()
        onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION)).assertIsDisplayed()
        assertFalse(hasNode(hasTestTag(NotificationTestTags.centerList(NotificationGroup.NOTICE))))
    }

    @Test
    fun `local read marks only affect their own group`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")

        row("report-1").performClick()
        waitUntil { repository.markedRead.isNotEmpty() }
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        val newest = TestNotifications.maxOf { it.createdAtMillis }
        waitUntil { vm.readOverlays.value.getValue(NotificationGroup.NOTICE).allReadUpToMillis == newest }
        assertEquals(setOf("report-1"), vm.readOverlays.value.getValue(NotificationGroup.NOTICE).readIds)
        assertEquals(ReadOverlay.Empty, vm.readOverlays.value.getValue(NotificationGroup.INTERACTION))

        // 切换标签页不影响各自的本地标记
        selectTab(vm, NotificationGroup.INTERACTION)
        assertEquals(ReadOverlay.Empty, vm.readOverlays.value.getValue(NotificationGroup.INTERACTION))
        // 互动没有显示的类型, "全部标为已读" 什么也不做
        runBlocking { vm.markAllRead(NotificationGroup.INTERACTION, Long.MAX_VALUE) }
        assertEquals(ReadOverlay.Empty, vm.readOverlays.value.getValue(NotificationGroup.INTERACTION))
        assertEquals(1, repository.markAllCalls.size)

        selectTab(vm, NotificationGroup.NOTICE)
        waitForRow("report-1")
        assertEquals(newest, vm.readOverlays.value.getValue(NotificationGroup.NOTICE).allReadUpToMillis)
        waitUntil { TestNotifications.none { hasUnreadDot(it.id) } }
    }

    @Test
    fun `guest sees the login prompt on both tabs`() = runAniComposeUiTest {
        val signInText = runBlocking { getString(Lang.login_sign_in) }
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(
            notificationTestKoin(
                repository = repository,
                badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCount = 0)),
                sessionStateProvider = FakeSessionStateProvider.Guest,
            ),
        )
        showCenter(vm)

        fun loginIn(group: NotificationGroup) =
            onNode(hasText(signInText) and hasAnyAncestor(hasTestTag(NotificationTestTags.centerList(group))))

        waitUntil { hasNode(hasText(signInText)) }
        loginIn(NotificationGroup.NOTICE).assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.TAB_ROW).assertIsDisplayed()

        selectTab(vm, NotificationGroup.INTERACTION)
        loginIn(NotificationGroup.INTERACTION).assertIsDisplayed()
        assertFalse(hasNode(hasTestTag(NotificationTestTags.EMPTY)))
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()
        assertEquals(0, tabBadgeCount())
        assertEquals(0, repository.notificationLoads)
    }
}

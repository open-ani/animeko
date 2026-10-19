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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.notification_empty
import me.him188.ani.app.ui.lang.notification_empty_supporting
import me.him188.ani.app.ui.lang.notification_go_to_episode
import me.him188.ani.app.ui.lang.notification_group_interaction
import me.him188.ani.app.ui.lang.notification_group_notice
import me.him188.ani.app.ui.lang.notification_interaction_empty
import me.him188.ani.app.ui.lang.notification_interaction_empty_supporting
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
        var checkUpdate = 0
        val episodes = mutableListOf<String>()

        val actions = NotificationActions.Noop.let { noop ->
            NotificationActions(
                onOpenEpisode = { subjectId, episodeId -> episodes += "$subjectId:$episodeId" },
                onOpenSubject = noop.onOpenSubject,
                onOpenPerson = noop.onOpenPerson,
                onOpenCharacter = noop.onOpenCharacter,
                onReauthorizeBangumi = { reauthorize++ },
                onOpenAccountSettings = { accountSettings++ },
                onCheckUpdate = { checkUpdate++ },
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

    private fun ComposeUiTest.row(id: String, group: NotificationGroup = NotificationGroup.NOTICE) =
        onNodeWithTag(NotificationTestTags.centerList(group))
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

    private fun ComposeUiTest.rowTexts(id: String, group: NotificationGroup = NotificationGroup.NOTICE): List<String> {
        row(id, group)
        return onNodeWithTag(NotificationTestTags.item(id)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun ComposeUiTest.rowHeightDp(id: String, group: NotificationGroup = NotificationGroup.NOTICE) =
        row(id, group).fetchSemanticsNode().boundsInRoot.height / density.density

    private fun ComposeUiTest.hasNodeInRow(id: String, tag: String) =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().any { node ->
            hasAnyAncestor(hasTestTag(NotificationTestTags.item(id))).matches(node)
        }

    private fun ComposeUiTest.bannerIn(group: NotificationGroup) = onNode(
        hasTestTag(NotificationTestTags.UPDATE_BANNER) and
                hasAnyAncestor(hasTestTag(NotificationTestTags.centerList(group))),
    )

    private fun ComposeUiTest.hasBanner() = hasNode(hasTestTag(NotificationTestTags.UPDATE_BANNER))

    /**
     * 两行的互动: 没有标题上方的一行.
     */
    private val reaction = createTestGenericNotification(
        id = "reaction-1",
        rawType = "reaction",
        createdAtMillis = TestNotificationNowMillis - 4 * 60 * 60_000L,
        overline = null,
        title = "Rin 等 12 人赞了你的评论",
        body = "OP 的分镜每一帧都能当壁纸",
        icon = NotificationIcon.REACTION,
    )

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

            assertEquals(listOf("你举报的评论已被处理", "2 小时前", "葬送的芙莉莲 第 12 话"), rowTexts("report-1"))
            assertEquals(listOf("GitHub 账号已解绑", "昨天", "该账号已登录另一个 Animeko 账号"), rowTexts("security-1"))
            assertEquals(
                listOf("Bangumi 授权已过期", "10 月 11 日", "追番进度已停止同步，重新授权后自动恢复。"),
                rowTexts("bangumi-expired"),
            )
            assertEquals(listOf("你举报的评论已被处理", "9 月 23 日", "孤独摇滚！ 第 5 话"), rowTexts("report-2"))
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
            assertEquals(FontWeight.Medium, textIn(unreadRow, "你举报的评论已被处理").textFontWeight())
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

        val excluded = setOf(NotificationType.REPORT_RESULT)
        // 每个分组按自己的分类请求, 都排除关闭的类型
        waitUntil { repository.notificationLoadsOf(NotificationGroup.INTERACTION) > 0 }
        assertEquals(
            setOf(NotificationLoad(NotificationGroup.INTERACTION, excluded), NotificationLoad(NotificationGroup.NOTICE, excluded)),
            repository.notificationLoadRequests.toSet(),
        )
        assertTrue(onAllNodesWithTag(NotificationTestTags.item("report-1")).fetchSemanticsNodes().isEmpty())

        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        val call = repository.markAllCalls.single()
        assertEquals(NotificationGroup.NOTICE, call.group)
        assertEquals(excluded, call.excludeTypes)
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
        assertEquals(
            setOf(NotificationType.ACCOUNT_SECURITY),
            repository.notificationLoadRequests.last { it.group == NotificationGroup.NOTICE }.excludeTypes,
        )
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
        val goToEpisode = runBlocking { getString(Lang.notification_go_to_episode) }
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
        assertEquals(listOf(MarkAllCall(NotificationGroup.NOTICE, emptySet(), newest)), repository.markAllCalls.toList())
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
        assertEquals(
            listOf(MarkAllCall(NotificationGroup.NOTICE, emptySet(), newestLoaded)),
            repository.markAllCalls.toList(),
        )
        waitForRow(newerNotification.id)
        assertTrue(repository.notificationLoads > loadsBefore)
        assertTrue(hasUnreadDot(newerNotification.id))
        assertFalse(hasUnreadDot("report-1"))
    }

    @Test
    fun `a rising unread count reloads the list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications.map { it.copy(read = true) })
        val badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCounts = emptyMap()))
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")
        val interactionLoads = repository.notificationLoadsOf(NotificationGroup.INTERACTION)

        repository.notifications = listOf(newerNotification) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 1))

        waitForRow(newerNotification.id)
        assertTrue(hasUnreadDot(newerNotification.id))
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled()
        // 只重新加载未读数增加的标签页
        assertEquals(interactionLoads, repository.notificationLoadsOf(NotificationGroup.INTERACTION))
    }

    @Test
    fun `a rising interaction count reloads the interaction list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications.map { it.copy(read = true) })
        val badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCounts = emptyMap()))
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")
        val noticeLoads = repository.notificationLoadsOf(NotificationGroup.NOTICE)

        val reply = createTestGenericNotification()
        repository.notifications = listOf(reply) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(
            unreadCounts = mapOf(NotificationGroup.INTERACTION to 1),
        )

        selectTab(vm, NotificationGroup.INTERACTION)
        waitForRow(reply.id)
        assertEquals(noticeLoads, repository.notificationLoadsOf(NotificationGroup.NOTICE))
    }

    @Test
    fun `a reload keeps the list at the top only when it was at the top`() = runAniComposeUiTest {
        val many = TestNotifications + List(30) {
            createTestSystemNotification(id = "system-extra-$it", createdAtMillis = TestNotificationNowMillis - (60L + it) * 86_400_000L)
        }
        val repository = FakeNotificationRepository(notifications = many.map { it.copy(read = true) })
        val badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCounts = emptyMap()))
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")
        val listTop = boundsDp(NotificationTestTags.centerList(NotificationGroup.NOTICE)).top

        // 停在顶部时, 重新加载后最前面的新通知显示在顶部
        repository.notifications = listOf(newerNotification) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 1))
        waitForRow(newerNotification.id)
        waitForIdle()
        assertEquals(listTop, boundsDp(NotificationTestTags.item(newerNotification.id)).top, 0.5f)

        // 滚动到其他位置时不跳回顶部
        row("system-extra-29")
        waitForIdle()
        val bottomRow = boundsDp(NotificationTestTags.item("system-extra-29"))
        val newest = createTestSystemNotification(id = "system-newest", read = false, createdAtMillis = TestNotificationNowMillis)
        fun rowCount() = onNodeWithTag(NotificationTestTags.centerList(NotificationGroup.NOTICE)).fetchSemanticsNode()
            .config[SemanticsProperties.CollectionInfo].rowCount
        val rowsBefore = rowCount()
        repository.notifications = listOf(newest) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 2))
        waitUntil { rowCount() > rowsBefore }
        waitForIdle()
        assertEquals(bottomRow, boundsDp(NotificationTestTags.item("system-extra-29")))
        assertFalse(hasNode(hasTestTag(NotificationTestTags.item(newest.id))))
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
            TestNotificationBadgeState.copy(
                unreadCounts = emptyMap(),
                unreadAnnouncementCount = 0,
                hasUnreadAnnouncements = false,
            ),
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
        val emptySupportingText = runBlocking { getString(Lang.notification_empty_supporting) }
        val vm = NotificationCenterViewModel(
            notificationTestKoin(
                repository = FakeNotificationRepository(notifications = emptyList()),
                badgeManager = FakeNotificationBadgeManager(NotificationBadgeState.Empty),
            ),
        )
        showCenter(vm)

        waitUntil { onAllNodesWithTag(NotificationTestTags.EMPTY).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(NotificationTestTags.EMPTY).assertIsDisplayed().assertTextEquals(emptyText, emptySupportingText)
        assertEmptyStateIcon(NotificationTestTags.EMPTY)
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
        val badgeManager = FakeNotificationBadgeManager(
            TestNotificationBadgeState.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 3)),
        )
        val vm = NotificationCenterViewModel(notificationTestKoin(badgeManager = badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        // 读屏读出标签和未读数, 而不是徽标上的数字
        noticeBadge().assertContentDescriptionEquals(threeUnread)
        tab(NotificationGroup.NOTICE).assertTextEquals(noticeLabel).assertContentDescriptionEquals(threeUnread)
        // 互动没有未读通知
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

        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = emptyMap())
        waitUntil { tabBadgeCount() == 0 }
        tab(NotificationGroup.NOTICE).assertTextEquals(noticeLabel)
        assertNull(tab(NotificationGroup.NOTICE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))

        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 120))
        waitUntil { tabBadgeCount() == 1 }
        noticeBadge().assertContentDescriptionEquals(manyUnread)
        assertEquals("99+", notificationBadgeText(120))
    }

    @Test
    fun `each tab badge shows the count of its own category`() = runAniComposeUiTest {
        val twoUnread = runBlocking { getString(Lang.notification_unread_count, 2) }
        val threeUnread = runBlocking { getString(Lang.notification_unread_count, 3) }
        val badgeManager = FakeNotificationBadgeManager(
            TestNotificationBadgeState.copy(
                unreadCounts = mapOf(NotificationGroup.INTERACTION to 2, NotificationGroup.NOTICE to 3),
            ),
        )
        val vm = NotificationCenterViewModel(notificationTestKoin(badgeManager = badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        assertEquals(2, tabBadgeCount())
        tab(NotificationGroup.INTERACTION).assertContentDescriptionEquals(twoUnread)
        tab(NotificationGroup.NOTICE).assertContentDescriptionEquals(threeUnread)

        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.INTERACTION to 2))
        waitUntil { tabBadgeCount() == 1 }
        tab(NotificationGroup.INTERACTION).assertContentDescriptionEquals(twoUnread)
        assertNull(tab(NotificationGroup.NOTICE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `empty interaction tab shows its empty state`() = runAniComposeUiTest {
        val interactionEmpty = runBlocking { getString(Lang.notification_interaction_empty) }
        val interactionEmptySupporting = runBlocking { getString(Lang.notification_interaction_empty_supporting) }
        val repository = FakeNotificationRepository()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled()

        selectTab(vm, NotificationGroup.INTERACTION)
        onNode(
            hasTestTag(NotificationTestTags.EMPTY) and
                    hasAnyAncestor(hasTestTag(NotificationTestTags.centerList(NotificationGroup.INTERACTION))),
        ).assertIsDisplayed().assertTextEquals(interactionEmpty, interactionEmptySupporting)
        assertEmptyStateIcon(NotificationTestTags.EMPTY)
        tab(NotificationGroup.INTERACTION).assertIsSelected()
        tab(NotificationGroup.NOTICE).assertIsNotSelected()
        // 空列表没有可以标记的通知
        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsNotEnabled()

        // 每个分组请求自己的分类
        assertEquals(
            setOf(NotificationGroup.INTERACTION, NotificationGroup.NOTICE),
            repository.notificationLoadRequests.map { it.group }.toSet(),
        )
        assertTrue(repository.markAllCalls.isEmpty())
    }

    @Test
    fun `interaction tab lists its notifications and marks them read in its own group`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
        // 服务端还有未加载的未读互动, 所以点击已加载的唯一一条未读之后仍然可以 "全部标为已读"
        val badgeManager = FakeNotificationBadgeManager(
            TestNotificationBadgeState.copy(
                unreadCounts = mapOf(NotificationGroup.INTERACTION to 2, NotificationGroup.NOTICE to 3),
            ),
        )
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        val opened = showCenter(vm)
        waitForRow("report-1")
        // 互动的通知不出现在通知标签页
        assertFalse(hasNode(hasTestTag(NotificationTestTags.item("reply-1"))))

        selectTab(vm, NotificationGroup.INTERACTION)
        waitForRow("reply-1")
        onNodeWithTag(NotificationTestTags.item("reply-1")).performClick()
        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("reply-1")), repository.markedRead.toList())
        assertEquals(listOf("reply-1"), opened.details)
        assertEquals(setOf("reply-1"), vm.readOverlays.value.getValue(NotificationGroup.INTERACTION).readIds)
        assertEquals(ReadOverlay.Empty, vm.readOverlays.value.getValue(NotificationGroup.NOTICE))

        onNodeWithTag(NotificationTestTags.MARK_ALL_READ).assertIsEnabled().performClick()
        waitUntil { repository.markAllCalls.isNotEmpty() }
        val newest = TestInteractionNotifications.maxOf { it.createdAtMillis }
        assertEquals(
            listOf(MarkAllCall(NotificationGroup.INTERACTION, emptySet(), newest)),
            repository.markAllCalls.toList(),
        )
        assertEquals(ReadOverlay.Empty, vm.readOverlays.value.getValue(NotificationGroup.NOTICE))
    }

    @Test
    fun `both tabs can be pulled to refresh`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
        val badgeManager = FakeNotificationBadgeManager()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow("report-1")

        // 通知可以下拉刷新
        val refreshesBefore = badgeManager.refreshRequests.get()
        val loadsBefore = repository.notificationLoads
        pullToRefresh(NotificationTestTags.centerList(NotificationGroup.NOTICE))
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitUntil { repository.notificationLoads > loadsBefore }
        waitForRow("report-1")

        selectTab(vm, NotificationGroup.INTERACTION)
        waitForRow("reply-1")
        val refreshes = badgeManager.refreshRequests.get()
        val interactionLoads = repository.notificationLoadsOf(NotificationGroup.INTERACTION)
        pullToRefresh(NotificationTestTags.centerList(NotificationGroup.INTERACTION))
        waitUntil { badgeManager.refreshRequests.get() > refreshes }
        waitUntil { repository.notificationLoadsOf(NotificationGroup.INTERACTION) > interactionLoads }
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
        // "全部标为已读" 只标记自己的分组
        runBlocking { vm.markAllRead(NotificationGroup.INTERACTION, 1000) }
        assertEquals(1000, vm.readOverlays.value.getValue(NotificationGroup.INTERACTION).allReadUpToMillis)
        assertEquals(newest, vm.readOverlays.value.getValue(NotificationGroup.NOTICE).allReadUpToMillis)
        assertEquals(MarkAllCall(NotificationGroup.INTERACTION, emptySet(), 1000), repository.markAllCalls.last())

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
                badgeManager = FakeNotificationBadgeManager(TestNotificationBadgeState.copy(unreadCounts = emptyMap())),
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

    // region types without their own rendering

    @Test
    fun `unknown type rows use three lines with an overline and two lines without`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val repository = FakeNotificationRepository(
                notifications = TestNotifications + TestInteractionNotifications + reaction,
            )
            val vm = NotificationCenterViewModel(notificationTestKoin(repository))
            showCenter(vm)
            waitForRow("report-1")
            selectTab(vm, NotificationGroup.INTERACTION)
            waitForRow("reply-1")
            val group = NotificationGroup.INTERACTION

            // 条目与剧集 / 谁做了什么 / 内容预览. 时间和未读标记在第一行
            assertEquals(
                listOf("葬送的芙莉莲 第 1 话", "30 分钟前", "Bob 回复了你的评论", "同意，这一集的作画太好了"),
                rowTexts("reply-1", group),
            )
            assertEquals(88f, rowHeightDp("reply-1", group), 0.5f)
            val overline = onNode(
                hasTestTag(NotificationTestTags.ROW_OVERLINE) and hasAnyAncestor(hasTestTag(NotificationTestTags.item("reply-1"))),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            fun textBounds(text: String) = onNode(
                hasText(text) and hasAnyAncestor(hasTestTag(NotificationTestTags.item("reply-1"))),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val headline = textBounds("Bob 回复了你的评论")
            val supporting = textBounds("同意，这一集的作画太好了")
            val time = textBounds("30 分钟前")
            assertTrue(overline.bottom <= headline.top + 0.5f && headline.bottom <= supporting.top + 0.5f)
            assertTrue(time.top < headline.top, "time should be on the overline line")
            assertTrue(hasNodeInRow("reply-1", NotificationTestTags.UNREAD_DOT))
            onNode(
                hasText("Bob 回复了你的评论") and hasAnyAncestor(hasTestTag(NotificationTestTags.item("reply-1"))),
                useUnmergedTree = true,
            ).let { assertEquals(FontWeight.Medium, it.textFontWeight()) }
            // 三行项顶部对齐
            assertEquals(row("reply-1", group).fetchSemanticsNode().boundsInRoot.top + 10f * density.density, overline.top, 1f)

            // 没有标题上方的一行时与其他通知一样是两行
            assertEquals(listOf("Rin 等 12 人赞了你的评论", "4 小时前", "OP 的分镜每一帧都能当壁纸"), rowTexts("reaction-1", group))
            assertEquals(72f, rowHeightDp("reaction-1", group), 0.5f)
            assertFalse(hasNodeInRow("reaction-1", NotificationTestTags.ROW_OVERLINE))
            // 只有标题时是一行
            assertEquals(listOf("Alice 关注了你", "10 月 10 日"), rowTexts("follow-1", group))
            assertEquals(56f, rowHeightDp("follow-1", group), 0.5f)
        }
    }

    @Test
    fun `unknown type rows show the image instead of the icon`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        showCenter(vm)
        waitForRow("report-1")
        selectTab(vm, NotificationGroup.INTERACTION)
        waitForRow("mention-1")

        row("mention-1", NotificationGroup.INTERACTION)
        assertTrue(hasNodeInRow("mention-1", NotificationTestTags.LEADING_IMAGE))
        row("reply-1", NotificationGroup.INTERACTION)
        assertFalse(hasNodeInRow("reply-1", NotificationTestTags.LEADING_IMAGE))
    }

    @Test
    fun `clicking an unknown type row marks it read and shows the generic detail beside the list`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
        val vm = NotificationCenterViewModel(notificationTestKoin(repository))
        val opened = showCenter(vm, twoPane = true)
        waitForRow("report-1")
        selectTab(vm, NotificationGroup.INTERACTION)
        waitForRow("reply-1")

        row("reply-1", NotificationGroup.INTERACTION).performClick()

        waitUntil { repository.markedRead.isNotEmpty() }
        assertEquals(listOf(listOf("reply-1")), repository.markedRead.toList())
        waitUntil { hasNode(hasTestTag(NotificationTestTags.DETAIL_UPDATE_HINT)) }
        onNode(hasText("Bob 回复了你的评论") and hasAnyAncestor(hasTestTag(NotificationTestTags.DETAIL)))
            .assertIsDisplayed()
        assertTrue(opened.details.isEmpty())
        waitUntil { !hasNodeInRow("reply-1", NotificationTestTags.UNREAD_DOT) }

        // 前往按钮在详情底部, 顶栏没有操作
        assertFalse(hasNode(hasTestTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION)))
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).performClick()
        assertEquals(listOf("400602:1227087"), opened.episodes)

        onNodeWithTag(NotificationTestTags.DETAIL_CHECK_UPDATE).performClick()
        assertEquals(1, opened.checkUpdate)
    }

    @Test
    fun `update banner appears once in a tab that loaded an unknown type`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
            val vm = NotificationCenterViewModel(notificationTestKoin(repository))
            val opened = showCenter(vm)
            waitForRow("report-1")
            // 通知标签页只有认识的类型
            assertFalse(hasBanner())

            selectTab(vm, NotificationGroup.INTERACTION)
            waitForRow("reply-1")
            bannerIn(NotificationGroup.INTERACTION).assertIsDisplayed()
                .assertTextContains("部分通知需要更新版本才能完整显示")
            assertEquals(1, onAllNodesWithTag(NotificationTestTags.UPDATE_BANNER).fetchSemanticsNodes().size)
            // 在列表顶部
            val banner = bannerIn(NotificationGroup.INTERACTION).fetchSemanticsNode().boundsInRoot
            assertTrue(banner.bottom <= row("reply-1", NotificationGroup.INTERACTION).fetchSemanticsNode().boundsInRoot.top)

            onNodeWithTag(NotificationTestTags.UPDATE_BANNER_ACTION).assertTextEquals("更新").performClick()
            assertEquals(1, opened.checkUpdate)
            // 只前往更新设置, 不打开也不标记通知
            assertTrue(opened.details.isEmpty())
            assertTrue(repository.markedRead.isEmpty())
        }
    }

    @Test
    fun `known types shown from the generic content do not show the update banner`() = runAniComposeUiTest {
        // 认识的类型缺少自己的内容
        val systemWithoutPayload = createTestGenericNotification(
            id = "system-without-payload",
            read = true,
            rawType = NotificationType.SYSTEM.wireValue,
            rawCategory = NotificationGroup.NOTICE.wireValue,
            createdAtMillis = TestNotificationNowMillis - 10 * 60_000L,
            overline = null,
            title = "系统消息",
        )
        val futureNotice = createTestGenericNotification(
            id = "future-notice",
            rawType = "future_notice",
            rawCategory = NotificationGroup.NOTICE.wireValue,
            overline = null,
            title = "新的通知类型",
            icon = null,
        )
        val repository = FakeNotificationRepository(notifications = TestNotifications + systemWithoutPayload)
        val badgeManager = FakeNotificationBadgeManager()
        val vm = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))
        showCenter(vm)
        waitForRow(systemWithoutPayload.id)
        assertFalse(hasBanner())

        // 通知分类中出现客户端不认识的类型
        repository.notifications = listOf(futureNotice) + repository.notifications
        badgeManager.state.value = badgeManager.state.value.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 4))
        waitForRow(futureNotice.id)
        waitUntil { hasBanner() }
        bannerIn(NotificationGroup.NOTICE).assertIsDisplayed()
    }

    // endregion
}

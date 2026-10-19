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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_detail_placeholder
import me.him188.ani.app.ui.lang.notification_announcement_not_found
import me.him188.ani.app.ui.lang.notification_announcements_empty
import me.him188.ani.app.ui.lang.notification_announcements_empty_supporting
import me.him188.ani.app.ui.lang.notification_detail_placeholder
import me.him188.ani.app.ui.lang.notification_not_found
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 列表为空、宽屏中尚未选择条目和条目不存在时的提示.
 */
class NotificationEmptyStateTest {
    private companion object {
        const val TOLERANCE_DP = 1f

        /**
         * 列表底部的内边距. 列表为空时的提示在除去内边距的可见区域中居中, 所以比列表的中心最多高出它的一半.
         */
        const val LIST_BOTTOM_PADDING_DP = 16f
    }

    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showCenter(vm: NotificationCenterViewModel, twoPane: Boolean = false) {
        setContent {
            NotificationTestHost {
                NotificationCenterScreen(
                    vm = vm,
                    onNavigateToDetail = {},
                    onNavigateToAnnouncements = {},
                    onNavigateToSettings = {},
                    onLogin = {},
                    actions = NotificationActions.Noop,
                    navigator = if (twoPane) rememberTwoPaneNavigator() else rememberSinglePaneNavigator(),
                )
            }
        }
    }

    private fun ComposeUiTest.showAnnouncements(vm: AnnouncementsViewModel, twoPane: Boolean = false) {
        setContent {
            NotificationTestHost {
                AnnouncementsScreen(
                    vm = vm,
                    onNavigateToDetail = {},
                    onLogin = {},
                    navigator = if (twoPane) rememberTwoPaneNavigator() else rememberSinglePaneNavigator(),
                )
            }
        }
    }

    private fun emptyCenterViewModel(
        repository: FakeNotificationRepository = FakeNotificationRepository(notifications = emptyList()),
        badgeManager: FakeNotificationBadgeManager = FakeNotificationBadgeManager(NotificationBadgeState.Empty),
    ) = NotificationCenterViewModel(notificationTestKoin(repository, badgeManager))

    private fun ComposeUiTest.hasNode(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil { hasNode(tag) }
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getBoundsInRoot()

    /**
     * [tag] 节点的边界, 不被列表等父节点裁剪.
     */
    private fun ComposeUiTest.unclippedBounds(tag: String): DpRect {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val position = node.positionInRoot
        return with(density) {
            DpRect(
                left = position.x.toDp(),
                top = position.y.toDp(),
                right = (position.x + node.size.width).toDp(),
                bottom = (position.y + node.size.height).toDp(),
            )
        }
    }

    private val DpRect.centerX get() = ((left + right) / 2).value
    private val DpRect.centerY get() = ((top + bottom) / 2).value

    /**
     * [stateTag] 的提示在 [areaTag] 中水平和垂直居中.
     */
    private fun ComposeUiTest.assertCenteredIn(stateTag: String, areaTag: String) {
        val area = bounds(areaTag)
        val state = bounds(stateTag)
        assertEquals(area.centerX, state.centerX, TOLERANCE_DP, "horizontal center of $state in $area")
        assertEquals(area.centerY, state.centerY, TOLERANCE_DP, "vertical center of $state in $area")
    }

    /**
     * [stateTag] 的提示在 [listTag] 的列表中水平居中, 垂直方向位于除去列表内边距的可见区域的中央.
     */
    private fun ComposeUiTest.assertCenteredInList(stateTag: String, listTag: String) {
        val list = bounds(listTag)
        val state = bounds(stateTag)
        assertEquals(list.centerX, state.centerX, TOLERANCE_DP, "horizontal center of $state in $list")
        val raise = list.centerY - state.centerY
        assertTrue(
            raise in -TOLERANCE_DP..(LIST_BOTTOM_PADDING_DP / 2 + TOLERANCE_DP),
            "$state should be vertically centered in $list, but is ${raise}dp above its center",
        )
    }

    // region list

    @Test
    fun `empty announcement list shows an icon a title and a supporting text`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = emptyList())))
        showAnnouncements(vm)
        waitForTag(AnnouncementTestTags.EMPTY)

        onNodeWithTag(AnnouncementTestTags.EMPTY).assertTextEquals(
            string(Lang.notification_announcements_empty),
            string(Lang.notification_announcements_empty_supporting),
        )
        assertEmptyStateIcon(AnnouncementTestTags.EMPTY)
    }

    @Test
    fun `empty notice tab is centered in the list`() = runAniComposeUiTest {
        showCenter(emptyCenterViewModel())
        waitForTag(NotificationTestTags.EMPTY)
        assertCenteredInList(NotificationTestTags.EMPTY, NotificationTestTags.centerList(NotificationGroup.NOTICE))
    }

    @Test
    fun `empty notice tab is centered in the list of the two pane layout`() = runAniComposeUiTest {
        showCenter(emptyCenterViewModel(), twoPane = true)
        waitForTag(NotificationTestTags.EMPTY)
        assertCenteredInList(NotificationTestTags.EMPTY, NotificationTestTags.centerList(NotificationGroup.NOTICE))
    }

    @Test
    fun `empty announcement list is centered in the list`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = emptyList())))
        showAnnouncements(vm)
        waitForTag(AnnouncementTestTags.EMPTY)
        assertCenteredInList(AnnouncementTestTags.EMPTY, AnnouncementTestTags.LIST)
    }

    @Test
    fun `empty state taller than the list starts at the top and scrolls`() = runAniComposeUiTest {
        setContent {
            NotificationTestHost {
                Box(Modifier.size(360.dp, 200.dp)) {
                    AnnouncementsScreen(
                        items = rememberTestLazyPagingItems(emptyList()),
                        access = NotificationAccess.AUTHORIZED,
                        readOverlay = ReadOverlay.Empty,
                        onOpenAnnouncement = {},
                        onRefresh = {},
                        onNavigateToDetail = {},
                        onLogin = {},
                        navigator = rememberSinglePaneNavigator(),
                    )
                }
            }
        }
        waitForTag(AnnouncementTestTags.EMPTY)
        val list = bounds(AnnouncementTestTags.LIST)
        val before = unclippedBounds(AnnouncementTestTags.EMPTY)
        // 提示完整显示, 不被压扁, 从列表顶部开始
        assertTrue(before.bottom - before.top > list.bottom - list.top, "$before should be taller than $list")
        assertEquals(list.top.value, before.top.value, TOLERANCE_DP, "top of $before in $list")

        onNodeWithTag(AnnouncementTestTags.LIST).performTouchInput { swipeUp() }
        waitForIdle()
        val after = unclippedBounds(AnnouncementTestTags.EMPTY)
        assertTrue(after.bottom <= list.bottom, "$after should be scrolled into $list")
    }

    @Test
    fun `empty notice tab can be pulled to refresh`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = emptyList())
        val badgeManager = FakeNotificationBadgeManager(NotificationBadgeState.Empty)
        showCenter(emptyCenterViewModel(repository, badgeManager))
        waitForTag(NotificationTestTags.EMPTY)

        repository.notifications = listOf(createTestSystemNotification(id = "system-new", read = false))
        val loadsBefore = repository.notificationLoadsOf(NotificationGroup.NOTICE)
        val refreshesBefore = badgeManager.refreshRequests.get()
        pullToRefresh(NotificationTestTags.centerList(NotificationGroup.NOTICE))

        waitUntil { repository.notificationLoadsOf(NotificationGroup.NOTICE) > loadsBefore }
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitForTag(NotificationTestTags.item("system-new"))
        assertTrue(!hasNode(NotificationTestTags.EMPTY))
    }

    @Test
    fun `empty announcement list can be pulled to refresh`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(announcements = emptyList())
        val badgeManager = FakeNotificationBadgeManager(NotificationBadgeState.Empty)
        showAnnouncements(AnnouncementsViewModel(notificationTestKoin(repository, badgeManager)))
        waitForTag(AnnouncementTestTags.EMPTY)

        repository.announcements = TestAnnouncements
        val loadsBefore = repository.announcementLoads.get()
        val refreshesBefore = badgeManager.refreshRequests.get()
        pullToRefresh(AnnouncementTestTags.LIST)

        waitUntil { repository.announcementLoads.get() > loadsBefore }
        waitUntil { badgeManager.refreshRequests.get() > refreshesBefore }
        waitForTag(AnnouncementTestTags.item(TestAnnouncements.first().id))
        assertTrue(!hasNode(AnnouncementTestTags.EMPTY))
    }

    // endregion

    // region detail

    @Test
    fun `two pane notification placeholder is centered in the detail pane`() = runAniComposeUiTest {
        showCenter(NotificationCenterViewModel(notificationTestKoin()), twoPane = true)
        waitForTag(NotificationTestTags.item(TestNotifications.first().id))
        waitForTag(NotificationTestTags.DETAIL_PLACEHOLDER)

        onNodeWithTag(NotificationTestTags.DETAIL_PLACEHOLDER).assertTextEquals(string(Lang.notification_detail_placeholder))
        assertEmptyStateIcon(NotificationTestTags.DETAIL_PLACEHOLDER)
        assertCenteredIn(NotificationTestTags.DETAIL_PLACEHOLDER, NotificationTestTags.DETAIL_PANE)
    }

    @Test
    fun `two pane detail pane is blank beside an empty notification list`() = runAniComposeUiTest {
        showCenter(emptyCenterViewModel(), twoPane = true)
        waitForTag(NotificationTestTags.EMPTY)
        onNodeWithTag(NotificationTestTags.DETAIL_PANE).assertExists()
        onNodeWithTag(NotificationTestTags.DETAIL_PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun `two pane detail pane is blank beside an empty announcement list`() = runAniComposeUiTest {
        showAnnouncements(
            AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = emptyList()))),
            twoPane = true,
        )
        waitForTag(AnnouncementTestTags.EMPTY)
        onNodeWithTag(NotificationTestTags.DETAIL_PANE).assertExists()
        onNodeWithTag(AnnouncementTestTags.DETAIL_PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun `two pane announcement placeholder is centered in the detail pane`() = runAniComposeUiTest {
        showAnnouncements(AnnouncementsViewModel(notificationTestKoin()), twoPane = true)
        waitForTag(AnnouncementTestTags.item(TestAnnouncements.first().id))
        waitForTag(AnnouncementTestTags.DETAIL_PLACEHOLDER)

        onNodeWithTag(AnnouncementTestTags.DETAIL_PLACEHOLDER)
            .assertTextEquals(string(Lang.notification_announcement_detail_placeholder))
        assertEmptyStateIcon(AnnouncementTestTags.DETAIL_PLACEHOLDER)
        assertCenteredIn(AnnouncementTestTags.DETAIL_PLACEHOLDER, NotificationTestTags.DETAIL_PANE)
    }

    /**
     * 加载中的指示器位于顶栏下方内容区域的中央. 先显示加载中并记下指示器的位置, 再切换到 "不存在", 检查提示与它居中于同一点.
     */
    private fun ComposeUiTest.assertNotFoundCentered(
        stateTag: String,
        text: String,
        showDetail: @Composable (DetailLoadState<Nothing>) -> Unit,
    ) {
        var state by mutableStateOf<DetailLoadState<Nothing>>(DetailLoadState.Loading)
        setContent {
            NotificationTestHost {
                showDetail(state)
            }
        }
        val indicator = onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).getBoundsInRoot()

        state = DetailLoadState.NotFound
        waitForTag(stateTag)
        onNodeWithTag(stateTag).assertTextEquals(text)
        assertEmptyStateIcon(stateTag)
        val notFound = bounds(stateTag)
        assertEquals(indicator.centerX, notFound.centerX, TOLERANCE_DP, "horizontal center")
        assertEquals(indicator.centerY, notFound.centerY, TOLERANCE_DP, "vertical center")
    }

    @Test
    fun `missing notification shows a centered empty state`() = runAniComposeUiTest {
        assertNotFoundCentered(NotificationTestTags.DETAIL_NOT_FOUND, string(Lang.notification_not_found)) { state ->
            NotificationDetailScreen(state = state, actions = NotificationActions.Noop, onRetry = {})
        }
    }

    @Test
    fun `missing announcement shows a centered empty state`() = runAniComposeUiTest {
        assertNotFoundCentered(AnnouncementTestTags.DETAIL_NOT_FOUND, string(Lang.notification_announcement_not_found)) { state ->
            AnnouncementDetailScreen(state = state, onRetry = {})
        }
    }

    // endregion
}

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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_not_found
import me.him188.ani.app.ui.lang.notification_announcement_tag_community
import me.him188.ani.app.ui.lang.notification_announcement_tag_general
import me.him188.ani.app.ui.lang.notification_announcement_tag_maintenance
import me.him188.ani.app.ui.lang.notification_announcement_tag_release
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementsScreenTest {
    private val taggedAnnouncements = listOf(
        createTestAnnouncement(id = "maintenance", tag = AnnouncementTag.MAINTENANCE, pinned = true),
        createTestAnnouncement(id = "release", tag = AnnouncementTag.RELEASE),
        createTestAnnouncement(id = "community", tag = AnnouncementTag.COMMUNITY),
        createTestAnnouncement(id = "general", tag = AnnouncementTag.GENERAL),
        createTestAnnouncement(id = "unknown", tag = AnnouncementTag.UNKNOWN, rawTag = "event"),
    )

    private fun ComposeUiTest.showAnnouncements(vm: AnnouncementsViewModel, opened: MutableList<String>) {
        setContent {
            NotificationTestHost {
                AnnouncementsScreen(
                    vm = vm,
                    onNavigateToDetail = { opened += it },
                    onLogin = {},
                    navigator = rememberSinglePaneNavigator(),
                )
            }
        }
    }

    private fun ComposeUiTest.waitForItem(id: String) {
        waitUntil { onAllNodesWithTag(AnnouncementTestTags.item(id)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.nodeIn(id: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(AnnouncementTestTags.item(id))), useUnmergedTree = true)

    private fun ComposeUiTest.hasNodeIn(id: String, tag: String): Boolean =
        onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(AnnouncementTestTags.item(id))), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagText(id: String): String =
        nodeIn(id, AnnouncementTestTags.TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun ComposeUiTest.rowTexts(id: String): List<String> =
        onNodeWithTag(AnnouncementTestTags.item(id)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `pinned announcements show a pin before the tag`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)))
        showAnnouncements(vm, mutableListOf())
        waitForItem("maintenance")

        assertTrue(hasNodeIn("maintenance", AnnouncementTestTags.PIN))
        assertTrue(!hasNodeIn("release", AnnouncementTestTags.PIN))
        val pin = nodeIn("maintenance", AnnouncementTestTags.PIN).fetchSemanticsNode().boundsInRoot
        val tag = nodeIn("maintenance", AnnouncementTestTags.TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(pin.right <= tag.left, "pin $pin should be before tag $tag")
    }

    @Test
    fun `tags use localized labels and unknown tags are shown as general announcements`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)))
        showAnnouncements(vm, mutableListOf())
        waitForItem("unknown")

        val expected = runBlocking {
            mapOf(
                "maintenance" to getString(Lang.notification_announcement_tag_maintenance),
                "release" to getString(Lang.notification_announcement_tag_release),
                "community" to getString(Lang.notification_announcement_tag_community),
                "general" to getString(Lang.notification_announcement_tag_general),
                "unknown" to getString(Lang.notification_announcement_tag_general),
            )
        }
        for ((id, label) in expected) {
            assertEquals(label, tagText(id), id)
        }
    }

    @Test
    fun `tag labels in simplified chinese`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = AnnouncementsViewModel(
                notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)),
            )
            showAnnouncements(vm, mutableListOf())
            waitForItem("unknown")
            assertEquals("维护", tagText("maintenance"))
            assertEquals("版本更新", tagText("release"))
            assertEquals("社区", tagText("community"))
            assertEquals("公告", tagText("general"))
            assertEquals("公告", tagText("unknown"))
        }
    }

    @Test
    fun `rows show compact dates`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = AnnouncementsViewModel(notificationTestKoin())
            showAnnouncements(vm, mutableListOf())
            waitForItem("announcement-3")
            assertEquals(
                listOf("维护", "昨天", TestAnnouncements[0].title, TestAnnouncements[0].body),
                rowTexts("announcement-1"),
            )
            assertEquals("10 月 6 日", rowTexts("announcement-2")[1])
            assertEquals("9 月 28 日", rowTexts("announcement-3")[1])
        }
    }

    @Test
    fun `clicking an unread announcement marks it read and opens the detail`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        val opened = mutableListOf<String>()
        showAnnouncements(vm, opened)
        waitForItem("announcement-1")
        assertTrue(hasNodeIn("announcement-1", AnnouncementTestTags.UNREAD_DOT))
        assertTrue(!hasNodeIn("announcement-3", AnnouncementTestTags.UNREAD_DOT))

        onNodeWithTag(AnnouncementTestTags.item("announcement-1")).performClick()

        waitUntil { repository.markedAnnouncementsRead.isNotEmpty() }
        assertEquals(listOf(listOf("announcement-1")), repository.markedAnnouncementsRead.toList())
        assertEquals(listOf("announcement-1"), opened)
        waitUntil { !hasNodeIn("announcement-1", AnnouncementTestTags.UNREAD_DOT) }
    }

    @Test
    fun `mark all announcements read`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        showAnnouncements(vm, mutableListOf())
        waitForItem("announcement-1")

        onNodeWithTag(AnnouncementTestTags.MARK_ALL_READ).assertIsEnabled().performClick()

        waitUntil { repository.markAllAnnouncementsCalls.get() == 1 }
        assertEquals(listOf(TestAnnouncements.maxOf { it.publishedAtMillis }), repository.markAllAnnouncementsBefore.toList())
        waitUntil { TestAnnouncements.none { hasNodeIn(it.id, AnnouncementTestTags.UNREAD_DOT) } }
    }

    @Test
    fun `guests can browse but not mark announcements`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(announcements = TestAnnouncements.map { it.copy(read = null) })
        val vm = AnnouncementsViewModel(notificationTestKoin(repository, sessionStateProvider = FakeSessionStateProvider.Guest))
        val opened = mutableListOf<String>()
        showAnnouncements(vm, opened)
        waitForItem("announcement-1")

        assertTrue(!hasNodeIn("announcement-1", AnnouncementTestTags.UNREAD_DOT))
        assertTrue(onAllNodesWithTag(AnnouncementTestTags.MARK_ALL_READ).fetchSemanticsNodes().isEmpty())

        onNodeWithTag(AnnouncementTestTags.item("announcement-1")).performClick()
        waitForIdle()
        assertEquals(listOf("announcement-1"), opened)
        assertTrue(repository.markedAnnouncementsRead.isEmpty())
    }

    @Test
    fun `detail screen for a deleted announcement shows a hint without retry`() = runAniComposeUiTest {
        val notFoundText = runBlocking { getString(Lang.notification_announcement_not_found) }
        val retryText = runBlocking { getString(Lang.settings_mediasource_retry) }
        val vm = AnnouncementDetailViewModel("deleted", notificationTestKoin())
        setContent {
            NotificationTestHost {
                AnnouncementDetailScreen(vm)
            }
        }
        waitUntil { onAllNodesWithTag(AnnouncementTestTags.DETAIL_NOT_FOUND).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(notFoundText).assertIsDisplayed()
        assertTrue(onAllNodesWithText(retryText).fetchSemanticsNodes().isEmpty())
    }
}

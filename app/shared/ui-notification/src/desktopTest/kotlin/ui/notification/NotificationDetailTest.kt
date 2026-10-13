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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.notification_account_new_login_hint
import me.him188.ani.app.ui.lang.notification_account_platform
import me.him188.ani.app.ui.lang.notification_account_reauthorize
import me.him188.ani.app.ui.lang.notification_account_settings
import me.him188.ani.app.ui.lang.notification_not_found
import me.him188.ani.app.ui.lang.notification_report_go_to_character
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_report_go_to_person
import me.him188.ani.app.ui.lang.notification_report_go_to_subject
import me.him188.ani.app.ui.lang.notification_report_outcome_handled
import me.him188.ani.app.ui.lang.notification_report_outcome_removed
import me.him188.ani.app.ui.lang.notification_report_privacy_footnote
import me.him188.ani.app.ui.lang.notification_report_reason
import me.him188.ani.app.ui.lang.notification_report_snapshot_footnote
import me.him188.ani.app.ui.lang.notification_report_snapshot_title
import me.him188.ani.app.ui.lang.notification_report_target
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.settings_mediasource_copy
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDetailTest {
    private class RecordedActions {
        val calls = mutableListOf<String>()
        val actions = NotificationDetailActions(
            onOpenEpisode = { subjectId, episodeId -> calls += "episode:$subjectId:$episodeId" },
            onOpenSubject = { subjectId, placeholder: SubjectDetailPlaceholder? ->
                calls += "subject:$subjectId:${placeholder?.nameCN}"
            },
            onOpenPerson = { calls += "person:$it" },
            onOpenCharacter = { calls += "character:$it" },
            onReauthorizeBangumi = { calls += "reauthorize" },
            onOpenAccountSettings = { calls += "account-settings" },
        )
    }

    private fun ComposeUiTest.showDetail(notification: AppNotification, actions: NotificationDetailActions) {
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, actions)
            }
        }
    }

    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.assertFieldValue(fieldTag: String, value: String) {
        onNode(hasText(value) and hasAnyAncestor(hasTestTag(fieldTag)), useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.assertNoAction() {
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_ACTION).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `report result shows reason target outcome and snapshot`() = runAniComposeUiTest {
        val notification = createTestReportResult()
        val target = runBlocking {
            reportTargetText(notification.content as AppNotificationContent.ReportResult, false)
                .resolveString()
        }
        showDetail(notification, NotificationDetailActions.Noop)

        onNodeWithTag(NotificationTestTags.REPORT_REASON).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_reason)).assertIsDisplayed()
        onNodeWithText(string(Lang.comment_report_reason_spoiler)).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_target)).assertIsDisplayed()
        onNodeWithText(target).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_outcome_removed)).assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_AUTHOR, useUnmergedTree = true).assertTextEquals("星野")
        onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_TEXT, useUnmergedTree = true)
            .assertTextEquals("最后一集芙莉莲会和费伦分开")
    }

    @Test
    fun `report result with snapshot explains the snapshot and the privacy rule`() = runAniComposeUiTest {
        val privacy = string(Lang.notification_report_privacy_footnote)
        val footnote = runBlocking { getString(Lang.notification_report_snapshot_footnote, privacy) }
        showDetail(createTestReportResult(), NotificationDetailActions.Noop)
        onNodeWithText(footnote).assertExists()
    }

    @Test
    fun `report result without snapshot only states the privacy rule`() = runAniComposeUiTest {
        val privacy = string(Lang.notification_report_privacy_footnote)
        val footnote = runBlocking { getString(Lang.notification_report_snapshot_footnote, privacy) }
        showDetail(createTestReportResult(contentSnapshot = null), NotificationDetailActions.Noop)

        onNodeWithText(privacy).assertExists()
        assertTrue(onAllNodesWithText(footnote).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText(string(Lang.notification_report_snapshot_title)).fetchSemanticsNodes().isEmpty())
        assertTrue(
            onAllNodesWithTag(NotificationTestTags.REPORT_SNAPSHOT_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `report result shows handled when the comment was kept`() = runAniComposeUiTest {
        showDetail(createTestReportResult(contentRemoved = false), NotificationDetailActions.Noop)
        onNodeWithText(string(Lang.notification_report_outcome_handled)).assertIsDisplayed()
    }

    @Test
    fun `episode comment report opens the episode`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(createTestReportResult(), recorded.actions)
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_episode))
            .performClick()
        assertEquals(listOf("episode:400602:1227087"), recorded.calls)
    }

    @Test
    fun `subject review report opens the subject`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(
            createTestReportResult(targetType = CommentReportTargetType.SUBJECT_REVIEW, episodeId = null, episodeSort = null),
            recorded.actions,
        )
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_subject))
            .performClick()
        assertEquals(listOf("subject:400602:葬送的芙莉莲"), recorded.calls)
    }

    @Test
    fun `person and character reports open the person or character`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        var notification by mutableStateOf(
            createTestReportResult(
                targetType = CommentReportTargetType.PERSON_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null,
                personId = 5, targetName = "种崎敦美",
            ),
        )
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, recorded.actions)
            }
        }
        onNodeWithText("种崎敦美").assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_person))
            .performClick()

        runOnIdle {
            notification = createTestReportResult(
                targetType = CommentReportTargetType.CHARACTER_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null,
                characterId = 6, targetName = "芙莉莲",
            )
        }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_character))
            .performClick()
        assertEquals(listOf("person:5", "character:6"), recorded.calls)
    }

    @Test
    fun `report without target ids has no navigation button`() = runAniComposeUiTest {
        showDetail(
            createTestReportResult(
                targetType = CommentReportTargetType.CHARACTER_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null, characterId = null,
            ),
            NotificationDetailActions.Noop,
        )
        onNodeWithTag(NotificationTestTags.REPORT_TARGET).assertIsDisplayed()
        assertNoAction()
    }

    @Test
    fun `report with unknown target type shows a generic target and no navigation button`() = runAniComposeUiTest {
        showDetail(
            createTestReportResult(
                targetType = null,
                reason = CommentReportReason.OTHER,
                personId = 5,
                characterId = 6,
                targetName = "芙莉莲",
            ),
            NotificationDetailActions.Noop,
        )
        assertFieldValue(NotificationTestTags.REPORT_TARGET, string(Lang.notification_report_target_comment))
        assertFieldValue(NotificationTestTags.REPORT_REASON, string(Lang.comment_report_reason_other))
        assertTrue(onAllNodesWithText("芙莉莲").fetchSemanticsNodes().isEmpty())
        assertNoAction()
    }

    @Test
    fun `new login shows known platforms and omits unknown ones`() = runAniComposeUiTest {
        var notification by mutableStateOf(createTestAccountSecurity(clientPlatform = "android-tv-arm64-v8a"))
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, NotificationDetailActions.Noop)
            }
        }
        onNodeWithText(string(Lang.notification_account_platform)).assertExists()
        onNodeWithText("Android TV").assertExists()

        runOnIdle {
            notification = createTestAccountSecurity(clientPlatform = "freebsd-x86_64")
        }
        waitForIdle()
        assertTrue(onAllNodesWithText(string(Lang.notification_account_platform)).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("freebsd", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `bangumi authorization expired offers reauthorization`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(
            createTestAccountSecurity(event = AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED, loginMethod = null),
            recorded.actions,
        )
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_account_reauthorize))
            .performClick()
        assertEquals(listOf("reauthorize"), recorded.calls)
    }

    @Test
    fun `new login shows the hint and opens account settings`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(createTestAccountSecurity(event = AccountSecurityEvent.NEW_LOGIN), recorded.actions)
        onNodeWithText(string(Lang.notification_account_new_login_hint)).assertExists()
        onNodeWithText("macOS").assertExists()
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_account_settings))
            .performClick()
        assertEquals(listOf("account-settings"), recorded.calls)
    }

    @Test
    fun `system notification shows linked body and details link`() = runAniComposeUiTest {
        showDetail(createTestSystemNotification(), NotificationDetailActions.Noop)
        onNodeWithText("欢迎加入 Animeko").assertIsDisplayed()
        onNodeWithText("https://myani.org/help", substring = true).assertExists()
        onNodeWithTag(NotificationTestTags.DETAIL_LINK).assertExists()
    }

    @Test
    fun `system notification without link has no details button`() = runAniComposeUiTest {
        showDetail(createTestSystemNotification(linkUrl = null), NotificationDetailActions.Noop)
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `detail screen loads the notification by id`() = runAniComposeUiTest {
        val vm = NotificationDetailViewModel("security-2", notificationTestKoin())
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(vm, NotificationDetailActions.Noop)
            }
        }
        waitUntil { onAllNodes(hasTestTag(NotificationTestTags.DETAIL)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_account_reauthorize))
    }

    @Test
    fun `detail screen for a missing notification shows a hint without retry`() = runAniComposeUiTest {
        val vm = NotificationDetailViewModel("missing", notificationTestKoin())
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(vm, NotificationDetailActions.Noop)
            }
        }
        waitUntil { onAllNodesWithTag(NotificationTestTags.DETAIL_NOT_FOUND).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(string(Lang.notification_not_found)).assertIsDisplayed()
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_retry)).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_copy)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `not found state renders without a retry button`() = runAniComposeUiTest {
        var retries = 0
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(
                    state = DetailLoadState.NotFound,
                    actions = NotificationDetailActions.Noop,
                    onRetry = { retries++ },
                )
            }
        }
        onNodeWithTag(NotificationTestTags.DETAIL_NOT_FOUND).assertIsDisplayed()
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_retry)).fetchSemanticsNodes().isEmpty())
        assertEquals(0, retries)
    }
}

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

import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_generic
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_supporting
import me.him188.ani.app.ui.lang.notification_account_unknown
import me.him188.ani.app.ui.lang.notification_report_go_to_character
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_report_go_to_person
import me.him188.ani.app.ui.lang.notification_report_go_to_subject
import me.him188.ani.app.ui.lang.notification_report_result_row_title
import me.him188.ani.app.ui.lang.notification_report_target_character_comment
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_report_target_episode_comment
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationFormattingTest {
    private fun plain(text: String) = NotificationText.Plain(text)
    private fun episode(number: String) =
        NotificationText.Res(Lang.exploration_schedule_episode, listOf(plain(number)))

    // region episode label

    @Test
    fun `numeric episode sort is shown without zero padding`() {
        assertEquals(EpisodeSortLabel.Numbered("12"), parseEpisodeSortLabel("12"))
        assertEquals(EpisodeSortLabel.Numbered("1"), parseEpisodeSortLabel("01"))
        assertEquals(EpisodeSortLabel.Numbered("12.5"), parseEpisodeSortLabel("12.5"))
        assertEquals(EpisodeSortLabel.Numbered("0"), parseEpisodeSortLabel("0"))
        assertEquals(EpisodeSortLabel.Numbered("0.5"), parseEpisodeSortLabel("00.5"))
    }

    @Test
    fun `non numeric episode sort is shown as is`() {
        assertEquals(EpisodeSortLabel.Raw("SP1"), parseEpisodeSortLabel("SP1"))
        assertEquals(EpisodeSortLabel.Raw("OVA"), parseEpisodeSortLabel(" OVA "))
        assertEquals(plain("SP1"), episodeLabelText("SP1"))
        assertEquals(episode("3"), episodeLabelText("03"))
    }

    // endregion

    // region snapshot

    @Test
    fun `snapshot is split at the first full width colon`() {
        assertEquals(
            ReportSnapshot(author = "星野", text = "最后一集：芙莉莲"),
            parseReportSnapshot("星野：最后一集：芙莉莲"),
        )
    }

    @Test
    fun `snapshot without author`() {
        assertEquals(ReportSnapshot(author = null, text = "只有正文"), parseReportSnapshot("只有正文"))
        assertEquals(ReportSnapshot(author = null, text = "正文"), parseReportSnapshot("：正文"))
        // 半角冒号不是分隔符
        assertEquals(ReportSnapshot(author = null, text = "a:b"), parseReportSnapshot("a:b"))
    }

    // endregion

    // region report result

    @Test
    fun `report row uses chinese subject name and episode label`() {
        val text = notificationRowText(createTestReportResult().content, useOriginalTitle = false)
        assertEquals(NotificationText.Res(Lang.notification_report_result_row_title), text.headline)
        assertEquals(NotificationText.Words(listOf(plain("葬送的芙莉莲"), episode("12"))), text.supporting)
    }

    @Test
    fun `report row uses original title when preferred`() {
        val content = createTestReportResult(episodeSort = "SP1").content as AppNotificationContent.ReportResult
        assertEquals(
            NotificationText.Words(listOf(plain("葬送のフリーレン"), plain("SP1"))),
            reportTargetText(content, useOriginalTitle = true),
        )
    }

    @Test
    fun `report row falls back to the other name when preferred name is blank`() {
        val content = createTestReportResult(subjectNameCn = " ").content as AppNotificationContent.ReportResult
        assertEquals(
            NotificationText.Words(listOf(plain("葬送のフリーレン"), episode("12"))),
            reportTargetText(content, useOriginalTitle = false),
        )
    }

    @Test
    fun `subject review shows subject only`() {
        val content = createTestReportResult(
            targetType = CommentReportTargetType.SUBJECT_REVIEW,
            episodeId = null,
            episodeSort = null,
        ).content as AppNotificationContent.ReportResult
        assertEquals(plain("葬送的芙莉莲"), reportTargetText(content, useOriginalTitle = false))
    }

    @Test
    fun `episode comment without episode sort shows subject only`() {
        val content = createTestReportResult(episodeSort = null).content as AppNotificationContent.ReportResult
        assertEquals(plain("葬送的芙莉莲"), reportTargetText(content, useOriginalTitle = false))
    }

    @Test
    fun `person and character comments show target name or fall back to target type`() {
        val character = createTestReportResult(
            targetType = CommentReportTargetType.CHARACTER_COMMENT,
            subjectId = null, subjectName = null, subjectNameCn = null, episodeId = null, episodeSort = null,
            characterId = 1, targetName = "芙莉莲",
        ).content as AppNotificationContent.ReportResult
        assertEquals(plain("芙莉莲"), reportTargetText(character, useOriginalTitle = false))

        assertEquals(
            NotificationText.Res(Lang.notification_report_target_character_comment),
            reportTargetText(character.copy(targetName = null), useOriginalTitle = false),
        )
    }

    @Test
    fun `episode comment without subject falls back to target type`() {
        val content = createTestReportResult(subjectName = null, subjectNameCn = null)
            .content as AppNotificationContent.ReportResult
        assertEquals(
            NotificationText.Res(Lang.notification_report_target_episode_comment),
            reportTargetText(content, useOriginalTitle = false),
        )
    }

    @Test
    fun `report result action depends on target type and ids`() {
        val episode = createTestReportResult().content as AppNotificationContent.ReportResult
        assertEquals(ReportResultAction.OpenEpisode(400602, 1227087), reportResultAction(episode))
        assertNull(reportResultAction(episode.copy(episodeId = null)))

        val review = episode.copy(targetType = CommentReportTargetType.SUBJECT_REVIEW)
        assertEquals(ReportResultAction.OpenSubject(400602), reportResultAction(review))
        assertNull(reportResultAction(review.copy(subjectId = null)))

        val person = episode.copy(targetType = CommentReportTargetType.PERSON_COMMENT, personId = 7)
        assertEquals(ReportResultAction.OpenPerson(7), reportResultAction(person))
        assertNull(reportResultAction(person.copy(personId = null)))

        val character = episode.copy(targetType = CommentReportTargetType.CHARACTER_COMMENT, characterId = 8)
        assertEquals(ReportResultAction.OpenCharacter(8), reportResultAction(character))
        assertNull(reportResultAction(character.copy(characterId = null)))
    }

    @Test
    fun `report result actions have labels and open their targets`() {
        val calls = mutableListOf<String>()
        val actions = NotificationActions.Noop.let { noop ->
            NotificationActions(
                onOpenEpisode = { subjectId, episodeId -> calls += "episode:$subjectId:$episodeId" },
                onOpenSubject = { subjectId, placeholder -> calls += "subject:$subjectId:${placeholder?.nameCN}" },
                onOpenPerson = { calls += "person:$it" },
                onOpenCharacter = { calls += "character:$it" },
                onReauthorizeBangumi = noop.onReauthorizeBangumi,
                onOpenAccountSettings = noop.onOpenAccountSettings,
            )
        }
        val content = createTestReportResult().content as AppNotificationContent.ReportResult
        val cases = listOf(
            ReportResultAction.OpenEpisode(1, 2) to Lang.notification_report_go_to_episode,
            ReportResultAction.OpenSubject(3) to Lang.notification_report_go_to_subject,
            ReportResultAction.OpenPerson(4) to Lang.notification_report_go_to_person,
            ReportResultAction.OpenCharacter(5) to Lang.notification_report_go_to_character,
        )
        for ((action, label) in cases) {
            assertEquals(label, action.labelRes)
            actions.perform(action, content)
        }
        assertEquals(listOf("episode:1:2", "subject:3:葬送的芙莉莲", "person:4", "character:5"), calls)
    }

    @Test
    fun `unknown target type shows a generic line and no action`() {
        val content = createTestReportResult().content as AppNotificationContent.ReportResult
        val unknown = content.copy(targetType = null, personId = 7, characterId = 8, targetName = "芙莉莲")
        val generic = NotificationText.Res(Lang.notification_report_target_comment)

        assertEquals(generic, reportTargetText(unknown, useOriginalTitle = false))
        assertEquals(
            NotificationRowText(NotificationText.Res(Lang.notification_report_result_row_title), generic),
            notificationRowText(unknown, useOriginalTitle = false),
        )
        assertEquals(
            generic,
            reportTargetText(unknown.copy(subjectName = null, subjectNameCn = null), useOriginalTitle = true),
        )
        assertNull(reportResultAction(unknown))
    }

    // endregion

    // region account security

    private fun accountSecurity(
        event: AccountSecurityEvent,
        provider: String? = null,
    ): AppNotificationContent.AccountSecurity =
        createTestAccountSecurity(event = event, provider = provider).content as AppNotificationContent.AccountSecurity

    @Test
    fun `third party unbound row shows provider`() {
        val text = accountSecurityRowText(
            accountSecurity(AccountSecurityEvent.THIRD_PARTY_UNBOUND, provider = "github"),
        )
        assertEquals(
            NotificationRowText(
                NotificationText.Res(Lang.notification_account_third_party_unbound, listOf(plain("GitHub"))),
                NotificationText.Res(Lang.notification_account_third_party_unbound_supporting),
            ),
            text,
        )
        assertEquals(
            NotificationText.Res(Lang.notification_account_third_party_unbound_generic),
            accountSecurityRowText(accountSecurity(AccountSecurityEvent.THIRD_PARTY_UNBOUND)).headline,
        )
    }

    @Test
    fun `third party unbound row hides unknown provider`() {
        assertEquals(
            NotificationText.Res(Lang.notification_account_third_party_unbound_generic),
            accountSecurityRowText(
                accountSecurity(AccountSecurityEvent.THIRD_PARTY_UNBOUND, provider = "weibo"),
            ).headline,
        )
        assertNull(providerDisplayName("weibo"))
        assertNull(providerDisplayName(" "))
        assertNull(providerDisplayName(null))
        assertEquals("GitHub", providerDisplayName(" GitHub "))
        assertEquals("Bangumi", providerDisplayName("bangumi"))
    }

    @Test
    fun `unknown event row is a generic alert without supporting text`() {
        assertEquals(
            NotificationRowText(NotificationText.Res(Lang.notification_account_unknown), supporting = null),
            accountSecurityRowText(accountSecurity(AccountSecurityEvent.UNKNOWN, provider = "github")),
        )
    }

    @Test
    fun `account security opens account settings and other types open the detail`() {
        for (event in AccountSecurityEvent.entries) {
            assertEquals(NotificationOpenTarget.ACCOUNT_SETTINGS, notificationOpenTarget(accountSecurity(event)))
        }
        assertEquals(NotificationOpenTarget.DETAIL, notificationOpenTarget(createTestReportResult().content))
        assertEquals(NotificationOpenTarget.DETAIL, notificationOpenTarget(createTestSystemNotification().content))
        assertEquals(NotificationOpenTarget.DETAIL, notificationOpenTarget(createTestBangumiExpired().content))
    }

    // endregion

    // region system

    @Test
    fun `bangumi authorization expired row uses client text`() {
        val content = AppNotificationContent.System(
            title = "服务端标题",
            body = "服务端正文",
            event = SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED,
        )
        assertEquals(
            NotificationRowText(
                NotificationText.Res(Lang.notification_system_bangumi_expired),
                NotificationText.Res(Lang.notification_system_bangumi_expired_supporting),
            ),
            notificationRowText(content, useOriginalTitle = false),
        )
    }

    @Test
    fun `only bangumi authorization expired has an inline action`() {
        assertEquals(
            listOf(NotificationInlineAction.REAUTHORIZE_BANGUMI),
            notificationInlineActions(createTestBangumiExpired().content),
        )
        assertEquals(emptyList(), notificationInlineActions(createTestSystemNotification().content))
        assertEquals(emptyList(), notificationInlineActions(createTestReportResult().content))
        assertEquals(emptyList(), notificationInlineActions(createTestAccountSecurity().content))
    }

    @Test
    fun `system row uses title and first non blank line of body`() {
        val text = notificationRowText(
            AppNotificationContent.System("标题", "\n  第一行  \n第二行"),
            useOriginalTitle = false,
        )
        assertEquals(NotificationRowText(plain("标题"), plain("第一行")), text)
        assertNull(notificationRowText(AppNotificationContent.System("标题", " \n "), false).supporting)
    }

    // endregion
}

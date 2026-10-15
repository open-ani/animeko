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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Shield
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationDestination
import me.him188.ani.app.data.models.notification.NotificationDisplay
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.data.models.notification.NotificationTarget
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_generic
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_supporting
import me.him188.ani.app.ui.lang.notification_go_to_character
import me.him188.ani.app.ui.lang.notification_go_to_episode
import me.him188.ani.app.ui.lang.notification_go_to_person
import me.him188.ani.app.ui.lang.notification_go_to_subject
import me.him188.ani.app.ui.lang.notification_report_result_row_title
import me.him188.ani.app.ui.lang.notification_report_target_character_comment
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_report_target_episode_comment
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_view_details
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun reportTargetTextOf(notification: AppNotification, useOriginalTitle: Boolean): NotificationText {
        val content = notification.content as AppNotificationContent.ReportResult
        return reportTargetText(content, notification.display, useOriginalTitle)
    }

    @Test
    fun `report row uses chinese subject name and episode label`() {
        val text = notificationRowText(createTestReportResult(), useOriginalTitle = false)
        assertEquals(NotificationText.Res(Lang.notification_report_result_row_title), text.headline)
        assertEquals(NotificationText.Words(listOf(plain("葬送的芙莉莲"), episode("12"))), text.supporting)
    }

    @Test
    fun `report row uses original title when preferred`() {
        assertEquals(
            NotificationText.Words(listOf(plain("葬送のフリーレン"), plain("SP1"))),
            reportTargetTextOf(createTestReportResult(episodeSort = "SP1"), useOriginalTitle = true),
        )
    }

    @Test
    fun `report row falls back to the other name when preferred name is blank`() {
        assertEquals(
            NotificationText.Words(listOf(plain("葬送のフリーレン"), episode("12"))),
            reportTargetTextOf(createTestReportResult(subjectNameCn = " "), useOriginalTitle = false),
        )
    }

    @Test
    fun `subject review shows subject only`() {
        val notification = createTestReportResult(
            targetType = CommentReportTargetType.SUBJECT_REVIEW,
            episodeId = null,
            episodeSort = null,
        )
        assertEquals(plain("葬送的芙莉莲"), reportTargetTextOf(notification, useOriginalTitle = false))
    }

    @Test
    fun `episode comment without episode sort shows subject only`() {
        assertEquals(
            plain("葬送的芙莉莲"),
            reportTargetTextOf(createTestReportResult(episodeSort = null), useOriginalTitle = false),
        )
    }

    @Test
    fun `person and character comments show target name or fall back to target type`() {
        val character = createTestReportResult(
            targetType = CommentReportTargetType.CHARACTER_COMMENT,
            subjectId = null, subjectName = null, subjectNameCn = null, episodeId = null, episodeSort = null,
            characterId = 1, targetName = "芙莉莲",
        )
        assertEquals(plain("芙莉莲"), reportTargetTextOf(character, useOriginalTitle = false))

        assertEquals(
            NotificationText.Res(Lang.notification_report_target_character_comment),
            reportTargetText(
                (character.content as AppNotificationContent.ReportResult).copy(targetName = null),
                character.display.copy(body = "服务端正文"),
                useOriginalTitle = false,
            ),
        )
    }

    @Test
    fun `episode comment without subject falls back to target type`() {
        assertEquals(
            NotificationText.Res(Lang.notification_report_target_episode_comment),
            reportTargetTextOf(
                createTestReportResult(subjectName = null, subjectNameCn = null),
                useOriginalTitle = false,
            ),
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
                onCheckUpdate = noop.onCheckUpdate,
            )
        }
        val content = createTestReportResult().content as AppNotificationContent.ReportResult
        val cases = listOf(
            ReportResultAction.OpenEpisode(1, 2) to Lang.notification_go_to_episode,
            ReportResultAction.OpenSubject(3) to Lang.notification_go_to_subject,
            ReportResultAction.OpenPerson(4) to Lang.notification_go_to_person,
            ReportResultAction.OpenCharacter(5) to Lang.notification_go_to_character,
        )
        for ((action, label) in cases) {
            assertEquals(label, action.labelRes)
            actions.perform(action, content)
        }
        assertEquals(listOf("episode:1:2", "subject:3:葬送的芙莉莲", "person:4", "character:5"), calls)
    }

    @Test
    fun `unknown target type shows the display body and goes to the display target`() {
        val notification = createTestReportResult()
        val unknown = (notification.content as AppNotificationContent.ReportResult)
            .copy(targetType = null, personId = 7, characterId = 8, targetName = "芙莉莲")
        val display = notification.display.copy(
            body = "\n 芙莉莲的角色评论 \n第二行",
            target = NotificationTarget(characterId = 8),
        )

        assertEquals(plain("芙莉莲的角色评论"), reportTargetText(unknown, display, useOriginalTitle = false))
        assertEquals(
            NotificationRowText(NotificationText.Res(Lang.notification_report_result_row_title), plain("芙莉莲的角色评论")),
            notificationRowText(notification.copy(display = display, content = unknown), useOriginalTitle = false),
        )
        assertNull(reportResultAction(unknown))
        assertEquals(NotificationDestination.Character(8), reportResultFallbackDestination(unknown, display))
    }

    @Test
    fun `unknown target type without display body or target shows a generic line and no destination`() {
        val notification = createTestReportResult()
        val unknown = (notification.content as AppNotificationContent.ReportResult).copy(targetType = null)
        val generic = NotificationText.Res(Lang.notification_report_target_comment)

        val display = notification.display
        assertEquals(generic, reportTargetText(unknown, display.copy(body = null), useOriginalTitle = false))
        assertEquals(generic, reportTargetText(unknown, display.copy(body = " \n "), useOriginalTitle = true))
        assertNull(reportResultFallbackDestination(unknown, display.copy(target = null)))
        val ftp = display.copy(target = NotificationTarget(url = "ftp://myani.org"))
        assertNull(reportResultFallbackDestination(unknown, ftp))
    }

    @Test
    fun `known target types do not use the display destination`() {
        val notification = createTestReportResult(episodeId = null)
        val content = notification.content as AppNotificationContent.ReportResult
        val display = notification.display.copy(target = NotificationTarget(subjectId = 1))
        assertNull(reportResultAction(content))
        assertNull(reportResultFallbackDestination(content, display))
        val episode = createTestReportResult().content as AppNotificationContent.ReportResult
        assertNull(reportResultFallbackDestination(episode, display))
    }

    // endregion

    // region account security

    private fun accountSecurity(
        event: AccountSecurityEvent,
        provider: String? = null,
    ): AppNotificationContent.AccountSecurity =
        createTestAccountSecurity(event = event, provider = provider).content as AppNotificationContent.AccountSecurity

    private fun accountSecurityRow(
        event: AccountSecurityEvent,
        provider: String? = null,
        display: NotificationDisplay = NotificationDisplay(title = "服务端标题", body = "服务端正文"),
    ): NotificationRowText = accountSecurityRowText(accountSecurity(event, provider), display)

    @Test
    fun `third party unbound row shows provider`() {
        val text = accountSecurityRow(AccountSecurityEvent.THIRD_PARTY_UNBOUND, provider = "github")
        assertEquals(
            NotificationRowText(
                NotificationText.Res(Lang.notification_account_third_party_unbound, listOf(plain("GitHub"))),
                NotificationText.Res(Lang.notification_account_third_party_unbound_supporting),
            ),
            text,
        )
        assertEquals(
            NotificationText.Res(Lang.notification_account_third_party_unbound_generic),
            accountSecurityRow(AccountSecurityEvent.THIRD_PARTY_UNBOUND).headline,
        )
    }

    @Test
    fun `third party unbound row hides unknown provider`() {
        assertEquals(
            NotificationText.Res(Lang.notification_account_third_party_unbound_generic),
            accountSecurityRow(AccountSecurityEvent.THIRD_PARTY_UNBOUND, provider = "weibo").headline,
        )
        assertNull(providerDisplayName("weibo"))
        assertNull(providerDisplayName(" "))
        assertNull(providerDisplayName(null))
        assertEquals("GitHub", providerDisplayName(" GitHub "))
        assertEquals("Bangumi", providerDisplayName("bangumi"))
    }

    @Test
    fun `unknown event row uses the display content`() {
        assertEquals(
            NotificationRowText(plain("密码已修改"), plain("如果不是你本人操作，请检查账号")),
            accountSecurityRow(
                AccountSecurityEvent.UNKNOWN,
                provider = "github",
                display = NotificationDisplay(title = "密码已修改", body = "\n如果不是你本人操作，请检查账号\n第二行"),
            ),
        )
        val notification = createTestAccountSecurity(event = AccountSecurityEvent.UNKNOWN, provider = null)
        assertEquals(
            NotificationRowText(plain(notification.display.title), supporting = null),
            notificationRowText(
                notification.copy(display = notification.display.copy(body = null)),
                useOriginalTitle = false,
            ),
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
        assertEquals(NotificationOpenTarget.DETAIL, notificationOpenTarget(createTestGenericNotification().content))
    }

    // endregion

    // region system

    @Test
    fun `bangumi authorization expired row uses client text`() {
        val notification = createTestSystemNotification(
            title = "服务端标题",
            body = "服务端正文",
            event = SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED,
        )
        assertEquals(
            NotificationRowText(
                NotificationText.Res(Lang.notification_system_bangumi_expired),
                NotificationText.Res(Lang.notification_system_bangumi_expired_supporting),
            ),
            notificationRowText(notification, useOriginalTitle = false),
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
        assertEquals(emptyList(), notificationInlineActions(createTestGenericNotification().content))
    }

    @Test
    fun `system row uses title and first non blank line of body`() {
        val text = notificationRowText(
            createTestSystemNotification(title = "标题", body = "\n  第一行  \n第二行"),
            useOriginalTitle = false,
        )
        assertEquals(NotificationRowText(plain("标题"), plain("第一行")), text)
        assertNull(notificationRowText(createTestSystemNotification(title = "标题", body = " \n "), false).supporting)
    }

    // endregion

    // region types without their own rendering

    @Test
    fun `generic row uses the display overline title and first line of body`() {
        val notification = createTestGenericNotification(
            overline = " 葬送的芙莉莲 第 1 话 ",
            title = "Bob 回复了你的评论",
            body = "\n第一行\n第二行",
        )
        assertEquals(
            NotificationRowText(plain("Bob 回复了你的评论"), plain("第一行"), overline = plain("葬送的芙莉莲 第 1 话")),
            notificationRowText(notification, useOriginalTitle = false),
        )
        assertNull(notificationRowText(createTestGenericNotification(body = null), false).supporting)
        assertNull(notificationRowText(createTestGenericNotification(overline = null), false).overline)
        assertNull(notificationRowText(createTestGenericNotification(overline = " "), false).overline)
    }

    @Test
    fun `types with their own rows ignore the display overline`() {
        for (notification in listOf(createTestReportResult(), createTestAccountSecurity(), createTestBangumiExpired())) {
            val withOverline = notification.copy(display = notification.display.copy(overline = "条目"))
            assertNull(notificationRowText(withOverline, false).overline, notification.id)
        }
    }

    @Test
    fun `generic rows use the display icon and unknown icons fall back to the default`() {
        val cases = mapOf(
            NotificationIcon.REPORT to Icons.Rounded.Flag,
            NotificationIcon.SECURITY to Icons.Rounded.Shield,
            NotificationIcon.INFO to Icons.Rounded.Info,
            NotificationIcon.REPLY to Icons.AutoMirrored.Rounded.Reply,
            NotificationIcon.MENTION to Icons.Rounded.AlternateEmail,
            NotificationIcon.REACTION to Icons.Rounded.AddReaction,
            NotificationIcon.FOLLOW to Icons.Rounded.PersonAdd,
        )
        for ((icon, vector) in cases) {
            assertEquals(vector, NotificationListItemDefaults.iconFor(createTestGenericNotification(icon = icon)))
        }
        // 服务端以后增加的图标
        val unknownIcon = NotificationIcon.fromWireValue("sparkle")
        assertNull(unknownIcon)
        assertEquals(Icons.Rounded.Notifications, NotificationListItemDefaults.iconFor(createTestGenericNotification(icon = unknownIcon)))
    }

    @Test
    fun `known types keep their own icons`() {
        val reportWithOtherIcon = createTestReportResult().let {
            it.copy(display = it.display.copy(icon = NotificationIcon.FOLLOW))
        }
        assertEquals(Icons.Rounded.Flag, NotificationListItemDefaults.iconFor(reportWithOtherIcon))
        assertEquals(Icons.Rounded.Shield, NotificationListItemDefaults.iconFor(createTestAccountSecurity()))
        assertEquals(Icons.Rounded.Info, NotificationListItemDefaults.iconFor(createTestSystemNotification()))
    }

    @Test
    fun `only generic rows show the display image`() {
        val url = "https://example.com/avatar.png"
        assertEquals(url, NotificationListItemDefaults.imageUrlFor(createTestGenericNotification(imageUrl = url)))
        assertNull(NotificationListItemDefaults.imageUrlFor(createTestGenericNotification(imageUrl = null)))
        assertNull(NotificationListItemDefaults.imageUrlFor(createTestGenericNotification(imageUrl = " ")))
        for (notification in TestNotifications) {
            val withImage = notification.copy(display = notification.display.copy(imageUrl = url))
            assertNull(NotificationListItemDefaults.imageUrlFor(withImage), notification.id)
        }
    }

    @Test
    fun `only types the client does not know ask for an update`() {
        assertTrue(createTestGenericNotification(rawType = "comment_reply").isUnknownType)
        assertTrue(createTestGenericNotification(rawType = "future_type").isUnknownType)
        // 认识的类型缺少自己的内容时用通用展示内容展示, 但更新也不会改变什么
        assertFalse(createTestGenericNotification(rawType = NotificationType.REPORT_RESULT.wireValue).isUnknownType)
        for (notification in TestNotifications) {
            assertFalse(notification.isUnknownType, notification.id)
        }
    }

    @Test
    fun `destinations have labels and open their targets`() {
        val calls = mutableListOf<String>()
        val actions = NotificationActions.Noop.let { noop ->
            NotificationActions(
                onOpenEpisode = { subjectId, episodeId -> calls += "episode:$subjectId:$episodeId" },
                onOpenSubject = { subjectId, placeholder -> calls += "subject:$subjectId:$placeholder" },
                onOpenPerson = { calls += "person:$it" },
                onOpenCharacter = { calls += "character:$it" },
                onReauthorizeBangumi = noop.onReauthorizeBangumi,
                onOpenAccountSettings = noop.onOpenAccountSettings,
                onCheckUpdate = noop.onCheckUpdate,
            )
        }
        val cases = listOf(
            NotificationDestination.Episode(1, 2) to Lang.notification_go_to_episode,
            NotificationDestination.Subject(3) to Lang.notification_go_to_subject,
            NotificationDestination.Person(4) to Lang.notification_go_to_person,
            NotificationDestination.Character(5) to Lang.notification_go_to_character,
            NotificationDestination.Url("https://myani.org") to Lang.notification_view_details,
        )
        for ((destination, label) in cases) {
            assertEquals(label, destination.labelRes)
            actions.open(destination, openUrl = { calls += "url:$it" })
        }
        assertEquals(
            listOf("episode:1:2", "subject:3:null", "person:4", "character:5", "url:https://myani.org"),
            calls,
        )
    }

    // endregion
}

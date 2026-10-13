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

import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.LoginMethod
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.notification_account_bangumi_expired
import me.him188.ani.app.ui.lang.notification_account_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_account_email_bound
import me.him188.ani.app.ui.lang.notification_account_email_changed
import me.him188.ani.app.ui.lang.notification_account_email_unbound
import me.him188.ani.app.ui.lang.notification_account_new_login
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_generic
import me.him188.ani.app.ui.lang.notification_account_third_party_unbound_supporting
import me.him188.ani.app.ui.lang.notification_account_unknown
import me.him188.ani.app.ui.lang.notification_login_method_email_otp
import me.him188.ani.app.ui.lang.notification_login_method_qr_code
import me.him188.ani.app.ui.lang.notification_report_result_row_title
import me.him188.ani.app.ui.lang.notification_report_target_character_comment
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_report_target_episode_comment
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
        loginMethod: LoginMethod? = null,
        clientPlatform: String? = null,
        email: String? = null,
        provider: String? = null,
    ) = createTestAccountSecurity(
        event = event,
        loginMethod = loginMethod,
        clientPlatform = clientPlatform,
        email = email,
        provider = provider,
    ).content as AppNotificationContent.AccountSecurity

    @Test
    fun `new login row shows platform and login method`() {
        val text = accountSecurityRowText(
            accountSecurity(AccountSecurityEvent.NEW_LOGIN, LoginMethod.QR_CODE, "macos-aarch64"),
        )
        assertEquals(NotificationText.Res(Lang.notification_account_new_login), text.headline)
        assertEquals(
            NotificationText.Words(listOf(plain("macOS"), NotificationText.Res(Lang.notification_login_method_qr_code))),
            text.supporting,
        )
    }

    @Test
    fun `new login row omits unknown parts`() {
        // 只有登录方式时首字母大写
        assertEquals(
            NotificationText.Capitalized(NotificationText.Res(Lang.notification_login_method_email_otp)),
            accountSecurityRowText(
                accountSecurity(AccountSecurityEvent.NEW_LOGIN, LoginMethod.EMAIL_OTP, "freebsd-x86_64"),
            ).supporting,
        )
        assertEquals(
            plain("Android TV"),
            accountSecurityRowText(
                accountSecurity(AccountSecurityEvent.NEW_LOGIN, LoginMethod.UNKNOWN, "android-tv-arm64-v8a"),
            ).supporting,
        )
        assertNull(accountSecurityRowText(accountSecurity(AccountSecurityEvent.NEW_LOGIN)).supporting)
    }

    @Test
    fun `email rows show masked email`() {
        for ((event, title) in listOf(
            AccountSecurityEvent.EMAIL_BOUND to Lang.notification_account_email_bound,
            AccountSecurityEvent.EMAIL_CHANGED to Lang.notification_account_email_changed,
            AccountSecurityEvent.EMAIL_UNBOUND to Lang.notification_account_email_unbound,
        )) {
            val text = accountSecurityRowText(accountSecurity(event, email = "a***@example.com"))
            assertEquals(NotificationRowText(NotificationText.Res(title), plain("a***@example.com")), text)
        }
    }

    @Test
    fun `third party unbound row shows provider`() {
        val text = accountSecurityRowText(accountSecurity(AccountSecurityEvent.THIRD_PARTY_UNBOUND, provider = "github"))
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
    fun `bangumi authorization expired row`() {
        assertEquals(
            NotificationRowText(
                NotificationText.Res(Lang.notification_account_bangumi_expired),
                NotificationText.Res(Lang.notification_account_bangumi_expired_supporting),
            ),
            accountSecurityRowText(accountSecurity(AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED)),
        )
    }

    @Test
    fun `unknown event row is a generic alert without supporting text`() {
        assertEquals(
            NotificationRowText(NotificationText.Res(Lang.notification_account_unknown), supporting = null),
            accountSecurityRowText(accountSecurity(AccountSecurityEvent.UNKNOWN, email = "a***@example.com")),
        )
    }

    @Test
    fun `account security action`() {
        assertEquals(
            AccountSecurityAction.REAUTHORIZE_BANGUMI,
            accountSecurityAction(AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED),
        )
        for (event in AccountSecurityEvent.entries - AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED) {
            assertEquals(AccountSecurityAction.ACCOUNT_SETTINGS, accountSecurityAction(event))
        }
    }

    @Test
    fun `client platform display names`() {
        assertEquals("macOS", clientPlatformDisplayName("macos-aarch64"))
        assertEquals("macOS", clientPlatformDisplayName("macos-x86_64"))
        assertEquals("Windows", clientPlatformDisplayName("windows-x86_64"))
        assertEquals("Linux", clientPlatformDisplayName("linux-x86_64"))
        assertEquals("Android", clientPlatformDisplayName("android-arm64-v8a"))
        assertEquals("Android", clientPlatformDisplayName("android-armeabi-v7a"))
        assertEquals("Android TV", clientPlatformDisplayName("android-tv-arm64-v8a"))
        assertEquals("iOS", clientPlatformDisplayName("ios-aarch64"))
    }

    @Test
    fun `client platform without architecture or not normalized`() {
        assertEquals("macOS", clientPlatformDisplayName("macos"))
        assertEquals("Android TV", clientPlatformDisplayName("android-tv"))
        assertEquals("Android TV", clientPlatformDisplayName(" Android TV arm64-v8a "))
    }

    @Test
    fun `unknown client platform is omitted`() {
        assertNull(clientPlatformDisplayName("freebsd-x86_64"))
        assertNull(clientPlatformDisplayName("iosevka"))
        assertNull(clientPlatformDisplayName("windowsphone-arm"))
        assertNull(clientPlatformDisplayName(" "))
        assertNull(clientPlatformDisplayName(null))
        assertEquals(
            NotificationText.Capitalized(NotificationText.Res(Lang.notification_login_method_qr_code)),
            loginSummaryText("freebsd-x86_64", LoginMethod.QR_CODE),
        )
        assertNull(loginSummaryText("freebsd-x86_64", null))
    }

    // endregion

    @Test
    fun `system row uses title and first non blank line of body`() {
        val text = notificationRowText(
            AppNotificationContent.System("标题", "\n  第一行  \n第二行"),
            useOriginalTitle = false,
        )
        assertEquals(NotificationRowText(plain("标题"), plain("第一行")), text)
        assertNull(notificationRowText(AppNotificationContent.System("标题", " \n "), false).supporting)
    }

    @Test
    fun `absolute date time`() {
        assertEquals("2026-10-13 12:00", formatAbsoluteDateTime(TestNotificationNowMillis, TimeZone.UTC))
        assertEquals("2026-10-13 20:00", formatAbsoluteDateTime(TestNotificationNowMillis, TimeZone.of("Asia/Shanghai")))
    }
}

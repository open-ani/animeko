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

import androidx.compose.ui.test.onNodeWithText
import java.util.Locale
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.LoginMethod
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginMethodTextTest {
    private fun resolve(text: NotificationText?, locale: Locale): String? = withLocale(locale) {
        runBlocking { text?.resolveString() }
    }

    @Test
    fun `english login method is lowercase after the platform and capitalized alone`() {
        assertEquals("macOS email code sign-in", resolve(loginSummaryText("macos-aarch64", LoginMethod.EMAIL_OTP), Locale.US))
        assertEquals("Email code sign-in", resolve(loginSummaryText(null, LoginMethod.EMAIL_OTP), Locale.US))
        assertEquals("Email code sign-in", resolve(loginSummaryText("freebsd-x86_64", LoginMethod.EMAIL_OTP), Locale.US))
        assertEquals("Email code sign-in", resolve(loginMethodText(LoginMethod.EMAIL_OTP), Locale.US))
    }

    @Test
    fun `chinese login method is unchanged`() {
        assertEquals("邮箱验证码登录", resolve(loginSummaryText(null, LoginMethod.EMAIL_OTP), Locale.SIMPLIFIED_CHINESE))
        assertEquals("macOS 邮箱验证码登录", resolve(loginSummaryText("macos-aarch64", LoginMethod.EMAIL_OTP), Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `english detail shows a capitalized login method`() = withLocale(Locale.US) {
        runAniComposeUiTest {
            setContent {
                NotificationTestHost {
                    NotificationDetailContent(
                        createTestAccountSecurity(loginMethod = LoginMethod.EMAIL_OTP),
                        NotificationDetailActions.Noop,
                    )
                }
            }
            onNodeWithText("Email code sign-in").assertExists()
        }
    }
}

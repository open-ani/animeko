/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.media

import androidx.compose.runtime.Composable
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_captcha_cloudflare
import me.him188.ani.app.ui.lang.media_captcha_image
import me.him188.ani.app.ui.lang.media_captcha_required_message
import me.him188.ani.app.ui.lang.media_captcha_slider
import me.him188.ani.app.ui.lang.media_captcha_turnstile
import me.him188.ani.app.ui.lang.media_captcha_unknown
import org.jetbrains.compose.resources.stringResource

/** Shared UI copy; the source state keeps the original challenge kind. */
@Composable
fun webCaptchaRequiredMessage(kind: WebCaptchaKind): String {
    val name = stringResource(when (kind) {
        WebCaptchaKind.Image -> Lang.media_captcha_image
        WebCaptchaKind.Cloudflare -> Lang.media_captcha_cloudflare
        WebCaptchaKind.CloudflareTurnstile -> Lang.media_captcha_turnstile
        WebCaptchaKind.SliderCaptcha -> Lang.media_captcha_slider
        WebCaptchaKind.Unknown -> Lang.media_captcha_unknown
    })
    return stringResource(Lang.media_captcha_required_message, name)
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import me.him188.ani.app.domain.mediasource.web.DesktopCaptchaTopBar
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults

internal val AniDesktopCaptchaTopBar = DesktopCaptchaTopBar { pageUrl, onDismiss, onConfirm ->
    AniTopAppBar(
        title = {
            AniTopAppBarDefaults.Title(captchaTitle(pageUrl))
        },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
        actions = {
            IconButton(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "完成",
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

private fun captchaTitle(pageUrl: String): String {
    return runCatching { java.net.URI(pageUrl).host }
        .getOrNull()
        .orEmpty()
        .ifBlank { "验证码验证" }
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import me.him188.ani.app.platform.window.LocalTitleBarThemeController

@Composable
actual fun DarkCaptionButtonAppearance() {
    val titleBarController = LocalTitleBarThemeController.current ?: return
    val owner = remember { Any() }
    DisposableEffect(titleBarController, owner) {
        titleBarController.requestTheme(owner = owner, isDark = true)
        onDispose {
            titleBarController.removeTheme(owner = owner)
        }
    }
}
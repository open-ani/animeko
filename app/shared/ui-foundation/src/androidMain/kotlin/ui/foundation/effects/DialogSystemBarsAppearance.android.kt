/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Dialog 内容的 ComposeView 挂在实现了 [DialogWindowProvider] 的 DialogLayout 下, 由它拿到 Dialog 自己的窗口.
 * 窗口随 Dialog 一起销毁, 不需要恢复.
 */
@Composable
actual fun DialogSystemBarsAppearance(lightBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightBars) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = lightBars
            insetsController.isAppearanceLightNavigationBars = lightBars
        }
        onDispose {}
    }
}

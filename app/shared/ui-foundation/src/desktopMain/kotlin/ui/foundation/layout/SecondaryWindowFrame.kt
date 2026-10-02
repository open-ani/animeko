/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState

/**
 * 给桌面端二级窗口 (例如图片查看器) 套上与主窗口一致的自定义窗口外观
 * (macOS 透明标题栏, Windows 自绘标题栏). 实现在 app-desktop 里, 通过 [LocalSecondaryWindowFrame] 注入.
 *
 * 调用时 [LocalPlatformWindow] 必须已指向该二级窗口.
 */
typealias SecondaryWindowFrame = @Composable FrameWindowScope.(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) -> Unit

/** 未提供时二级窗口使用系统默认外观. */
val LocalSecondaryWindowFrame = staticCompositionLocalOf<SecondaryWindowFrame?> { null }

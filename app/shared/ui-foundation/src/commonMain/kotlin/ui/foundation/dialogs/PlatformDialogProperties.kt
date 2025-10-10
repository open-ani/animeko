/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.dialogs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties


@Suppress("FunctionName")
expect fun PlatformDialogPropertiesImpl(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,
    // Android-only:
    excludeFromSystemGesture: Boolean = true,
    // Skiko-only:
    usePlatformInsets: Boolean = true,
    decorFitsSystemWindows: Boolean = true,
    scrimColor: Color = Color.Transparent,
): DialogProperties

@Suppress("FunctionName")
fun PlatformDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,
    // Android-only:
    excludeFromSystemGesture: Boolean = true,
    clippingEnabled: Boolean = true,
    decorFitsSystemWindows: Boolean = true,
    // Skiko-only:
    usePlatformInsets: Boolean = true,
    scrimColor: Color = Color.Transparent,
): DialogProperties = PlatformDialogPropertiesImpl(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    excludeFromSystemGesture = excludeFromSystemGesture,
    usePlatformInsets = usePlatformInsets,
    decorFitsSystemWindows = decorFitsSystemWindows,
    scrimColor = scrimColor,
)

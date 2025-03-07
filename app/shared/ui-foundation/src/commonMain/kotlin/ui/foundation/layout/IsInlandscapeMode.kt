/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.DeviceOrientation


/**
 * 横屏模式. 横屏模式不一定是全屏.
 *
 * PC 一定处于横屏模式.
 *
 */
@Composable 
fun isInLandscapeMode(): Boolean = LocalPlatformWindow.current.deviceOrientation == DeviceOrientation.LANDSCAPE

@Stable
fun BoxWithConstraintsScope.showTabletUI(): Boolean {
    // https://android-developers.googleblog.com/2023/06/detecting-if-device-is-foldable-tablet.html
    // 99.96% of phones have a built-in screen with a width smaller than 600dp when in portrait, 
    // but that same screen size could be the result of a freeform/split-screen window on a tablet or desktop device.

    return maxWidth >= 600.dp && maxHeight >= 600.dp
}

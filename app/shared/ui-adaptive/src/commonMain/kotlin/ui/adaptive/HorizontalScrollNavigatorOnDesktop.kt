/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.HorizontalScrollNavigator
import me.him188.ani.app.ui.foundation.HorizontalScrollNavigatorDefaults
import me.him188.ani.app.ui.foundation.HorizontalScrollNavigatorState
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.utils.platform.Platform

@Composable
fun HorizontalScrollNavigatorOnDesktop(
    state: HorizontalScrollNavigatorState,
    modifier: Modifier = Modifier,
    scrollLeftButton: @Composable (modifier: Modifier) -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollLeftButton(it)
    },
    scrollRightButton: @Composable (modifier: Modifier) -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollRightButton(it)
    },
    content: @Composable () -> Unit
) {
    val currentPlatform = LocalPlatform.current

    if (currentPlatform is Platform.Desktop) {
        HorizontalScrollNavigator(
            state = state,
            modifier = modifier,
            scrollLeftButton = scrollLeftButton,
            scrollRightButton = scrollRightButton,
            content = content,
        )
    } else {
        content()
    }
}
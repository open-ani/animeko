/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.HorizontalScrollControlDefaults
import me.him188.ani.app.ui.foundation.HorizontalScrollControlScaffold
import me.him188.ani.app.ui.foundation.HorizontalScrollControlState
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.utils.platform.Platform

@Composable
fun HorizontalScrollControlScaffoldOnDesktop(
    state: HorizontalScrollControlState,
    modifier: Modifier = Modifier,
    scrollLeftButton: @Composable () -> Unit = {
        HorizontalScrollControlDefaults.ScrollLeftButton()
    },
    scrollRightButton: @Composable () -> Unit = {
        HorizontalScrollControlDefaults.ScrollRightButton()
    },
    content: @Composable () -> Unit
) {
    val currentPlatform = LocalPlatform.current

    if (currentPlatform is Platform.Desktop) {
        HorizontalScrollControlScaffold(
            state = state,
            modifier = modifier,
            scrollLeftButton = scrollLeftButton,
            scrollRightButton = scrollRightButton,
            content = content,
        )
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
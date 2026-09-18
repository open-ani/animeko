/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ScreenshotButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        IconButton(onClick) {
            val color = Color.White
            CompositionLocalProvider(LocalContentColor provides color) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Lock screen")
            }
        }
    }
    // 悬停模式下半屏的控制面板中不需要浮动按钮的边框背景
    if (bordered) {
        PlayerFloatingButtonBox(
            modifier = modifier,
            content = content,
        )
    } else {
        Box(modifier) { content() }
    }
}


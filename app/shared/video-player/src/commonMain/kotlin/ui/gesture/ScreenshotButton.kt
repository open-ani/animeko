/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_screenshot
import org.jetbrains.compose.resources.stringResource

/** 全屏时右侧按钮栏里的截图按钮, 与手势锁同款的悬浮样式. */
@Composable
fun ScreenshotButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerFloatingButtonBox(
        modifier = modifier,
        content = {
            IconButton(onClick) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(Lang.video_player_screenshot))
                }
            }
        },
    )
}

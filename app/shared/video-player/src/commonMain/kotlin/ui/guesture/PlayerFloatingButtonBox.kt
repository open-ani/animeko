/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.guesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken

@Composable
fun PlayerFloatingButtonBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = appColorScheme(isDark = true).background.copy(0.05f),
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, appColorScheme(isDark = false).outline.slightlyWeaken()),
    ) {
        content()
    }
}
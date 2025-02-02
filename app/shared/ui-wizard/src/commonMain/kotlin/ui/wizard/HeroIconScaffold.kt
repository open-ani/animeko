/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
object HeroIconDefaults {
    @Stable
    val iconColor: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    @Stable
    val iconSize: Dp = 96.dp

    @Composable
    fun contentPadding(): PaddingValues {
        return PaddingValues(
            top = 16.dp,
            bottom = 8.dp,
        )
    }
}
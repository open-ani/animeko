/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor

/**
 * 最小高度 48.dp. 默认水平 padding [paneHorizontalPadding].
 */
@Composable
fun NavTitleHeader(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    trailingActions: @Composable () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    Row(modifier.fillMaxWidth().padding(contentPadding)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyleContentColor(
                MaterialTheme.typography.headlineSmall,
                MaterialTheme.colorScheme.onSurface,
            ) {
                title()
            }
        }

        Row {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                navigationIcon()
            }
        }

        Spacer(Modifier.weight(1f))

        Row {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                trailingActions()
            }
        }
    }
}

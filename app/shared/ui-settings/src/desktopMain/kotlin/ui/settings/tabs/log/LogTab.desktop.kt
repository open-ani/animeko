/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.app.platform.LocalContext
import java.awt.Desktop

@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val context = LocalContext.current
    ListItem(
        {
            Text("打开日志目录")
        },
        Modifier.clickable {
            Desktop.getDesktop().open((context as DesktopContext).logsDir)
        },
        colors = listItemColors,
    )
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_about_feedback
import me.him188.ani.app.ui.lang.settings_log_share_today_log_file
import me.him188.ani.app.ui.lang.settings_log_copy_today_log_content
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogTab(
    onClickFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    loggingItems: @Composable ColumnScope.(ListItemColors) -> Unit = { PlatformLoggingItems(it) },
) {
    Column(modifier.fillMaxWidth()) {
        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_feedback)) },
            modifier = Modifier.clickable(onClick = onClickFeedback),
            leadingContent = {
                Icon(Icons.Outlined.Feedback, contentDescription = null)
            },
            colors = listItemColors,
        )

        loggingItems(listItemColors)
    }
}

@Composable
internal expect fun ColumnScope.PlatformLoggingItems(
    listItemColors: ListItemColors,
)

@Composable
@Preview
private fun PreviewLogTab() {
    ProvideCompositionLocalsForPreview {
        Surface {
            LogTab(
                onClickFeedback = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 同一组日志操作由各平台或远程会话提供实际的数据读取与分享。 */
@Composable
internal fun ColumnScope.LogFileActions(
    listItemColors: ListItemColors,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(stringResource(Lang.settings_log_share_today_log_file)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onShare),
        colors = listItemColors,
    )
    ListItem(
        headlineContent = { Text(stringResource(Lang.settings_log_copy_today_log_content)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onCopy),
        colors = listItemColors,
    )
}

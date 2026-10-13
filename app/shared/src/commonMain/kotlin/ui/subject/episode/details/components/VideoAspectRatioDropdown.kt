/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_aspect_ratio
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.renderAspectRatioMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun VideoAspectRatioDropdown(
    videoAspectRatioControllerState: VideoAspectRatioControllerState,
    showDropdown: Boolean,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = showDropdown,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            stringResource(Lang.video_player_aspect_ratio),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        VideoAspectRatioControllerState.Entries.forEach { item ->
            DropdownMenuItem(
                text = { Text(renderAspectRatioMode(item)) },
                onClick = {
                    videoAspectRatioControllerState.setMode(item)
                    onDismissRequest()
                },
                enabled = item != videoAspectRatioControllerState.currentMode,
            )
        }
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_bubble
import org.jetbrains.compose.resources.stringResource

internal const val WATCH_TOGETHER_BUBBLE_TEST_TAG = "watch_together_bubble"

@Composable
internal fun WatchTogetherBubble(
    state: WatchTogetherUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = state.room
    val waiting = state.phase == WatchTogetherPhase.JOINING ||
            room?.connection == WatchTogetherConnectionPresentation.RECONNECTING
    val containerColor = when {
        room?.connection == WatchTogetherConnectionPresentation.RECONNECTING ||
                room?.connection == WatchTogetherConnectionPresentation.DEGRADED -> MaterialTheme.colorScheme.errorContainer

        state.phase == WatchTogetherPhase.JOINING -> MaterialTheme.colorScheme.tertiaryContainer
        state.phase == WatchTogetherPhase.IN_ROOM -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        room?.connection == WatchTogetherConnectionPresentation.RECONNECTING ||
                room?.connection == WatchTogetherConnectionPresentation.DEGRADED -> MaterialTheme.colorScheme.onErrorContainer

        state.phase == WatchTogetherPhase.JOINING -> MaterialTheme.colorScheme.onTertiaryContainer
        state.phase == WatchTogetherPhase.IN_ROOM -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 56.dp)
            .testTag(WATCH_TOGETHER_BUBBLE_TEST_TAG),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (waiting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.SyncAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = room?.members?.size?.toString() ?: stringResource(Lang.watch_together_bubble),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

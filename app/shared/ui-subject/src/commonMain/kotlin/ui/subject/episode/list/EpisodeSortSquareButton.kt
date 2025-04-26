/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.FilledTonalCombinedClickButton


@Composable
internal fun EpisodeSortSquareButton(
    item: EpisodeListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EpisodeListColors = EpisodeListDefaults.colors(),
) {
    val containerColor = when {
        item.isDoneOrDropped -> colors.doneOrDroppedColor
        !item.isBroadcast -> colors.notPublishedColor // 未开播
        else -> colors.canWatchColor // 还没看
    }
    FilledTonalCombinedClickButton(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(item.sort.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}

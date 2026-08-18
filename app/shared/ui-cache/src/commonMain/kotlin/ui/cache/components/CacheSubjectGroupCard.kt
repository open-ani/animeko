/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_finished_count
import me.him188.ani.datasources.api.topic.FileSize
import org.jetbrains.compose.resources.stringResource

/**
 * 新设计的条目分组卡片: 封面 + 标题 + "15/28 已完成 · 12.4 GB" + 下载速度 + 进度条 + chevron.
 *
 * 用于全局缓存管理页的手机布局和宽屏双栏布局的列表栏.
 * 多选模式下行首显示复选框 (选中该条目的全部缓存).
 *
 * 设计稿: [Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=1655-6587)
 */
@Composable
fun CacheSubjectGroupCard(
    group: CacheGroupState,
    selected: Boolean,
    selectionMode: Boolean,
    allEntriesSelected: Boolean,
    onToggleGroupSelection: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 设计稿: 手机上点击卡片会跳转页面, 显示 chevron; 宽屏双栏在右侧展示详情, 不显示.
    showChevron: Boolean = true,
    // 设计稿: 手机上卡片通栏无圆角, 宽屏列表栏为圆角卡片.
    shape: Shape = MaterialTheme.shapes.large,
) {
    val containerColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            selectionMode && allEntriesSelected -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
    )
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = allEntriesSelected,
                    onCheckedChange = { onToggleGroupSelection() },
                )
            }

            AsyncImage(
                group.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(44.dp)
                    .height(59.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    group.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val finishedCountText = stringResource(
                        Lang.cache_management_finished_count,
                        group.finishedCount,
                        group.displayTotalCount,
                    )
                    val metaText = if (group.totalSize != FileSize.Unspecified) {
                        "$finishedCountText · ${group.totalSize}"
                    } else {
                        finishedCountText
                    }
                    Text(
                        metaText,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    group.downloadSpeedText?.let { speedText ->
                        Text(
                            speedText,
                            Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                if (group.hasUnfinished) {
                    LinearProgressIndicator(
                        progress = { group.averageProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            if (!selectionMode && showChevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

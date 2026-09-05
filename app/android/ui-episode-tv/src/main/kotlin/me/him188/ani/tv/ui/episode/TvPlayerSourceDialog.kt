/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.datasources.api.Media

/**
 * 数据源选择居中弹窗 (atv-architecture.md §8.3: 0.72 宽, WEB 源简化列表, §8.1).
 *
 * 焦点被约束在弹窗内 (onExit cancel); 初始焦点由 Screen 送到 [entryAnchorModifier]
 * 标注的行 (当前选中项, 无则第一项). 返回键关闭由 Screen 的 BackHandler 分层处理.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvPlayerSourceDialog(
    candidates: List<Media>,
    selected: Media?,
    listState: LazyListState,
    containerModifier: Modifier,
    entryAnchorModifier: Modifier,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryIndex = candidates.indexOf(selected).takeIf { it >= 0 } ?: 0
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            containerModifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.8f)
                .background(Color(0xF517191C), RoundedCornerShape(16.dp))
                .padding(vertical = 20.dp)
                // 焦点约束在弹窗内: 任何方向的离开一律取消 (弹窗下层控制层仍在组合中)
                .focusProperties { onExit = { cancelFocus() } }
                .focusGroup(),
        ) {
            Row(
                Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选择数据源",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    if (candidates.isEmpty()) "正在查询…" else "${candidates.size} 个资源",
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(candidates, key = { _, media -> media.mediaId }) { index, media ->
                    SourceOptionRow(
                        media = media,
                        isSelected = media == selected,
                        onClick = { onSelect(media) },
                        modifier = if (index == entryIndex) entryAnchorModifier else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceOptionRow(
    media: Media,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = playerInverseSurfaceColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "当前选中",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                media.properties.alliance,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val badges = buildList {
                media.properties.resolution.takeIf { it.isNotBlank() }?.let(::add)
                if (media.properties.subtitleLanguageIds.isNotEmpty()) {
                    add(media.properties.subtitleLanguageIds.joinToString("/"))
                }
            }
            if (badges.isNotEmpty()) {
                Text(
                    badges.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

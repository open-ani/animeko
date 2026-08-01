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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 播放器控制层 (atv-architecture.md §8.3), 布局对齐参考实现:
 * 顶部标题两行 -> 中部功能胶囊行 -> 进度条行 (左右时间) -> 底部图标行.
 *
 * M5 起为胶囊/图标接入面板与弹窗; 当前为视觉与信息层.
 */
@Composable
internal fun TvPlayerControlsOverlay(
    title: TvEpisodeViewModel.TitleInfo,
    mediaLabel: String?,
    positionMillis: Long,
    durationMillis: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // 顶部 scrim + 两行标题
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 40.dp),
        ) {
            Text(
                title.subjectName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                title.episodeLine,
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                )
                .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 20.dp),
        ) {
            // 功能胶囊行
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapsuleChip(Icons.Rounded.Recommend, "相关推荐")
                CapsuleChip(Icons.Rounded.Groups, "制作人员")
                CapsuleChip(Icons.Rounded.Face, "角色")
                CapsuleChip(Icons.Rounded.Comment, "评论")
                CapsuleChip(Icons.Rounded.FormatListBulleted, "弹幕列表")
            }

            // 进度条行: 左当前时间 · 轨 · 右总时长
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    formatTime(positionMillis),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                val fraction = if (durationMillis > 0) {
                    (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.26f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    formatTime(durationMillis),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }

            // 底部图标行: 左组 (跳转/切集/字幕) · 右组 (数据源/倍速/画面比例/状态)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ControlIcon(Icons.Rounded.Replay10)
                    ControlIcon(Icons.Rounded.SkipNext)
                    ControlIcon(Icons.Rounded.Forward30)
                    ControlIcon(Icons.Rounded.Subtitles)
                }
                Box(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    mediaLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    LabeledIcon(Icons.Rounded.Speed, "倍速")
                    LabeledIcon(Icons.Rounded.AspectRatio, "适应")
                    Text(
                        if (isPlaying) "播放中" else "已暂停",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = Color.White)
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, modifier.size(26.dp), tint = Color.White.copy(alpha = 0.9f))
}

@Composable
private fun LabeledIcon(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.9f))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f))
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

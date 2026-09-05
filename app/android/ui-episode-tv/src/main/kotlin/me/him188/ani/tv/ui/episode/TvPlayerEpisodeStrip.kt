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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults

/** [TvPlayerEpisodeStrip] 默认值 (附录 A: 选集卡 204×114.75dp/播放器, 三态). */
internal object TvPlayerEpisodeStripDefaults {
    /** 卡宽 (约 4 卡/屏). */
    val CardWidth: Dp = 204.dp

    /** 卡高 (16:9). */
    val CardHeight: Dp = 114.75.dp

    /** 卡间距. */
    val CardSpacing: Dp = 12.dp

    /** 卡圆角 (= 聚焦描边圆角 11 - 留白 3, 同海报卡). */
    val CardShape = RoundedCornerShape(8.dp)
}

/**
 * 播放器选集条 (atv-architecture.md §8.3): 图标行下方横向剧照卡列表, 三态 =
 * 正在播放 (primary 徽标) / 已看 (降透明 + 对勾) / 未看. TMDB 分集剧照 (R3) 落地前
 * 卡面为纯色渐变 + 集序号/标题.
 *
 * 纯视图组件: 展开/收起与焦点接线由 Screen 注入 ([stripModifier]/[currentCardModifier], §14.7-2).
 */
@Composable
internal fun TvPlayerEpisodeStrip(
    episodes: List<TvEpisodeViewModel.StripEpisode>,
    currentEpisodeId: Int,
    listState: LazyListState,
    stripModifier: Modifier,
    currentCardModifier: Modifier,
    onClickEpisode: (TvEpisodeViewModel.StripEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.then(stripModifier).fillMaxWidth().padding(top = 18.dp),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(TvPlayerEpisodeStripDefaults.CardSpacing),
        contentPadding = PaddingValues(horizontal = TvPlayerControlsDefaults.HorizontalPadding),
    ) {
        items(episodes, key = { it.episodeId }) { episode ->
            val isCurrent = episode.episodeId == currentEpisodeId
            EpisodeStripCard(
                episode = episode,
                isCurrent = isCurrent,
                onClick = { onClickEpisode(episode) },
                modifier = if (isCurrent) currentCardModifier else Modifier,
            )
        }
    }
}

@Composable
private fun EpisodeStripCard(
    episode: TvEpisodeViewModel.StripEpisode,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(
            TvPlayerEpisodeStripDefaults.CardWidth,
            TvPlayerEpisodeStripDefaults.CardHeight,
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(TvFocusDefaults.RingCornerRadius)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
        border = TvFocusDefaults.clickableCardBorder(),
    ) {
        Box(
            Modifier
                .padding(TvFocusDefaults.RingInset)
                .fillMaxSize()
                .clip(TvPlayerEpisodeStripDefaults.CardShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF2A2E35),
                        1f to Color(0xFF181A1E),
                    ),
                ),
        ) {
            if (episode.watched && !isCurrent) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "已看",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp),
                    tint = Color.White.copy(alpha = 0.65f),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        episode.sortLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (episode.watched && !isCurrent) {
                            Color.White.copy(alpha = 0.55f)
                        } else {
                            Color.White
                        },
                    )
                    if (isCurrent) {
                        Row(
                            Modifier
                                .padding(start = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                "正在播放",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Text(
                    episode.title,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (episode.watched && !isCurrent) {
                        Color.White.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.8f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

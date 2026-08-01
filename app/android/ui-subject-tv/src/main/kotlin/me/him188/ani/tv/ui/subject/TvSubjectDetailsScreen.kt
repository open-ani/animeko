/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults

/**
 * TV 条目详情页 (atv-architecture.md §7.5):
 * 全屏 backdrop + Hero 首屏 (标题/原名 + 贴底三列信息带) + 选集横版剧照轮播.
 *
 * 布局对齐参考实现 (第三方 TV 版实机效果).
 */
@Composable
fun TvSubjectDetailsScreen(
    viewModel: TvSubjectDetailsViewModel,
    onPlayEpisode: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject by viewModel.subject.collectAsState()
    val backdropUrl by viewModel.backdropUrl.collectAsState()
    val episodeStills by viewModel.episodeStills.collectAsState()

    Box(modifier.fillMaxSize()) {
        val info = subject
        if (info == null) {
            Text(
                "加载中…",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            SubjectContent(info, backdropUrl, episodeStills, onPlayEpisode)
        }
    }
}

@Composable
private fun SubjectContent(
    info: SubjectCollectionInfo,
    backdropUrl: String?,
    episodeStills: Map<Int, String>,
    onPlayEpisode: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectInfo = info.subjectInfo
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 续播目标: 第一个未看完的集, 否则第一集
    val playTarget = info.episodes.firstOrNull { !it.collectionType.isDoneOrDropped() }
        ?: info.episodes.firstOrNull()

    Box(modifier.fillMaxSize()) {
        // 全屏 backdrop: TMDB 横版图优先, 无图时退化为竖版海报 Crop
        AsyncImage(
            model = backdropUrl ?: subjectInfo.imageLarge,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to surfaceColor.copy(alpha = 0.5f),
                        0.5f to surfaceColor.copy(alpha = 0.72f),
                        1.0f to surfaceColor.copy(alpha = 0.95f),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp)) {
            // 标题 + 日文原名
            Text(
                subjectInfo.displayName,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subjectInfo.name.isNotBlank() && subjectInfo.name != subjectInfo.displayName) {
                Text(
                    subjectInfo.name,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(Modifier.weight(1f))

            // 贴底三列信息带 (参考版核心布局)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // 左列: 播放按钮
                Column(Modifier.width(210.dp)) {
                    if (playTarget != null) {
                        Button(onClick = { onPlayEpisode(playTarget.episodeId) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                            Text("开始观看", Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // 中列: 年月/连载进度 + 收藏统计三列
                Column(Modifier.width(230.dp)) {
                    subjectInfo.airDate.takeIf { it.isValid }?.let { date ->
                        Text(
                            "${date.year} 年 ${date.month} 月",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val latest = info.airingInfo.latestSort
                    val total = subjectInfo.totalEpisodes.takeIf { it > 0 }
                    val progress = buildString {
                        if (latest != null) append("连载至 $latest")
                        if (total != null) {
                            if (isNotEmpty()) append(" · ")
                            append("预定全 $total 话")
                        }
                    }
                    if (progress.isNotEmpty()) {
                        Text(
                            progress,
                            Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        val stats = subjectInfo.collectionStats
                        StatColumn(stats.collect, "收藏")
                        StatColumn(stats.doing, "在看")
                        StatColumn(stats.wish, "想看")
                    }
                }

                // 标签墙
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subjectInfo.tags.take(10).chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { tag ->
                                Text(
                                    tag.name,
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // 右列: 评分直方图 + 分数
                RatingBlock(info)
            }

            // 选集: 横版剧照卡 (参考版 16:9, 卡内左下角序号 + 标题)
            Text(
                "选集",
                Modifier.padding(top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 48.dp),
            ) {
                items(info.episodes, key = { it.episodeId }) { episode ->
                    TvEpisodeCard(
                        episode = episode,
                        imageUrl = episodeStills[episode.episodeId]
                            ?: backdropUrl
                            ?: subjectInfo.imageLarge,
                        onClick = { onPlayEpisode(episode.episodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatCount(value),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 评分直方图 (1..10 竖条) + 分数 + 评分人数, 对齐参考版右下角布局. */
@Composable
private fun RatingBlock(info: SubjectCollectionInfo, modifier: Modifier = Modifier) {
    val rating = info.subjectInfo.ratingInfo
    val counts = (1..10).map { rating.count.get(it) }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier.width(250.dp), horizontalAlignment = Alignment.End) {
        Row(
            Modifier.height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { count ->
                val fraction = (count.toFloat() / max).coerceIn(0.04f, 1f)
                Box(
                    Modifier
                        .width(14.dp)
                        .height((46 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                )
            }
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (1..10).forEach {
                Text(
                    "$it",
                    Modifier.width(14.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                rating.score.takeIf { it.isNotBlank() } ?: "-",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                buildString {
                    if (rating.rank > 0) append("#${rating.rank} · ")
                    append("${formatCount(rating.total)} 人评分")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: EpisodeCollectionInfo,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = episode.collectionType.isDoneOrDropped()
    Surface(
        onClick = onClick,
        modifier = modifier.width(228.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
        border = TvFocusDefaults.clickableCardBorder(),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 底部渐变 + 序号/标题 (参考版卡内布局)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    episode.episodeInfo.sort.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                )
                Text(
                    episode.episodeInfo.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatCount(value: Int): String = when {
    value >= 1000 -> "%,d".format(value)
    else -> value.toString()
}

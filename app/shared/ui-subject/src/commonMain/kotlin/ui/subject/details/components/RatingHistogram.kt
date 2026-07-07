/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

/**
 * 评分分布直方图: 横轴为 1~10 分, 纵轴柱高按各分段人数相对最大值归一化.
 *
 * 数据取自 [RatingInfo.count] ([RatingCounts]), 服务端已提供, 无需额外请求.
 * 用于双栏评分区右对齐展示, 以及三栏右栏"评分"卡.
 *
 * 对齐 Figma `RatingHistogram` (1561:7512): 仅绘制底部对齐的实心柱, 无全高轨道;
 * 无人评分的分段画一条 [trackColor] 底部刻度, 保证空数据时布局不塌陷.
 */
@Composable
fun RatingHistogram(
    ratingInfo: RatingInfo,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    barHeight: Dp = 64.dp,
    barCornerRadius: Dp = 3.dp,
    barSpacing: Dp = 6.dp,
) {
    val counts = remember(ratingInfo) { IntArray(10) { ratingInfo.count.get(it + 1) } }
    val max = remember(counts) { counts.maxOrNull() ?: 0 }
    val shape = remember(barCornerRadius) { RoundedCornerShape(barCornerRadius) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (score in 1..10) {
                val count = counts[score - 1]
                // 归一化到 [MIN_VISIBLE_FRACTION, 1]; 有人评分时至少留一点可见高度.
                val hasVotes = max > 0 && count > 0
                val fraction = if (hasVotes) {
                    (count.toFloat() / max).coerceIn(MIN_VISIBLE_FRACTION, 1f)
                } else {
                    MIN_VISIBLE_FRACTION
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(shape)
                        .background(if (hasVotes) barColor else trackColor),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
        ) {
            for (score in 1..10) {
                Text(
                    score.toString(),
                    Modifier.weight(1f),
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val MIN_VISIBLE_FRACTION = 0.06f

@PreviewLightDark
@Composable
private fun PreviewRatingHistogram() {
    ProvideCompositionLocalsForPreview {
        Column(Modifier.padding(16.dp).width(320.dp)) {
            RatingHistogram(
                RatingInfo(
                    rank = 72,
                    total = 39983,
                    count = RatingCounts(
                        s1 = 120, s2 = 90, s3 = 150, s4 = 320, s5 = 780,
                        s6 = 1800, s7 = 5200, s8 = 12000, s9 = 9800, s10 = 9720,
                    ),
                    score = "8.4",
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewRatingHistogramEmpty() {
    ProvideCompositionLocalsForPreview {
        Column(Modifier.padding(16.dp).width(320.dp)) {
            RatingHistogram(RatingInfo.Empty)
        }
    }
}

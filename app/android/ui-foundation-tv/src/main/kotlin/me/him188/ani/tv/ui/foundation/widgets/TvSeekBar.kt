/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/** [TvSeekBar] 默认值 (atv-architecture.md §8.3: 6dp 轨、缓冲/已播分色、聚焦圆点). */
object TvSeekBarDefaults {
    /** 轨道高度. */
    val TrackHeight: Dp = 6.dp

    /** 聚焦圆点直径. */
    val DotSize: Dp = 16.dp

    /** 底轨色 (未播未缓冲). */
    val TrackColor: Color = Color.White.copy(alpha = 0.26f)

    /** 缓冲段色 (已缓冲未播). */
    val BufferColor: Color = Color.White.copy(alpha = 0.42f)

    /** 圆点色 (播放器系反色: 白). */
    val DotColor: Color = Color.White

    /** 已播段色. */
    val playedColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}

/**
 * TV 播放器进度条 (atv-architecture.md §8.3): 整行单焦点组件的**纯视觉**部分 ——
 * 6dp 轨 + 缓冲段/已播段分色 + 聚焦圆点; 拖拽预览 (scrub) 时圆点脱离播放位置移动.
 *
 * 按键/聚焦语义由调用方 (播放页根部按键路由) 持有, 本组件只按状态绘制:
 * [showDot] 聚焦或拖拽中显示圆点; [scrubMillis] 非空 = 拖拽预览态, 圆点画在预览位置,
 * 已播段仍停留在实际播放位置.
 */
@Composable
fun TvSeekBar(
    positionMillis: Long,
    durationMillis: Long,
    modifier: Modifier = Modifier,
    bufferedFraction: Float = 0f,
    scrubMillis: Long? = null,
    showDot: Boolean = false,
    trackHeight: Dp = TvSeekBarDefaults.TrackHeight,
    dotSize: Dp = TvSeekBarDefaults.DotSize,
    trackColor: Color = TvSeekBarDefaults.TrackColor,
    bufferColor: Color = TvSeekBarDefaults.BufferColor,
    playedColor: Color = TvSeekBarDefaults.playedColor,
    dotColor: Color = TvSeekBarDefaults.DotColor,
) {
    fun fractionOf(millis: Long): Float =
        if (durationMillis > 0) (millis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f

    val playedFraction = fractionOf(positionMillis)
    val dotFraction = fractionOf(scrubMillis ?: positionMillis)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(maxOf(trackHeight, dotSize)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackShape = RoundedCornerShape(trackHeight / 2)
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(trackShape)
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(bufferColor),
            )
            Box(
                Modifier
                    .fillMaxWidth(playedFraction)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(playedColor),
            )
        }
        if (showDot || scrubMillis != null) {
            val travel = maxWidth - dotSize
            Box(
                Modifier
                    .offset(x = travel * dotFraction)
                    .size(dotSize)
                    .background(dotColor, CircleShape),
            )
        }
    }
}

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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable

/** [TvLandscapeCard] 默认值 (Prime Video 实测: 卡宽≈屏宽 20%, 16:9, 间距≈16dp, 4 卡整 + 1 卡半露). */
object TvLandscapeCardDefaults {
    /** 卡片宽度. */
    val Width: Dp = 192.dp

    /** 横版图宽高比. */
    const val AspectRatio: Float = 16f / 9f

    /** 同行卡片间距. */
    val Spacing: Dp = 16.dp

    /** 图圆角 (= 聚焦描边圆角 11 - 留白 3, 同海报卡). */
    val ImageShape = RoundedCornerShape(8.dp)

    /** 无图/加载中的底色. */
    val PlaceholderColor: Color = Color(0xFF1E2126)

    /** 标题底部遮罩最深处 alpha. */
    const val TitleScrimAlpha: Float = 0.85f
}

/**
 * 横版 16:9 条目卡 (探索页 Prime 式行列表): TMDB backdrop 横图 (缺图时退化为海报裁切),
 * 卡内底部渐变遮罩上叠 [overline 小字 +] 标题 (横图无片名, 与 Prime 的 key art 不同, 必须自绘标题);
 * 聚焦 2.5dp primary 描边 @ 圆角 11dp, 内容常驻内缩 3dp, 无缩放 (TvFocusDefaults), 标题跑马灯.
 */
@Composable
fun TvLandscapeCard(
    imageUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    /** 焦点记忆身份键 (页内唯一): 跨 route 返回时恢复焦点用; null = 不参与. */
    memoryId: Any? = null,
    /** null = 宽度交给调用方 modifier 决定 (如 Row 内 weight 等分的自适应网格). */
    width: Dp? = TvLandscapeCardDefaults.Width,
    /** 标题上方的一行小字 (如继续观看进度「继续 · 第 3 话」); null 不显示. */
    overline: String? = null,
) {
    var selfFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .tvFocusMemorable(memoryId)
            .onFocusChanged {
                selfFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
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
                .fillMaxWidth()
                .aspectRatio(TvLandscapeCardDefaults.AspectRatio)
                .clip(TvLandscapeCardDefaults.ImageShape)
                .background(TvLandscapeCardDefaults.PlaceholderColor),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = TvLandscapeCardDefaults.TitleScrimAlpha),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (overline != null) {
                    Text(
                        overline,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    title,
                    Modifier
                        .fillMaxWidth()
                        // 聚焦时跑马灯滚动: 完整标题信息优先 (失焦即停)
                        .then(if (selfFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

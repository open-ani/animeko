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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.effects.blurEffect
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.focus.tvCardFocusBorder
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable

/** [TvPosterCard] 默认值 (海报卡 112dp/0.72/圆角 8/间距 10). */
object TvPosterCardDefaults {
    /** 卡片宽度 (探索/追番/搜索页统一规格). */
    val Width: Dp = 112.dp

    /** 竖版封面宽高比 (与详情页封面一致). */
    const val CoverRatio: Float = 0.72f

    /** 封面内容圆角，描边在图片外侧预留的空间绘制。 */
    val ImageShape = RoundedCornerShape(8.dp)
}

/**
 * 竖版海报卡:
 * 图片使用共享焦点描边和留白，无缩放；标题位于图片下方。
 */
@Composable
fun TvPosterCard(
    imageUrl: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    /** 焦点记忆身份键 (页内唯一, 如带前缀的 subjectId): 跨 route 返回时恢复焦点用; null = 不参与. */
    memoryId: Any? = null,
    /** null = 宽度交给调用方 modifier 决定 (如 Row 内 weight 等分的自适应网格). */
    width: Dp? = TvPosterCardDefaults.Width,
    /** 参考版探索页卡片行为纯图, 标题只在网格页展示 */
    showTitle: Boolean = true,
    obscureImage: Boolean = false,
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
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale, pressedScale = 1f),
    ) {
        Column {
            Box(Modifier.tvCardFocusBorder(selfFocused).padding(TvFocusDefaults.RingInset)) {
                val imageModifier = Modifier.fillMaxWidth().aspectRatio(TvPosterCardDefaults.CoverRatio)
                    .clip(TvPosterCardDefaults.ImageShape)
                if (obscureImage) Box(
                    imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = imageModifier.blurEffect(12.dp, BlurredEdgeTreatment.Unbounded).alpha(.6f),
                        contentScale = ContentScale.Crop,
                    )
                    Icon(Icons.Outlined.VisibilityOff, null)
                } else AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                )
            }
            if (showTitle) {
                Text(
                    title,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TvFocusDefaults.RingInset + 2.dp)
                        .padding(top = 6.dp, bottom = 2.dp)
                        .then(
                            // 聚焦时跑马灯滚动: 完整标题信息优先 (失焦即停)
                            if (selfFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.github.panpf.sketch.AsyncImageState

val DEFAULT_BACKGROUND_BRUSH = Brush.verticalGradient(
    0f to Color(0xB2FAFAFA),
    1.00f to Color(0xFFFAFAFA),
)

/**
 * 添加渐变图片背景或默认纯色背景
 * @param data 图片 URL 或文件路径
 * @param fallbackColor 无图片时的纯色
 * @param thumbnail 先显示的低分辨率版本, 含义同 [AsyncImage] 的 `thumbnailModel`
 */
fun Modifier.backgroundWithGradient(
    data: String?,
    fallbackColor: Color,
    brush: Brush = DEFAULT_BACKGROUND_BRUSH,
    thumbnail: String? = null,
) =
    if (data == null) {
        composed {
            background(fallbackColor)
        }
    } else {
        paintBackground(data, thumbnail = thumbnail).background(brush = brush)
    }

/**
 * 添加图片背景或默认纯色背景
 * @param data 图片 URL 或文件路径
 * @param fallbackColor 无图片时的纯色
 */
fun Modifier.backgroundOrFallback(painter: Painter?, fallbackColor: Color) =
    if (painter == null) {
        composed {
            background(fallbackColor)
        }
    } else {
        paintBackground(painter)
    }

fun Modifier.paintBackground(painter: Painter): Modifier = composed {
    paint(
        painter,
        contentScale = ContentScale.Crop,
    )
}

fun Modifier.paintBackground(data: String?, thumbnail: String? = null): Modifier =
    paintBackground(data, state = null, thumbnail = thumbnail)

internal fun Modifier.paintBackground(
    data: String?,
    state: AsyncImageState?,
    thumbnail: String? = null,
): Modifier = composed {
    var requestSize by remember { mutableStateOf<IntSize?>(null) }
    onSizeChanged { size ->
        val roundedSize = size.toAniImageRequestSize()
        if (requestSize != roundedSize) requestSize = roundedSize
    }.paint(
        rememberAniAsyncImagePainter(
            model = data,
            contentScale = ContentScale.Crop,
            requestSize = requestSize,
            filterQuality = defaultFilterQuality,
            state = state,
            thumbnailModel = thumbnail,
        ),
        contentScale = ContentScale.Crop,
    )
}

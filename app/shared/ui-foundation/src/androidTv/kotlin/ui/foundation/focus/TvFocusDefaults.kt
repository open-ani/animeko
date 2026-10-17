/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * TV 列表卡片使用主题色描边、固定留白和无缩放的焦点反馈。
 */
object TvFocusDefaults {
    /** 聚焦和按下保持原有尺寸。 */
    const val FocusedScale: Float = 1f

    /** 聚焦描边宽度 (主题主色). */
    val RingWidth: Dp = 2.5.dp

    /** 描边内缘与卡片内容之间的空隙。 */
    val RingGap: Dp = 2.dp

    /** 常驻预留描边和空隙，焦点变化不影响图片大小及布局。 */
    val RingInset: Dp = RingWidth + RingGap

    /** 内容圆角 8dp 的卡片所使用的描边外圆角。 */
    val RingCornerRadius: Dp = 8.dp + RingInset
}

/** Draw inside the reserved frame; callers inset their content by [TvFocusDefaults.RingInset]. */
@Composable
fun Modifier.tvCardFocusBorder(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(TvFocusDefaults.RingCornerRadius),
): Modifier = border(TvFocusDefaults.RingWidth, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)

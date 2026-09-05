/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme

/**
 * TV 焦点视觉的唯一出口 (atv-architecture.md D7):
 * PR#3217 实机验证的「色圈 + 留白、无缩放」风格. 如需切回官方缩放风格, 改这一处即可.
 */
object TvFocusDefaults {
    /** 无缩放; 官方风格为 1.05f. */
    const val FocusedScale: Float = 1f

    /** 聚焦描边宽度 (主题主色). */
    val RingWidth: Dp = 2.5.dp

    /** 描边与内容之间的留白 (聚焦时露出底色形成"色圈+留白"); 内容常驻按此内缩防跳动. */
    val RingInset: Dp = 3.dp

    /** 描边圆角 (= 内容圆角 8 + [RingInset] 3). */
    val RingCornerRadius: Dp = 11.dp

    /** 海报卡聚焦描边: [RingWidth] primary @ [RingCornerRadius], 内容常驻内缩 [RingInset]. */
    @Composable
    fun cardBorder(): Border = Border(
        border = BorderStroke(RingWidth, MaterialTheme.colorScheme.primary),
        inset = RingInset,
        shape = RoundedCornerShape(RingCornerRadius),
    )

    /** 不使用 glow. */
    fun cardGlow(): Glow = Glow.None

    /** [cardBorder] 的 tv Surface(clickable) 包装. */
    @Composable
    fun clickableCardBorder(shape: Shape = RoundedCornerShape(RingCornerRadius)): ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(RingWidth, MaterialTheme.colorScheme.primary),
                inset = RingInset,
                shape = shape,
            ),
        )
}

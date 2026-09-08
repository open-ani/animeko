/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme

/** Shared colors and geometry for focusable TV options, independent of their host panel. */
object TvOptionDefaults {
    val Container = Color(0xFF202127)
    val Raised = Color(0xFF2D2F37)
    val Content = Color(0xFFF2F2F6)
    val Muted = Color(0xFFB9BBC6)
    val Outline = Color.White.copy(alpha = .08f)
    val FocusedContainer = Color(0xFFF2F2F6)
    val FocusedContent = Color(0xFF1B1B20)
    val ItemShape = RoundedCornerShape(12.dp)
}

/** TV options inherit the palette of their containing surface. */
@Immutable
data class TvOptionColors(
    val container: Color = TvOptionDefaults.Container,
    val raised: Color = TvOptionDefaults.Raised,
    val content: Color = TvOptionDefaults.Content,
    val muted: Color = TvOptionDefaults.Muted,
    val outline: Color = TvOptionDefaults.Outline,
    val focusedContainer: Color = TvOptionDefaults.FocusedContainer,
    val focusedContent: Color = TvOptionDefaults.FocusedContent,
    /** Null uses the theme accent over [container]. */
    val selectedContainer: Color? = null,
)

val LocalTvOptionColors = staticCompositionLocalOf { TvOptionColors() }

@Composable
fun tvOptionSurfaceColors(selected: Boolean = false, filled: Boolean = false) =
    with(LocalTvOptionColors.current) {
        ClickableSurfaceDefaults.colors(
            containerColor = when {
                selected -> selectedContainer ?: MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                    .compositeOver(container)

                filled -> raised
                else -> Color.Transparent
            },
            contentColor = content,
            focusedContainerColor = focusedContainer,
            focusedContentColor = focusedContent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = muted.copy(alpha = .5f),
        )
    }

@Composable
fun TvOptionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalTvOptionColors.current.outline),
    )
}

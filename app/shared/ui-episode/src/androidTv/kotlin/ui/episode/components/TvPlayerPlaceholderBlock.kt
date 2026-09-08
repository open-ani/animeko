/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.external.placeholder.PlaceholderHighlight
import me.him188.ani.app.ui.external.placeholder.fade
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors

@Composable
internal fun TvPlayerPlaceholderBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    val contentColor = LocalTvOptionColors.current.content
    Spacer(
        modifier.placeholder(
            visible = true,
            color = contentColor.copy(alpha = .12f),
            shape = shape,
            highlight = { PlaceholderHighlight.fade(contentColor.copy(alpha = .08f)) },
        ),
    )
}

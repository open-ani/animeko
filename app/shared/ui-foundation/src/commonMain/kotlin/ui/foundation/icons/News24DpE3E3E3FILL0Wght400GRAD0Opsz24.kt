/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.icons

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Outlined.News: ImageVector
    get() {
        if (_News24DpE3E3E3FILL0Wght400GRAD0Opsz24 != null) {
            return _News24DpE3E3E3FILL0Wght400GRAD0Opsz24!!
        }
        _News24DpE3E3E3FILL0Wght400GRAD0Opsz24 = ImageVector.Builder(
            name = "News24DpE3E3E3FILL0Wght400GRAD0Opsz24",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveTo(200f, 840f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(120f, 760f)
                verticalLineToRelative(-560f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(200f, 120f)
                horizontalLineToRelative(440f)
                lineToRelative(200f, 200f)
                verticalLineToRelative(440f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(760f, 840f)
                lineTo(200f, 840f)
                close()
                moveTo(200f, 760f)
                horizontalLineToRelative(560f)
                verticalLineToRelative(-400f)
                lineTo(600f, 360f)
                verticalLineToRelative(-160f)
                lineTo(200f, 200f)
                verticalLineToRelative(560f)
                close()
                moveTo(280f, 680f)
                horizontalLineToRelative(400f)
                verticalLineToRelative(-80f)
                lineTo(280f, 600f)
                verticalLineToRelative(80f)
                close()
                moveTo(280f, 360f)
                horizontalLineToRelative(200f)
                verticalLineToRelative(-80f)
                lineTo(280f, 280f)
                verticalLineToRelative(80f)
                close()
                moveTo(280f, 520f)
                horizontalLineToRelative(400f)
                verticalLineToRelative(-80f)
                lineTo(280f, 440f)
                verticalLineToRelative(80f)
                close()
                moveTo(200f, 200f)
                verticalLineToRelative(160f)
                verticalLineToRelative(-160f)
                verticalLineToRelative(560f)
                verticalLineToRelative(-560f)
                close()
            }
        }.build()

        return _News24DpE3E3E3FILL0Wght400GRAD0Opsz24!!
    }

@Suppress("ObjectPropertyName")
private var _News24DpE3E3E3FILL0Wght400GRAD0Opsz24: ImageVector? = null

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

val Icons.Outlined.AwardStar: ImageVector
    get() {
        if (_AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24 != null) {
            return _AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24!!
        }
        _AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24 = ImageVector.Builder(
            name = "AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveToRelative(363f, 650f)
                lineToRelative(117f, -71f)
                lineToRelative(117f, 71f)
                lineToRelative(-31f, -133f)
                lineToRelative(104f, -90f)
                lineToRelative(-137f, -11f)
                lineToRelative(-53f, -126f)
                lineToRelative(-53f, 126f)
                lineToRelative(-137f, 11f)
                lineToRelative(104f, 90f)
                lineToRelative(-31f, 133f)
                close()
                moveTo(480f, 932f)
                lineTo(346f, 800f)
                lineTo(160f, 800f)
                verticalLineToRelative(-186f)
                lineTo(28f, 480f)
                lineToRelative(132f, -134f)
                verticalLineToRelative(-186f)
                horizontalLineToRelative(186f)
                lineToRelative(134f, -132f)
                lineToRelative(134f, 132f)
                horizontalLineToRelative(186f)
                verticalLineToRelative(186f)
                lineToRelative(132f, 134f)
                lineToRelative(-132f, 134f)
                verticalLineToRelative(186f)
                lineTo(614f, 800f)
                lineTo(480f, 932f)
                close()
                moveTo(480f, 820f)
                lineTo(580f, 720f)
                horizontalLineToRelative(140f)
                verticalLineToRelative(-140f)
                lineToRelative(100f, -100f)
                lineToRelative(-100f, -100f)
                verticalLineToRelative(-140f)
                lineTo(580f, 240f)
                lineTo(480f, 140f)
                lineTo(380f, 240f)
                lineTo(240f, 240f)
                verticalLineToRelative(140f)
                lineTo(140f, 480f)
                lineToRelative(100f, 100f)
                verticalLineToRelative(140f)
                horizontalLineToRelative(140f)
                lineToRelative(100f, 100f)
                close()
                moveTo(480f, 480f)
                close()
            }
        }.build()

        return _AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24!!
    }

@Suppress("ObjectPropertyName")
private var _AwardStar24DpE3E3E3FILL0Wght400GRAD0Opsz24: ImageVector? = null

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val AniIcons.Forward85: ImageVector
    get() {
        if (_forward85 != null) {
            return _forward85!!
        }
        _forward85 = Builder(
            name = "Forward85",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 960.0f,
            viewportHeight = 960.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFFe8eaed)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveToRelative(480.0f, 880.0f)
                curveToRelative(-50.0f, 0.0f, -96.833f, -9.5f, -140.5f, -28.5f)
                curveToRelative(-43.667f, -19.0f, -81.667f, -44.667f, -114.0f, -77.0f)
                curveToRelative(-32.333f, -32.333f, -58.0f, -70.333f, -77.0f, -114.0f)
                curveToRelative(-19.0f, -43.667f, -28.5f, -90.5f, -28.5f, -140.5f)
                curveToRelative(0.0f, -50.0f, 9.5f, -96.833f, 28.5f, -140.5f)
                curveToRelative(19.0f, -43.667f, 44.667f, -81.667f, 77.0f, -114.0f)
                curveToRelative(32.333f, -32.333f, 70.333f, -58.0f, 114.0f, -77.0f)
                curveToRelative(43.667f, -19.0f, 90.5f, -28.5f, 140.5f, -28.5f)
                horizontalLineToRelative(6.0f)
                lineToRelative(-62.0f, -62.0f)
                lineToRelative(56.0f, -58.0f)
                lineToRelative(160.0f, 160.0f)
                lineToRelative(-160.0f, 160.0f)
                lineToRelative(-56.0f, -58.0f)
                lineToRelative(62.0f, -62.0f)
                horizontalLineToRelative(-6.0f)
                curveToRelative(-78.0f, 0.0f, -144.167f, 27.167f, -198.5f, 81.5f)
                curveToRelative(-54.333f, 54.333f, -81.5f, 120.5f, -81.5f, 198.5f)
                curveToRelative(0.0f, 78.0f, 27.167f, 144.167f, 81.5f, 198.5f)
                curveToRelative(54.333f, 54.333f, 120.5f, 81.5f, 198.5f, 81.5f)
                curveToRelative(78.0f, 0.0f, 144.167f, -27.167f, 198.5f, -81.5f)
                curveToRelative(54.333f, -54.333f, 81.5f, -120.5f, 81.5f, -198.5f)
                horizontalLineToRelative(80.0f)
                curveToRelative(0.0f, 130.426f, -67.22f, 216.22f, -105.5f, 254.5f)
                curveToRelative(-32.333f, 32.333f, -70.333f, 58.0f, -114.0f, 77.0f)
                curveToRelative(-43.667f, 19.0f, -90.5f, 28.5f, -140.5f, 28.5f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFFe8eaed)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveToRelative(340.006f, 640.0f)
                horizontalLineToRelative(80.0f)
                curveToRelative(11.333f, 0.0f, 20.833f, -3.833f, 28.5f, -11.5f)
                curveToRelative(7.667f, -7.667f, 11.5f, -17.167f, 11.5f, -28.5f)
                verticalLineToRelative(-160.0f)
                curveToRelative(0.0f, -11.333f, -3.833f, -20.833f, -11.5f, -28.5f)
                curveToRelative(-7.667f, -7.667f, -17.167f, -11.5f, -28.5f, -11.5f)
                horizontalLineToRelative(-80.0f)
                curveToRelative(-11.333f, 0.0f, -20.833f, 3.833f, -28.5f, 11.5f)
                curveToRelative(-7.667f, 7.667f, -11.5f, 17.167f, -11.5f, 28.5f)
                verticalLineToRelative(160.0f)
                curveToRelative(0.0f, 11.333f, 3.833f, 20.833f, 11.5f, 28.5f)
                curveToRelative(7.667f, 7.667f, 17.167f, 11.5f, 28.5f, 11.5f)
                close()
                moveTo(360.006f, 500.0f)
                verticalLineToRelative(-60.0f)
                horizontalLineToRelative(40.0f)
                verticalLineToRelative(60.0f)
                close()
                moveTo(360.006f, 600.0f)
                verticalLineToRelative(-60.0f)
                horizontalLineToRelative(40.0f)
                verticalLineToRelative(60.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFFe8eaed)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveToRelative(500.006f, 640.0f)
                verticalLineToRelative(-60.0f)
                horizontalLineToRelative(99.989f)
                verticalLineToRelative(-40.0f)
                horizontalLineToRelative(-99.989f)
                verticalLineToRelative(-140.0f)
                horizontalLineToRelative(159.989f)
                verticalLineToRelative(60.0f)
                horizontalLineToRelative(-99.989f)
                verticalLineToRelative(40.0f)
                horizontalLineToRelative(59.989f)
                curveToRelative(11.333f, 0.0f, 20.833f, 3.833f, 28.5f, 11.5f)
                curveToRelative(7.667f, 7.667f, 11.5f, 17.167f, 11.5f, 28.5f)
                verticalLineToRelative(60.0f)
                curveToRelative(0.0f, 11.333f, -3.833f, 20.833f, -11.5f, 28.5f)
                curveToRelative(-7.667f, 7.667f, -17.167f, 11.5f, -28.5f, 11.5f)
                close()
            }
        }
            .build()
        return _forward85!!
    }

private var _forward85: ImageVector? = null

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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val AniIcons.RightPanelClose: ImageVector
    get() {
        if (_Right_panel_close != null) {
            return _Right_panel_close!!
        }
        _Right_panel_close = ImageVector.Builder(
            name = "Right_panel_close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(300f, 320f)
                verticalLineToRelative(320f)
                lineToRelative(160f, -160f)
                close()
                moveTo(200f, 840f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(120f, 760f)
                verticalLineToRelative(-560f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(200f, 120f)
                horizontalLineToRelative(560f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(840f, 200f)
                verticalLineToRelative(560f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(760f, 840f)
                close()
                moveToRelative(440f, -80f)
                horizontalLineToRelative(120f)
                verticalLineToRelative(-560f)
                horizontalLineTo(640f)
                close()
                moveToRelative(-80f, 0f)
                verticalLineToRelative(-560f)
                horizontalLineTo(200f)
                verticalLineToRelative(560f)
                close()
                moveToRelative(80f, 0f)
                horizontalLineToRelative(120f)
                close()
            }
        }.build()
        return _Right_panel_close!!
    }

private var _Right_panel_close: ImageVector? = null

public val AniIcons.RightPanelOpen: ImageVector
    get() {
        if (_Right_panel_open != null) {
            return _Right_panel_open!!
        }
        _Right_panel_open = ImageVector.Builder(
            name = "Right_panel_open",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(460f, 640f)
                verticalLineToRelative(-320f)
                lineTo(300f, 480f)
                close()
                moveTo(200f, 840f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(120f, 760f)
                verticalLineToRelative(-560f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(200f, 120f)
                horizontalLineToRelative(560f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(840f, 200f)
                verticalLineToRelative(560f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(760f, 840f)
                close()
                moveToRelative(440f, -80f)
                horizontalLineToRelative(120f)
                verticalLineToRelative(-560f)
                horizontalLineTo(640f)
                close()
                moveToRelative(-80f, 0f)
                verticalLineToRelative(-560f)
                horizontalLineTo(200f)
                verticalLineToRelative(560f)
                close()
                moveToRelative(80f, 0f)
                horizontalLineToRelative(120f)
                close()
            }
        }.build()
        return _Right_panel_open!!
    }

private var _Right_panel_open: ImageVector? = null

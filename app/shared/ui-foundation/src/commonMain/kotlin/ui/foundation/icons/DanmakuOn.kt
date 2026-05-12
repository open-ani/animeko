/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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

public val AniIcons.DanmakuOn: ImageVector
    get() {
        if (_danmakuOn != null) {
            return _danmakuOn!!
        }
        _danmakuOn = ImageVector.Builder(
            name = "DanmakuOn",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
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
                moveTo(9f, 12f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.117f, 1.993f)
                lineTo(9f, 14f)
                horizontalLineTo(4f)
                verticalLineToRelative(1f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.883f, 0.993f)
                lineTo(5f, 16f)
                horizontalLineToRelative(1.5f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.493f, 1.356f)
                lineTo(8f, 17.5f)
                verticalLineToRelative(0.5f)
                lineToRelative(2.133f, -1.6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.016f, -0.391f)
                lineToRelative(0.184f, -0.009f)
                horizontalLineTo(18f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.993f, -0.883f)
                lineTo(19f, 15f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.824f, 2.995f)
                lineTo(18f, 18f)
                horizontalLineToRelative(-6.667f)
                lineTo(8f, 20.5f)
                curveToRelative(-0.791f, 0.593f, -1.906f, 0.075f, -1.994f, -0.879f)
                lineTo(6f, 19.5f)
                verticalLineTo(18f)
                horizontalLineTo(5f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.995f, -2.824f)
                lineTo(2f, 15f)
                verticalLineToRelative(-2f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.883f, -0.993f)
                lineTo(3f, 12f)
                close()
                moveToRelative(6f, 0f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.117f, 1.993f)
                lineTo(15f, 14f)
                horizontalLineToRelative(-2f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.117f, -1.993f)
                lineTo(13f, 12f)
                close()
                moveTo(7f, 8f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.117f, 1.993f)
                lineTo(7f, 10f)
                horizontalLineTo(5f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.117f, -1.993f)
                lineTo(5f, 8f)
                close()
                moveToRelative(12f, 0f)
                lineToRelative(0.117f, 0.007f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1.986f)
                lineTo(19f, 10f)
                horizontalLineToRelative(-8f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.117f, -1.993f)
                lineTo(11f, 8f)
                close()
                moveToRelative(-1f, -5f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.995f, 2.824f)
                lineTo(21f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                verticalLineTo(6f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.883f, -0.993f)
                lineTo(18f, 5f)
                horizontalLineTo(5f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.993f, 0.883f)
                lineTo(4f, 6f)
                verticalLineToRelative(2f)
                horizontalLineTo(2f)
                verticalLineTo(6f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.824f, -2.995f)
                lineTo(5f, 3f)
                close()
            }
        }.build()
        return _danmakuOn!!
    }

private var _danmakuOn: ImageVector? = null

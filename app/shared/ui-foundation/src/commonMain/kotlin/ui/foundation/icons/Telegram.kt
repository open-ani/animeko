package me.him188.ani.app.ui.foundation.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp


private var _TelegramIcon: ImageVector? = null

public val AniIcons.Telegram: ImageVector
    get() {
        if (_TelegramIcon != null) {
            return _TelegramIcon!!
        }
        _TelegramIcon = ImageVector.Builder(
            name = "TelegramIcon",
            defaultWidth = 512.dp,
            defaultHeight = 512.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(
                fill = Brush.linearGradient(
                    start = Offset(256f, 3.84f),
                    end = Offset(256f, 512f),
                    colorStops = arrayOf(
                        0f to Color(0xFF2AABEE),
                        1f to Color(0xFF229ED9),
                    ),
                ),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(512f, 256f)
                arcTo(256f, 256f, 0f, isMoreThanHalf = false, isPositiveArc = true, 256f, 512f)
                arcTo(256f, 256f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 256f)
                arcTo(256f, 256f, 0f, isMoreThanHalf = false, isPositiveArc = true, 512f, 256f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFFFFFFFF)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(115.88f, 253.3f)
                curveToRelative(74.63f, -32.52f, 124.39f, -53.95f, 149.29f, -64.31f)
                curveToRelative(71.1f, -29.57f, 85.87f, -34.71f, 95.5f, -34.88f)
                curveToRelative(2.12f, -0.03f, 6.85f, 0.49f, 9.92f, 2.98f)
                curveToRelative(2.59f, 2.1f, 3.3f, 4.94f, 3.64f, 6.93f)
                curveToRelative(0.34f, 2f, 0.77f, 6.53f, 0.43f, 10.08f)
                curveToRelative(-3.85f, 40.48f, -20.52f, 138.71f, -29f, 184.05f)
                curveToRelative(-3.59f, 19.19f, -10.66f, 25.62f, -17.5f, 26.25f)
                curveToRelative(-14.86f, 1.37f, -26.15f, -9.83f, -40.55f, -19.27f)
                curveToRelative(-22.53f, -14.76f, -35.26f, -23.96f, -57.13f, -38.37f)
                curveToRelative(-25.28f, -16.66f, -8.89f, -25.81f, 5.51f, -40.77f)
                curveToRelative(3.77f, -3.92f, 69.27f, -63.5f, 70.54f, -68.9f)
                curveToRelative(0.16f, -0.68f, 0.31f, -3.2f, -1.19f, -4.53f)
                reflectiveCurveToRelative(-3.71f, -0.87f, -5.3f, -0.51f)
                curveToRelative(-2.26f, 0.51f, -38.25f, 24.3f, -107.98f, 71.37f)
                curveToRelative(-10.22f, 7.02f, -19.48f, 10.43f, -27.77f, 10.26f)
                curveToRelative(-9.14f, -0.2f, -26.72f, -5.17f, -39.79f, -9.42f)
                curveToRelative(-16.03f, -5.21f, -28.77f, -7.97f, -27.66f, -16.82f)
                curveToRelative(0.57f, -4.61f, 6.92f, -9.32f, 19.04f, -14.14f)
                close()
            }
        }.build()
        return _TelegramIcon!!
    }


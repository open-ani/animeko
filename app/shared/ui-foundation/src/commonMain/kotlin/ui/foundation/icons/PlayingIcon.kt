package me.him188.ani.app.ui.foundation.icons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_now_playing
import org.jetbrains.compose.resources.stringResource

/**
 * 三个矩形垂直运动 (高度变化)
 */
@Composable
fun PlayingIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    width: Dp = 24.dp,
    height: Dp = 16.dp,
    thickness: Dp = 3.dp,
    color: Color = LocalContentColor.current,
) {
    val resolvedContentDescription = contentDescription ?: stringResource(Lang.subject_episode_now_playing)
    val density = LocalDensity.current
    val totalHeightPx = with(density) { height.toPx() }
    val reservedHeightPx = totalHeightPx * (1 - 0.612f)
    val heightPx = totalHeightPx - reservedHeightPx

    val offset1 = 0.3f
    val offset2 = 1.0f
    val offset3 = 0.0f

    var line1Height by remember { mutableFloatStateOf(reservedHeightPx + calc(offset1 * heightPx, heightPx)) }
    var line2Height by remember { mutableFloatStateOf(reservedHeightPx + calc(offset2 * heightPx, heightPx)) }
    var line3Height by remember { mutableFloatStateOf(reservedHeightPx + calc(offset3 * heightPx, heightPx)) }
//    var line4Height by remember { mutableFloatStateOf(0f) }

    val lineCount = 3


    LaunchedEffect(density, height) {
        animate(
            0f, 2f,
            animationSpec = infiniteRepeatable(
                tween(1000, easing = LinearEasing),
                RepeatMode.Restart,
            ),
        ) { value, _ ->
            line1Height = reservedHeightPx + calc((value + offset1) * heightPx, heightPx)
            line2Height = reservedHeightPx + calc((value + offset2) * heightPx, heightPx)
            line3Height = reservedHeightPx + calc((value + offset3) * heightPx, heightPx)
//            line4Height = reservedHeightPx + calc((value + 0.5f) * heightPx, heightPx)
        }
    }

    val widthPx = with(density) { width.toPx() }
    val thicknessPx = with(density) { thickness.toPx() }
    val spacingPx = widthPx / (lineCount + 1)

    val cornerRadius: CornerRadius = with(density) {
        CornerRadius(2.dp.toPx(), 2.dp.toPx())
    }
    Box(
        modifier
            .height(max(width, height))
            .width(max(width, height)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Canvas(
            Modifier.height(height).width(width),
            resolvedContentDescription,
        ) {
            // draw there vertical lines, separated evenly
            drawRoundRect(
                color = color,
                topLeft = Offset(spacingPx * 1 - thicknessPx / 2, totalHeightPx - line1Height),
                size = Size(thicknessPx, line1Height),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(spacingPx * 2 - thicknessPx / 2, totalHeightPx - line2Height),
                size = Size(thicknessPx, line2Height),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(spacingPx * 3 - thicknessPx / 2, totalHeightPx - line3Height),
                size = Size(thicknessPx, line3Height),
                cornerRadius = cornerRadius,
            )
//        drawRoundRect(
//            color = color,
//            topLeft = Offset((spacingPx * 4) - (thicknessPx), heightPx - line4Height),
//            size = Size(thicknessPx, line4Height),
//        )
        }
    }
}

private fun calc(v: Float, heightPx: Float): Float {
    val period = 2 * heightPx
    val vMod = v % period
    return when {
        vMod > heightPx -> 2 * heightPx - vMod
        else -> vMod
    }
}

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.format2f
import kotlin.math.floor

/**
 * DanmakuState holds all params which [Canvas] needs to draw a danmaku text.
 */
@Immutable
data class DanmakuState(
    val presentation: DanmakuPresentation,
    val measurer: TextMeasurer,
    val baseStyle: TextStyle,
    val style: DanmakuStyle,
    val enableColor: Boolean,
    val isDebug: Boolean
) {
    val danmakuText = presentation.danmaku.run {
        val seconds = playTimeMillis.toFloat().div(1000)
        if (isDebug) "$text (${floor((seconds / 60)).toInt()}:${String.format2f(seconds % 60)})" else text
    }
    
    val solidTextLayout = measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(
            style.styleForText(
                color = if (enableColor) {
                    rgbColor(presentation.danmaku.color.toUInt().toLong()).copy(alpha = style.alpha)
                } else Color.White,
            ),
        ),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )
    
    val borderTextLayout = measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(style.styleForBorder()),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )
    
    val textWidth = solidTextLayout.size.width
}

/**
 * actually draw
 */
fun DrawScope.drawDanmakuText(
    state: DanmakuState,
    screenPosX: Float,
    screenPosY: Float,
) {
    val offset = Offset(screenPosX, screenPosY)
    // draw black bolder first, then solid text
    drawText(textLayoutResult = state.borderTextLayout, topLeft = offset)
    drawText(
        textLayoutResult = state.solidTextLayout,
        topLeft = offset,
        textDecoration = if (state.presentation.isSelf) TextDecoration.Underline else null
    )
}

@Suppress("NOTHING_TO_INLINE")
private inline fun rgbColor(value: Long): Color {
    return Color(0xFF_00_00_00L or value)
}

internal fun dummyDanmaku(
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    style: DanmakuStyle,
): DanmakuState {
    return DanmakuState(
        presentation = DanmakuPresentation(
            Danmaku(
                Uuid.randomString(),
                "dummy",
                0L, "1",
                DanmakuLocation.NORMAL, "dummy 占位 攟 の 😄", 0,
            ),
            isSelf = false
        ),
        measurer = measurer,
        baseStyle = baseStyle,
        style = style,
        enableColor = false,
        isDebug = false
    )
}
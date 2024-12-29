/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToLong

const val TAG_MEDIA_PROGRESS_INDICATOR_TEXT = "MediaProgressIndicatorText"

/**
 * "88:88:88 / 88:88:88"
 */
@Composable
fun MediaProgressIndicatorText(
    state: PlayerProgressSliderState,
    modifier: Modifier = Modifier
) {
    val text by remember(state) {
        derivedStateOf {
            val currentPositionMillis = if (state.isPreviewing) {
                state.displayPositionRatio.times(state.totalDurationMillis).roundToLong()
            } else {
                state.currentPositionMillis
            }

            renderSeconds(currentPositionMillis / 1000, state.totalDurationMillis / 1000)
        }
    }
    val reserve by remember(state) {
        derivedStateOf {
            renderSecondsReserve(state.totalDurationMillis / 1000)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(reserve, Modifier.alpha(0f)) // fix width
        Text(
            text = text,
            style = LocalTextStyle.current.copy(
                color = Color.DarkGray,
                drawStyle = Stroke(
                    miter = 3f,
                    width = 2f,
                    join = StrokeJoin.Round,
                ),
            ),
        ) // border
        Text(
            text = text,
            Modifier.testTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT),
        )
    }
}


/**
 * Returns the most wide text that [renderSeconds] may return for that [totalSecs]. This can be used to reserve space for the text.
 */
@Stable
private fun renderSecondsReserve(
    totalSecs: Long?
): String = when (totalSecs) { // 8 is usually the visually widest character
    null -> "88:88 / 88:88"
    in 0..59 -> "88:88 / 88:88"
    in 60..3599 -> "88:88 / 88:88"
    in 3600..Int.MAX_VALUE -> "88:88:88 / 88:88:88"
    else -> "88:88 / 88:88"
}

/**
 * Renders position into format like "888:88:88 / 888:88:88" (hours:minutes:seconds)
 * @see renderSecondsReserve
 */
@Stable
internal fun renderSeconds(current: Long, total: Long?): String {
    if (total == null) {
        return "00:${current.fixToString(2)} / 00:00"
    }
    return if (current < 60 && total < 60) {
        "00:${current.fixToString(2)} / 00:${total.fixToString(2)}"
    } else if (current < 3600 && total < 3600) {
        val startM = (current / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endM = (total / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startM:$startS / $endM:$endS"""
    } else {
        val startH = (current / 3600).fixToString(2)
        val startM = (current % 3600 / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endH = (total / 3600).fixToString(2)
        val endM = (total % 3600 / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startH:$startM:$startS / $endH:$endM:$endS"""
    }
}

private fun Long.fixToString(length: Int, prefix: Char = '0'): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

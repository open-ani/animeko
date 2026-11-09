/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

import androidx.compose.runtime.compositionLocalOf

data class SubtitleAppearance(val fontScale: Float = 1f) {
    val effectiveFontScale: Float get() = if (fontScale.isFinite()) fontScale.coerceIn(0.75f, 2f) else 1f
}

val LocalSubtitleAppearance = compositionLocalOf { SubtitleAppearance() }

/** 以逻辑像素表示视频边缘被系统栏和播放器控制栏占用的空间。 */
internal data class SubtitleSafeArea(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
)

internal val LocalSubtitleSafeArea = compositionLocalOf { SubtitleSafeArea() }

internal data class SubtitleRegion(val left: Double, val top: Double, val width: Double, val height: Double) {
    val bottom: Double get() = top + height
}

/** 字幕区域避开控件，最多占据视频高度的 35%，窄小视口也不越界。 */
internal fun subtitleRegion(width: Double, height: Double, safeArea: SubtitleSafeArea): SubtitleRegion {
    val w = width.coerceAtLeast(0.0)
    val h = height.coerceAtLeast(0.0)
    val left = maxOf(16.0, safeArea.left + 8.0).coerceIn(0.0, w)
    val right = maxOf(16.0, safeArea.right + 8.0).coerceIn(0.0, (w - left).coerceAtLeast(0.0))
    val top = maxOf(12.0, safeArea.top + 8.0).coerceIn(0.0, h)
    val bottom = (h - maxOf(24.0, safeArea.bottom + 12.0)).coerceIn(top, h)
    val regionHeight = minOf(h * 0.35, bottom - top)
    return SubtitleRegion(left, bottom - regionHeight, w - left - right, regionHeight)
}

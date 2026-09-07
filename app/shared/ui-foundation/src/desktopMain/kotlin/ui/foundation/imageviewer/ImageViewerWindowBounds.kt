/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.min

/** 查看器窗口的位置和大小 (dp, 与 AWT 的点坐标一致). */
internal data class ImageViewerWindowBounds(
    val position: DpOffset,
    val size: DpSize,
)

/** 窗口最小尺寸, 保证工具栏放得下. */
internal val IMAGE_VIEWER_WINDOW_MIN_SIZE: DpSize = DpSize(480.dp, 360.dp)

/** 窗口最多占屏幕可用区域的比例. */
internal const val IMAGE_VIEWER_WINDOW_MAX_SCREEN_FRACTION: Float = 0.9f

/**
 * 按图片尺寸算出查看器窗口的大小: 与图片 1:1 (像素 / [density]) 一样大, 不超过屏幕可用区域的 [maxScreenFraction],
 * 不小于 [minSize]; 超出上限时按比例缩小. 窗口在 [screen] 内居中.
 *
 * @param imageSize 解码后的图片像素尺寸.
 * @param density 窗口所在屏幕的像素密度 (Retina 为 2).
 * @param screen 屏幕可用区域 (去掉菜单栏 / 任务栏).
 */
internal fun computeImageViewerWindowBounds(
    imageSize: IntSize,
    density: Float,
    screen: DpRect,
    minSize: DpSize = IMAGE_VIEWER_WINDOW_MIN_SIZE,
    maxScreenFraction: Float = IMAGE_VIEWER_WINDOW_MAX_SCREEN_FRACTION,
): ImageViewerWindowBounds {
    val maxWidth = screen.width * maxScreenFraction
    val maxHeight = screen.height * maxScreenFraction

    val imageWidth = imageSize.width.coerceAtLeast(1) / density.coerceAtLeast(0.01f)
    val imageHeight = imageSize.height.coerceAtLeast(1) / density.coerceAtLeast(0.01f)
    val scale = min(1f, min(maxWidth.value / imageWidth, maxHeight.value / imageHeight))

    val width = (imageWidth * scale).dp
    val height = (imageHeight * scale).dp
    val size = DpSize(
        width.coerceIn(minOf(minSize.width, maxWidth), maxWidth),
        height.coerceIn(minOf(minSize.height, maxHeight), maxHeight),
    )
    val position = DpOffset(
        x = screen.left + (screen.width - size.width) / 2,
        y = screen.top + (screen.height - size.height) / 2,
    )
    return ImageViewerWindowBounds(position, size)
}

/** [window] 所在屏幕的像素密度. */
internal fun screenDensity(window: Window): Float =
    window.graphicsConfiguration.defaultTransform.scaleX.toFloat()

/**
 * [window] 所在屏幕的可用区域 (去掉菜单栏 / Dock / 任务栏), 单位为 AWT 点 (= dp).
 */
internal fun usableScreenArea(window: Window): DpRect {
    val gc = window.graphicsConfiguration
    val bounds = gc.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return DpRect(
        left = (bounds.x + insets.left).dp,
        top = (bounds.y + insets.top).dp,
        right = (bounds.x + bounds.width - insets.right).dp,
        bottom = (bounds.y + bounds.height - insets.bottom).dp,
    )
}

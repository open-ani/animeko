/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/** 截图面板停靠的角. */
enum class PlayerScreenshotPanelCorner {
    BottomLeft,
    BottomRight,
}

/** 竖屏布局 (播放器区域高大于宽) 停在右下角, 横屏布局停在左下角. */
fun playerScreenshotPanelCorner(playerWidth: Int, playerHeight: Int): PlayerScreenshotPanelCorner =
    if (playerHeight > playerWidth) PlayerScreenshotPanelCorner.BottomRight else PlayerScreenshotPanelCorner.BottomLeft

/**
 * 截图容器变换的几何: 起点是截图按原比例铺满视频区域的位置, 终点是角落的缩略图. 坐标相对播放器区域, 单位 px.
 */
class PlayerScreenshotPanelGeometry(
    val startRect: Rect,
    val thumbnailRect: Rect,
    val corner: PlayerScreenshotPanelCorner,
)

/**
 * @param playerSize 播放器区域大小
 * @param imageSize 截图像素大小; 决定长宽比
 * @param insets 面板需要避开的系统栏. 变换起点不避让, 与视频区域对齐
 * @param margin 缩略图与区域 (或系统栏) 的间距
 * @param bottomOffset 额外的底部偏移, 用于避开底部控制栏
 * @param thumbnailHeight 缩略图目标高度
 * @param maxThumbnailWidth 缩略图最大宽度, 超过时整体按比例缩小
 */
fun computePlayerScreenshotPanelGeometry(
    playerSize: IntSize,
    imageSize: IntSize,
    insets: WindowInsets,
    density: Density,
    layoutDirection: LayoutDirection,
    margin: Float,
    bottomOffset: Float,
    thumbnailHeight: Float,
    maxThumbnailWidth: Float,
): PlayerScreenshotPanelGeometry {
    val corner = playerScreenshotPanelCorner(playerSize.width, playerSize.height)
    if (playerSize.width <= 0 || playerSize.height <= 0) {
        return PlayerScreenshotPanelGeometry(Rect.Zero, Rect.Zero, corner)
    }
    val playerWidth = playerSize.width.toFloat()
    val playerHeight = playerSize.height.toFloat()
    val aspectRatio = if (imageSize.width > 0 && imageSize.height > 0) {
        imageSize.width.toFloat() / imageSize.height
    } else {
        16f / 9f
    }

    val startWidth: Float
    val startHeight: Float
    if (playerWidth / playerHeight > aspectRatio) {
        startHeight = playerHeight
        startWidth = playerHeight * aspectRatio
    } else {
        startWidth = playerWidth
        startHeight = playerWidth / aspectRatio
    }
    val startRect = Rect(
        Offset((playerWidth - startWidth) / 2, (playerHeight - startHeight) / 2),
        Size(startWidth, startHeight),
    )

    var thumbHeight = thumbnailHeight
    var thumbWidth = thumbHeight * aspectRatio
    if (thumbWidth > maxThumbnailWidth) {
        thumbWidth = maxThumbnailWidth
        thumbHeight = thumbWidth / aspectRatio
    }
    val bottom = playerHeight - insets.getBottom(density) - bottomOffset - margin
    val left = when (corner) {
        PlayerScreenshotPanelCorner.BottomLeft -> insets.getLeft(density, layoutDirection) + margin
        PlayerScreenshotPanelCorner.BottomRight ->
            playerWidth - insets.getRight(density, layoutDirection) - margin - thumbWidth
    }
    val thumbnailRect = Rect(left, bottom - thumbHeight, left + thumbWidth, bottom)
    return PlayerScreenshotPanelGeometry(startRect, thumbnailRect, corner)
}

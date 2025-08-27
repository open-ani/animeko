/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtLeast
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.vlc.VlcMediampPlayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
    aspectRatioMode: AspectRatioMode,
) {
    check(player is VlcMediampPlayer)

    val frameSizeCalculator = remember(aspectRatioMode) {
        FrameSizeCalculator(aspectRatioMode)
    }
    
    @OptIn(InternalMediampApi::class)
    Canvas(modifier) {
        val bitmap = player.surface.bitmap ?: return@Canvas
        frameSizeCalculator.calculate(
            IntSize(bitmap.width, bitmap.height),
            Size(size.width, size.height),
        )
        drawImage(
            bitmap,
            dstSize = frameSizeCalculator.dstSize,
            dstOffset = frameSizeCalculator.dstOffset,
            filterQuality = FilterQuality.High,
        )
    }
}

private class FrameSizeCalculator(private val aspectRatioMode: AspectRatioMode) {
    private var lastImageSize: IntSize = IntSize.Zero
    private var lastFrameSize: IntSize = IntSize.Zero
    private var lastAspectRatioMode: AspectRatioMode = aspectRatioMode

    var dstSize: IntSize = IntSize.Zero
        private set
    var dstOffset: IntOffset = IntOffset.Zero
        private set

    fun calculate(imageSize: IntSize, frameSize: Size) {
        val frameSizePx = IntSize(frameSize.width.roundToInt(), frameSize.height.roundToInt())

        if (lastImageSize == imageSize && lastFrameSize == frameSizePx && lastAspectRatioMode == aspectRatioMode) return

        when (aspectRatioMode) {
            AspectRatioMode.FIT -> calculateFit(imageSize, frameSize)
            AspectRatioMode.STRETCH -> calculateStretch(frameSize)
            AspectRatioMode.FILL -> calculateFill(imageSize, frameSize)
        }

        lastImageSize = imageSize
        lastFrameSize = frameSizePx
        lastAspectRatioMode = aspectRatioMode
    }

    private fun calculateFit(imageSize: IntSize, frameSize: Size) {
        val scale = min(
            frameSize.width / imageSize.width,
            frameSize.height / imageSize.height,
        )

        val scaledW = (imageSize.width * scale).roundToInt()
        val scaledH = (imageSize.height * scale).roundToInt()

        dstSize = IntSize(scaledW, scaledH)

        val offsetX = ((frameSize.width - scaledW) / 2f).fastCoerceAtLeast(0f).roundToInt()
        val offsetY = ((frameSize.height - scaledH) / 2f).fastCoerceAtLeast(0f).roundToInt()
        dstOffset = IntOffset(offsetX, offsetY)
    }

    private fun calculateStretch(frameSize: Size) {
        dstSize = IntSize(frameSize.width.roundToInt(), frameSize.height.roundToInt())
        dstOffset = IntOffset.Zero
    }

    private fun calculateFill(imageSize: IntSize, frameSize: Size) {
        val scale = max(
            frameSize.width / imageSize.width,
            frameSize.height / imageSize.height,
        )

        val scaledW = (imageSize.width * scale).roundToInt()
        val scaledH = (imageSize.height * scale).roundToInt()

        dstSize = IntSize(scaledW, scaledH)

        val offsetX = ((frameSize.width - scaledW) / 2f).roundToInt()
        val offsetY = ((frameSize.height - scaledH) / 2f).roundToInt()
        dstOffset = IntOffset(offsetX, offsetY)
    }
}

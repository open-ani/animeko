/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package me.him188.ani.app.videoplayer.videoenhancement

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import me.him188.ani.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider
import kotlin.math.roundToInt

/**
 * A single-pass radial EWA approximation of mpv's `ewa_lanczossharp` presentation chain.
 *
 * It uses mpv's Jinc radius and sharp blur, sigmoid upscaling, and 0.7 anti-ringing while
 * avoiding a second full-size intermediate texture on mobile GPUs.
 *
 * The output is the input scaled to fit [viewportSize], or the input size while it is `null`.
 * [viewportSize] may be updated from any thread while the effect is in use: the shader program
 * resizes its output on the next frame. Replacing the effect through `ExoPlayer.setVideoEffects`
 * to resize is unsafe, because Media3 blocks the playback thread when a second effect change
 * arrives before the frames of the previous one are rendered.
 */
internal class DesktopStyleLanczosSharpEffect : GlEffect {
    @Volatile
    var viewportSize: VideoDimensions? = null

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DesktopStyleLanczosSharpShaderProgram(context) { viewportSize }
}

private class DesktopStyleLanczosSharpShaderProgram(
    context: Context,
    private val viewportSize: () -> VideoDimensions?,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    val shaderSources = LanczosSharpShaderSources(context)

    private val program = try {
        GlProgram(shaderSources.vertexShader, shaderSources.fragmentShader).also {
            it.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException("Could not compile desktop-style Lanczos sharp effect", e)
    }

    private var inputWidth = 0
    private var inputHeight = 0
    private var configuredViewport: VideoDimensions? = null

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long,
    ) {
        if (viewportSize() != configuredViewport) {
            // BaseGlShaderProgram calls configure() again once its output textures are deleted.
            // A frame is only queued while the single output texture is free, so nothing
            // downstream is using it.
            try {
                super.release()
            } catch (e: VideoFrameProcessingException) {
                onError(e)
                return
            }
        }
        super.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val viewport = viewportSize()
        configuredViewport = viewport
        return lanczosSharpOutputSize(inputWidth, inputHeight, viewport)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
            program.setFloatsUniform(
                "uInputSize",
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release desktop-style Lanczos sharp effect", e)
        }
        super.release()
    }
}


/**
 * The size of [inputWidth] x [inputHeight] scaled to fit [viewport] while keeping its aspect ratio.
 */
internal fun lanczosSharpOutputSize(inputWidth: Int, inputHeight: Int, viewport: VideoDimensions?): Size {
    if (viewport == null) return Size(inputWidth, inputHeight)
    val scale = minOf(
        viewport.width.toDouble() / inputWidth,
        viewport.height.toDouble() / inputHeight,
    )
    return Size(
        (inputWidth * scale).roundToInt().coerceAtLeast(1),
        (inputHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

private class LanczosSharpShaderSources(context: Context) {
    val vertexShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/ewa_lanczossharp.vert",
    )
    val fragmentShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/ewa_lanczossharp.frag",
    )
}
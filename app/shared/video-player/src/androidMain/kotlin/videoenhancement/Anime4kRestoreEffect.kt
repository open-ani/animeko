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
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import me.him188.ani.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider

/** Official Anime4K Restore CNN S executed at the source frame size with two FP16 ping-pong textures. */
internal object Anime4kRestoreEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kRestoreShaderProgram(context)
}

private class Anime4kRestoreShaderProgram(
    context: Context,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val shaderSources = Anime4kRestoreShaderSources(context)
    private val programs: List<GlProgram> = readPassBodies(context).mapIndexed { index, body ->
        try {
            GlProgram(shaderSources.vertexShader, shaderSources.fragmentShader(index, body)).also { program ->
                program.setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                )
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not compile Anime4K Restore CNN S pass ${index + 1}", e)
        }
    }.also { require(it.size == 4) { "Anime4K Restore CNN S must contain four passes" } }

    private var width: Int = 0
    private var height: Int = 0
    private val intermediateTextures = IntArray(2)
    private val intermediateFramebuffers = IntArray(2)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (width == inputWidth && height == inputHeight && intermediateTextures[0] != 0) {
            return Size(inputWidth, inputHeight)
        }
        try {
            deleteIntermediateBuffers()
            width = inputWidth
            height = inputHeight
            for (index in intermediateTextures.indices) {
                intermediateTextures[index] = GlUtil.createTexture(
                    inputWidth,
                    inputHeight,
                    /* useHighPrecisionColorComponents = */ true,
                )
                intermediateFramebuffers[index] = GlUtil.createFboForTexture(intermediateTextures[index])
            }
            val texelSize = floatArrayOf(1f / inputWidth, 1f / inputHeight)
            programs.forEach { it.setFloatsUniform("uTexelSize", texelSize) }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not configure Anime4K Restore CNN S", e)
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val outputFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFramebuffer, 0)
        try {
            drawPass(programs[0], inputTexId, intermediateFramebuffers[0])
            drawPass(programs[1], intermediateTextures[0], intermediateFramebuffers[1])
            drawPass(programs[2], intermediateTextures[1], intermediateFramebuffers[0])
            drawPass(
                programs[3],
                intermediateTextures[0],
                outputFramebuffer[0],
                originalTexId = inputTexId,
            )
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun drawPass(
        program: GlProgram,
        inputTexId: Int,
        outputFramebuffer: Int,
        originalTexId: Int? = null,
    ) {
        GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
        originalTexId?.let {
            program.setSamplerTexIdUniform("uOriginalSampler", it, /* texUnitIndex = */ 1)
        }
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        try {
            deleteIntermediateBuffers()
            programs.forEach(GlProgram::delete)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release Anime4K Restore CNN S", e)
        }
        super.release()
    }

    private fun deleteIntermediateBuffers() {
        for (index in intermediateTextures.indices) {
            if (intermediateFramebuffers[index] != 0) {
                GlUtil.deleteFbo(intermediateFramebuffers[index])
                intermediateFramebuffers[index] = 0
            }
            if (intermediateTextures[index] != 0) {
                GlUtil.deleteTexture(intermediateTextures[index])
                intermediateTextures[index] = 0
            }
        }
    }
}

private fun readPassBodies(context: Context): List<String> {
    val source = VideoEnhancementShaderProvider.getShaderSource(context, anime4kShaderResource)
    return source.split(Regex("(?m)^//!DESC "))
        .drop(1)
        .map { section ->
            section.lineSequence()
                .drop(1)
                .filterNot { it.startsWith("//!") }
                .joinToString("\n")
        }
}

private class Anime4kRestoreShaderSources(context: Context) {
    val vertexShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/common.vert",
    )
    private val fragmentShaderTemplates = restoreFragmentShaderResources.map { resource ->
        VideoEnhancementShaderProvider.getShaderSource(context, "$exoEffectShaderDirectory/$resource")
    }

    fun fragmentShader(pass: Int, body: String): String {
        val template = fragmentShaderTemplates.getOrNull(pass)
            ?: error("Unsupported Anime4K pass: $pass")
        return VideoEnhancementShaderProvider.renderShaderTemplate(template, "PASS_BODY" to body)
    }
}

private const val anime4kShaderResource = "Anime4K_Restore_CNN_S.glsl"
private const val exoEffectShaderDirectory = "exo-effects"
private val restoreFragmentShaderResources = listOf(
    "restore_pass_1.frag",
    "restore_pass_2.frag",
    "restore_pass_3.frag",
    "restore_pass_4.frag",
)

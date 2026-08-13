/*
 * Copyright (C) 2026 OpenAni and contributors.
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

/** Official Anime4K Upscale CNN x2 S with four source-size convolution passes. */
internal object Anime4kUpscaleEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kUpscaleShaderProgram(context)
}

private class Anime4kUpscaleShaderProgram(
    context: Context,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val convolutionPrograms = readUpscalePassBodies(context).dropLast(1).mapIndexed { index, body ->
        try {
            createProgram(upscaleConvolutionFragmentShader(index, body))
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not compile Anime4K Upscale CNN x2 S pass ${index + 1}", e)
        }
    }.also { require(it.size == 4) { "Anime4K Upscale CNN x2 S must contain four convolution passes" } }

    private val depthToSpaceProgram = try {
        createProgram(depthToSpaceFragmentShader)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException("Could not compile Anime4K Upscale CNN x2 S depth-to-space pass", e)
    }

    private var width = 0
    private var height = 0
    private val intermediateTextures = IntArray(2)
    private val intermediateFramebuffers = IntArray(2)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (width != inputWidth || height != inputHeight || intermediateTextures[0] == 0) {
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
                convolutionPrograms.forEach { it.setFloatsUniform("uTexelSize", texelSize) }
                depthToSpaceProgram.setFloatsUniform(
                    "uInputSize",
                    floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
                )
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException("Could not configure Anime4K Upscale CNN x2 S", e)
            }
        }
        return Size(inputWidth * 2, inputHeight * 2)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val outputFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFramebuffer, 0)
        try {
            drawConvolutionPass(convolutionPrograms[0], inputTexId, intermediateFramebuffers[0])
            drawConvolutionPass(convolutionPrograms[1], intermediateTextures[0], intermediateFramebuffers[1])
            drawConvolutionPass(convolutionPrograms[2], intermediateTextures[1], intermediateFramebuffers[0])
            drawConvolutionPass(convolutionPrograms[3], intermediateTextures[0], intermediateFramebuffers[1])

            GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer[0], width * 2, height * 2)
            depthToSpaceProgram.use()
            depthToSpaceProgram.setSamplerTexIdUniform(
                "uFeatureSampler",
                intermediateTextures[1],
                /* texUnitIndex = */ 0,
            )
            depthToSpaceProgram.setSamplerTexIdUniform(
                "uOriginalSampler",
                inputTexId,
                /* texUnitIndex = */ 1,
            )
            depthToSpaceProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun drawConvolutionPass(program: GlProgram, inputTexId: Int, outputFramebuffer: Int) {
        GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        try {
            deleteIntermediateBuffers()
            convolutionPrograms.forEach(GlProgram::delete)
            depthToSpaceProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release Anime4K Upscale CNN x2 S", e)
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

@Throws(GlUtil.GlException::class)
private fun createProgram(fragmentShader: String): GlProgram =
    GlProgram(vertexShader, fragmentShader).also { program ->
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

private fun readUpscalePassBodies(context: Context): List<String> {
    val source = context.assets.open(anime4kUpscaleShaderAsset).bufferedReader().use { it.readText() }
    return source.split(Regex("(?m)^//!DESC "))
        .drop(1)
        .map { section ->
            section.lineSequence()
                .drop(1)
                .filterNot { it.startsWith("//!") }
                .joinToString("\n")
        }
}

private fun upscaleConvolutionFragmentShader(pass: Int, body: String): String {
    val bindings = when (pass) {
        0 -> """
            uniform sampler2D uTexSampler;
            #define MAIN_pos vTexSamplingCoord
            #define MAIN_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)
        """.trimIndent()

        1 -> """
            uniform sampler2D uTexSampler;
            #define conv2d_tf_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)
        """.trimIndent()

        2 -> """
            uniform sampler2D uTexSampler;
            #define conv2d_1_tf_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)
        """.trimIndent()

        3 -> """
            uniform sampler2D uTexSampler;
            #define conv2d_2_tf_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)
        """.trimIndent()

        else -> error("Unsupported Anime4K upscale convolution pass: $pass")
    }
    return """
        #version 100
        precision highp float;
        varying vec2 vTexSamplingCoord;
        uniform vec2 uTexelSize;
        $bindings
        $body
        void main() {
            gl_FragColor = hook();
        }
    """.trimIndent()
}

private const val depthToSpaceFragmentShader = """
    #version 100
    precision highp float;

    varying vec2 vTexSamplingCoord;
    uniform sampler2D uFeatureSampler;
    uniform sampler2D uOriginalSampler;
    uniform vec2 uInputSize;

    void main() {
        vec2 sourcePosition = vTexSamplingCoord * uInputSize;
        vec2 fraction = fract(sourcePosition);
        ivec2 subpixel = ivec2(fraction * 2.0);
        vec2 featureUv = (floor(sourcePosition) + 0.5) / uInputSize;
        vec4 features = texture2D(uFeatureSampler, featureUv);
        int component = subpixel.y * 2 + subpixel.x;
        float residual = features.r;
        if (component == 1) residual = features.g;
        if (component == 2) residual = features.b;
        if (component == 3) residual = features.a;

        vec4 original = texture2D(uOriginalSampler, vTexSamplingCoord);
        gl_FragColor = vec4(original.rgb + vec3(residual), original.a);
    }
"""

private const val vertexShader = """
    #version 100
    attribute vec4 aFramePosition;
    varying vec2 vTexSamplingCoord;
    void main() {
        gl_Position = aFramePosition;
        vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
    }
"""

private const val anime4kUpscaleShaderAsset =
    "video-enhancement/Anime4K_Upscale_CNN_x2_S.glsl"

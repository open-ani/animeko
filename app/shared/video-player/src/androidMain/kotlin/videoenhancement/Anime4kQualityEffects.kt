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

/** Official Anime4K Restore CNN M used by the quality profile. */
internal object Anime4kRestoreQualityEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kMediumShaderProgram(context, anime4kRestoreMediumAsset, upscale = false)
}

/** Official Anime4K Upscale CNN x2 M used by the quality profile. */
internal object Anime4kUpscaleQualityEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kMediumShaderProgram(context, anime4kUpscaleMediumAsset, upscale = true)
}

private class Anime4kMediumShaderProgram(
    context: Context,
    asset: String,
    private val upscale: Boolean,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val profileName = if (upscale) "Anime4K Upscale CNN x2 M" else "Anime4K Restore CNN M"
    private val shaderSources = Anime4kQualityShaderSources(context)
    private val passBodies = readPassBodies(context, asset)
    private val convolutionPrograms = passBodies.take(convolutionPassCount).mapIndexed { index, body ->
        createProgram(
            shaderSources.vertexShader,
            shaderSources.convolutionFragmentShader(index, body),
            "$profileName pass ${index + 1}",
        )
    }
    private val combineProgram = createProgram(
        shaderSources.vertexShader,
        shaderSources.featureCombineFragmentShader(passBodies[convolutionPassCount], includeOriginal = !upscale),
        "$profileName feature combine pass",
    )
    private val depthToSpaceProgram = if (upscale) {
        createProgram(
            shaderSources.vertexShader,
            shaderSources.depthToSpaceFragmentShader,
            "$profileName depth-to-space pass",
        )
    } else {
        null
    }

    private var width = 0
    private var height = 0
    private val intermediateTextures = IntArray(convolutionPassCount + if (upscale) 1 else 0)
    private val intermediateFramebuffers = IntArray(intermediateTextures.size)

    init {
        val expectedPasses = convolutionPassCount + if (upscale) 2 else 1
        require(passBodies.size == expectedPasses) {
            "$profileName must contain $expectedPasses passes, found ${passBodies.size}"
        }
    }

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
                depthToSpaceProgram?.setFloatsUniform(
                    "uInputSize",
                    floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
                )
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException("Could not configure $profileName", e)
            }
        }
        return if (upscale) Size(inputWidth * 2, inputHeight * 2) else Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val outputFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFramebuffer, 0)
        try {
            convolutionPrograms.forEachIndexed { index, program ->
                val source = if (index == 0) inputTexId else intermediateTextures[index - 1]
                drawSingleInputPass(program, source, intermediateFramebuffers[index], width, height)
            }

            val combineFramebuffer = if (upscale) {
                intermediateFramebuffers[convolutionPassCount]
            } else {
                outputFramebuffer[0]
            }
            drawFeatureCombinePass(inputTexId, combineFramebuffer)

            depthToSpaceProgram?.let { program ->
                GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer[0], width * 2, height * 2)
                program.use()
                program.setSamplerTexIdUniform(
                    "uFeatureSampler",
                    intermediateTextures[convolutionPassCount],
                    /* texUnitIndex = */ 0,
                )
                program.setSamplerTexIdUniform("uOriginalSampler", inputTexId, /* texUnitIndex = */ 1)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
                GlUtil.checkGlError()
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun drawFeatureCombinePass(inputTexId: Int, outputFramebuffer: Int) {
        GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
        combineProgram.use()
        intermediateTextures.take(convolutionPassCount).forEachIndexed { index, texture ->
            combineProgram.setSamplerTexIdUniform("uFeature$index", texture, index)
        }
        if (!upscale) {
            combineProgram.setSamplerTexIdUniform("uOriginalSampler", inputTexId, convolutionPassCount)
        }
        combineProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        try {
            deleteIntermediateBuffers()
            convolutionPrograms.forEach(GlProgram::delete)
            combineProgram.delete()
            depthToSpaceProgram?.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release $profileName", e)
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

private fun drawSingleInputPass(
    program: GlProgram,
    inputTexId: Int,
    outputFramebuffer: Int,
    width: Int,
    height: Int,
) {
    GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
    program.use()
    program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
    program.bindAttributesAndUniforms()
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
    GlUtil.checkGlError()
}

private fun readPassBodies(context: Context, asset: String): List<String> =
    VideoEnhancementShaderProvider.getShaderSource(context, asset)
        .split(Regex("(?m)^//!DESC "))
        .drop(1)
        .map { section ->
            section.lineSequence()
                .drop(1)
                .filterNot { it.startsWith("//!") }
                .joinToString("\n")
        }

private class Anime4kQualityShaderSources(context: Context) {
    val vertexShader = VideoEnhancementShaderProvider.getShaderSource(context, "$exoEffectShaderDirectory/common.vert")
    val depthToSpaceFragmentShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/depth_to_space.frag",
    )
    private val convolutionFragmentShaderTemplate = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/quality_convolution.frag",
    )
    private val featureCombineFragmentShaderTemplate = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/feature_combine.frag",
    )
    private val featureBindingTemplate = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/feature_binding.glsl",
    )
    private val originalBinding = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/original_binding.glsl",
    ).trimEnd()

    fun convolutionFragmentShader(pass: Int, body: String): String {
        val inputName = if (pass == 0) "MAIN" else featureName(pass - 1)
        return VideoEnhancementShaderProvider.renderShaderTemplate(
            convolutionFragmentShaderTemplate,
            "INPUT_NAME" to inputName,
            "PASS_BODY" to body,
        )
    }

    fun featureCombineFragmentShader(body: String, includeOriginal: Boolean): String {
        val featureBindings = List(convolutionPassCount) { index ->
            VideoEnhancementShaderProvider.renderShaderTemplate(
                featureBindingTemplate,
                "FEATURE_INDEX" to index.toString(),
                "FEATURE_NAME" to featureName(index),
            ).trimEnd()
        }.joinToString("\n")
        return VideoEnhancementShaderProvider.renderShaderTemplate(
            featureCombineFragmentShaderTemplate,
            "FEATURE_BINDINGS" to featureBindings,
            "ORIGINAL_BINDING" to if (includeOriginal) originalBinding else "",
            "PASS_BODY" to body,
        )
    }
}

private fun featureName(index: Int): String = if (index == 0) "conv2d_tf" else "conv2d_${index}_tf"

@Throws(GlUtil.GlException::class)
private fun createProgram(vertexShader: String, fragmentShader: String, passName: String): GlProgram = try {
    GlProgram(vertexShader, fragmentShader).also { program ->
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }
} catch (e: GlUtil.GlException) {
    throw VideoFrameProcessingException("Could not compile $passName", e)
}

private const val convolutionPassCount = 7
private const val anime4kRestoreMediumAsset = "Anime4K_Restore_CNN_M.glsl"
private const val anime4kUpscaleMediumAsset = "Anime4K_Upscale_CNN_x2_M.glsl"

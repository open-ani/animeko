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
    private val passBodies = readPassBodies(context, asset)
    private val convolutionPrograms = passBodies.take(convolutionPassCount).mapIndexed { index, body ->
        createProgram(convolutionFragmentShader(index, body), "$profileName pass ${index + 1}")
    }
    private val combineProgram = createProgram(
        featureCombineFragmentShader(passBodies[convolutionPassCount], includeOriginal = !upscale),
        "$profileName feature combine pass",
    )
    private val depthToSpaceProgram = if (upscale) {
        createProgram(depthToSpaceFragmentShader, "$profileName depth-to-space pass")
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
    context.assets.open(asset).bufferedReader().use { it.readText() }
        .split(Regex("(?m)^//!DESC "))
        .drop(1)
        .map { section ->
            section.lineSequence()
                .drop(1)
                .filterNot { it.startsWith("//!") }
                .joinToString("\n")
        }

private fun convolutionFragmentShader(pass: Int, body: String): String {
    val inputName = featureName(pass - 1)
    val binding = if (pass == 0) {
        "#define MAIN_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)"
    } else {
        "#define ${inputName}_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)"
    }
    return fragmentShader("""
        uniform sampler2D uTexSampler;
        uniform vec2 uTexelSize;
        $binding
        $body
    """.trimIndent())
}

private fun featureCombineFragmentShader(body: String, includeOriginal: Boolean): String {
    val bindings = buildString {
        repeat(convolutionPassCount) { index ->
            val feature = featureName(index)
            appendLine("uniform sampler2D uFeature$index;")
            appendLine("#define ${feature}_pos vTexSamplingCoord")
            appendLine("#define ${feature}_tex(position) texture2D(uFeature$index, position)")
        }
        if (includeOriginal) {
            appendLine("uniform sampler2D uOriginalSampler;")
            appendLine("#define MAIN_pos vTexSamplingCoord")
            appendLine("#define MAIN_tex(position) texture2D(uOriginalSampler, position)")
        }
    }
    return fragmentShader("$bindings\n$body")
}

private val depthToSpaceFragmentShader = fragmentShader("""
    uniform sampler2D uFeatureSampler;
    uniform sampler2D uOriginalSampler;
    vec4 hook() {
        vec2 sourcePosition = vTexSamplingCoord * uInputSize;
        vec2 fraction = fract(sourcePosition);
        vec2 featureUv = (floor(sourcePosition) + 0.5) / uInputSize;
        vec4 features = texture2D(uFeatureSampler, featureUv);
        int component = int(fraction.y * 2.0) * 2 + int(fraction.x * 2.0);
        float residual = features.r;
        if (component == 1) residual = features.g;
        if (component == 2) residual = features.b;
        if (component == 3) residual = features.a;
        vec4 original = texture2D(uOriginalSampler, vTexSamplingCoord);
        return vec4(original.rgb + vec3(residual), original.a);
    }
""".trimIndent(), includeInputSize = true)

private fun fragmentShader(body: String, includeInputSize: Boolean = false): String = """
    #version 100
    precision highp float;
    varying vec2 vTexSamplingCoord;
    ${if (includeInputSize) "uniform vec2 uInputSize;" else ""}
    $body
    void main() {
        gl_FragColor = hook();
    }
""".trimIndent()

private fun featureName(index: Int): String = if (index == 0) "conv2d_tf" else "conv2d_${index}_tf"

@Throws(GlUtil.GlException::class)
private fun createProgram(fragmentShader: String, passName: String): GlProgram = try {
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
private const val anime4kRestoreMediumAsset = "video-enhancement/Anime4K_Restore_CNN_M.glsl"
private const val anime4kUpscaleMediumAsset = "video-enhancement/Anime4K_Upscale_CNN_x2_M.glsl"

private const val vertexShader = """
    #version 100
    attribute vec4 aFramePosition;
    varying vec2 vTexSamplingCoord;
    void main() {
        gl_Position = aFramePosition;
        vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
    }
"""

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
import kotlin.math.roundToInt

/**
 * A single-pass radial EWA approximation of mpv's `ewa_lanczossharp` presentation chain.
 *
 * It uses mpv's Jinc radius and sharp blur, sigmoid upscaling, and 0.7 anti-ringing while
 * avoiding a second full-size intermediate texture on mobile GPUs.
 */
internal class DesktopStyleLanczosSharpEffect(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DesktopStyleLanczosSharpShaderProgram(viewportWidth, viewportHeight)
}

private class DesktopStyleLanczosSharpShaderProgram(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val program = try {
        GlProgram(vertexShader, fragmentShader).also {
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

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val scale = minOf(
            viewportWidth.toDouble() / inputWidth,
            viewportHeight.toDouble() / inputHeight,
        )
        return Size(
            (inputWidth * scale).roundToInt().coerceAtLeast(1),
            (inputHeight * scale).roundToInt().coerceAtLeast(1),
        )
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

private const val vertexShader = """
    #version 100
    attribute vec4 aFramePosition;
    varying vec2 vTexSamplingCoord;
    void main() {
        gl_Position = aFramePosition;
        vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
    }
"""

private const val fragmentShader = """
    #version 100
    precision highp float;

    varying vec2 vTexSamplingCoord;
    uniform sampler2D uTexSampler;
    uniform vec2 uInputSize;

    const float SIGMOID_CENTER = 0.75;
    const float SIGMOID_SLOPE = 6.5;
    const float ANTI_RINGING = 0.70;
    const float EWA_RADIUS_SQUARED = 10.097135144970707;

    // Degree-9 Chebyshev approximation of mpv's windowed-Jinc ewa_lanczossharp weight.
    // Radius = 3.2383154841662362 * blur 0.9812505837223707.
    float ewaLanczosSharpWeight(float distanceSquared) {
        if (distanceSquared >= EWA_RADIUS_SQUARED) return 0.0;
        float t = 2.0 * distanceSquared / EWA_RADIUS_SQUARED - 1.0;
        float b1 = 0.0;
        float b2 = 0.0;
        float b0;
        b0 = 2.0 * t * b1 - b2 - 0.000119375901424; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 + 0.000795447922179; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 - 0.00445990281424; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 + 0.0188623358477; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 - 0.0584877567769; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 + 0.127899271054; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 - 0.195814548882; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 + 0.228753586124; b2 = b1; b1 = b0;
        b0 = 2.0 * t * b1 - b2 - 0.241137517405; b2 = b1; b1 = b0;
        return t * b1 - b2 + 0.123659839061;
    }

    vec3 sigmoid(vec3 value) {
        vec3 low = 1.0 / (1.0 + exp(vec3(SIGMOID_SLOPE * SIGMOID_CENTER)));
        vec3 high = 1.0 / (1.0 + exp(vec3(SIGMOID_SLOPE * (SIGMOID_CENTER - 1.0))));
        vec3 mapped = 1.0 / (1.0 + exp(SIGMOID_SLOPE * (vec3(SIGMOID_CENTER) - value)));
        return (mapped - low) / (high - low);
    }

    vec3 inverseSigmoid(vec3 value) {
        vec3 low = 1.0 / (1.0 + exp(vec3(SIGMOID_SLOPE * SIGMOID_CENTER)));
        vec3 high = 1.0 / (1.0 + exp(vec3(SIGMOID_SLOPE * (SIGMOID_CENTER - 1.0))));
        vec3 mapped = clamp(value, 0.0, 1.0) * (high - low) + low;
        return vec3(SIGMOID_CENTER) - log(1.0 / mapped - 1.0) / SIGMOID_SLOPE;
    }

    vec3 sampleSigmoid(vec2 pixel) {
        vec2 uv = (pixel + 0.5) / uInputSize;
        return sigmoid(texture2D(uTexSampler, uv).rgb);
    }

    void main() {
        vec2 sourcePosition = vTexSamplingCoord * uInputSize - 0.5;
        vec2 base = floor(sourcePosition);
        vec2 fraction = sourcePosition - base;
        vec3 accumulated = vec3(0.0);
        float weightSum = 0.0;

        // The 3.1776 source-pixel radius fits in an 8x8 footprint. The radial cutoff leaves
        // roughly 32 contributing samples for a typical subpixel position.
        for (int y = -3; y <= 4; y++) {
            for (int x = -3; x <= 4; x++) {
                vec2 offset = vec2(float(x), float(y));
                vec2 delta = offset - fraction;
                float distanceSquared = dot(delta, delta);
                if (distanceSquared < EWA_RADIUS_SQUARED) {
                    float weight = ewaLanczosSharpWeight(distanceSquared);
                    accumulated += sampleSigmoid(base + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        vec3 filtered = accumulated / max(weightSum, 0.00001);
        vec3 sample00 = sampleSigmoid(base);
        vec3 sample10 = sampleSigmoid(base + vec2(1.0, 0.0));
        vec3 sample01 = sampleSigmoid(base + vec2(0.0, 1.0));
        vec3 sample11 = sampleSigmoid(base + vec2(1.0, 1.0));
        vec3 localMin = min(min(sample00, sample10), min(sample01, sample11));
        vec3 localMax = max(max(sample00, sample10), max(sample01, sample11));
        filtered = mix(filtered, clamp(filtered, localMin, localMax), ANTI_RINGING);

        float alpha = texture2D(uTexSampler, vTexSamplingCoord).a;
        gl_FragColor = vec4(inverseSigmoid(filtered), alpha);
    }
"""

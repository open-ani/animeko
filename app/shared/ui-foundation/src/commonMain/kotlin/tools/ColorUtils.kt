/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package me.him188.ani.app.tools


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ColorUtils {
    private const val XYZ_WHITE_REFERENCE_X = 95.047
    private const val XYZ_WHITE_REFERENCE_Y = 100.0
    private const val XYZ_WHITE_REFERENCE_Z = 108.883
    private const val XYZ_EPSILON = 0.008856
    private const val XYZ_KAPPA = 903.3
    private const val MIN_ALPHA_SEARCH_MAX_ITERATIONS = 10
    private const val ALPHA_EPSILON = 1e-6f

    /**
     * 等价于 AndroidX ColorUtils.compositeColors(foreground, background)
     *
     * 含义：把 foreground 作为前景色叠到 background 上。
     *
     * 注意：这里统一转到 sRGB 计算，行为更接近原来的 @ColorInt 版本。
     */
    fun compositeColor(foreground: Color, background: Color): Color {
        return compositeColors(foreground, background)
    }

    fun compositeColors(foreground: Color, background: Color): Color {
        val fg = foreground.convert(ColorSpaces.Srgb)
        val bg = background.convert(ColorSpaces.Srgb)

        val fgA = fg.alpha
        val bgA = bg.alpha
        val outA = fgA + bgA * (1f - fgA)

        if (outA <= 0f) {
            return Color(red = 0f, green = 0f, blue = 0f, alpha = 0f)
        }

        val r = (fg.red * fgA + bg.red * bgA * (1f - fgA)) / outA
        val g = (fg.green * fgA + bg.green * bgA * (1f - fgA)) / outA
        val b = (fg.blue * fgA + bg.blue * bgA * (1f - fgA)) / outA

        return Color(
            red = r.coerceIn(0f, 1f),
            green = g.coerceIn(0f, 1f),
            blue = b.coerceIn(0f, 1f),
            alpha = outA.coerceIn(0f, 1f),
            colorSpace = ColorSpaces.Srgb,
        )
    }

    /**
     * 等价于 AndroidX ColorUtils.blendARGB(color1, color2, ratio)
     *
     * 含义：在 color1 和 color2 之间做线性插值。
     * ratio = 0f 得到 color1
     * ratio = 1f 得到 color2
     */
    fun blendColor(color1: Color, color2: Color, ratio: Float): Color {
        return blendArgb(color1, color2, ratio)
    }

    fun blendArgb(color1: Color, color2: Color, ratio: Float): Color {
        require(ratio in 0f..1f) {
            "ratio must be between 0 and 1."
        }

        val c1 = color1.convert(ColorSpaces.Srgb)
        val c2 = color2.convert(ColorSpaces.Srgb)

        val inverseRatio = 1f - ratio

        return Color(
            red = c1.red * inverseRatio + c2.red * ratio,
            green = c1.green * inverseRatio + c2.green * ratio,
            blue = c1.blue * inverseRatio + c2.blue * ratio,
            alpha = c1.alpha * inverseRatio + c2.alpha * ratio,
            colorSpace = ColorSpaces.Srgb,
        )
    }

    fun calculateLuminance(color: Color): Double {
        val result = DoubleArray(3)
        colorToXyz(color, result)
        return result[1] / 100.0
    }

    fun calculateContrast(foreground: Color, background: Color): Double {
        require(background.alpha >= 1f - ALPHA_EPSILON) {
            "background can not be translucent: alpha=${background.alpha}"
        }

        val effectiveForeground =
            if (foreground.alpha < 1f - ALPHA_EPSILON) {
                compositeColors(foreground, background)
            } else {
                foreground
            }

        val luminance1 = calculateLuminance(effectiveForeground) + 0.05
        val luminance2 = calculateLuminance(background) + 0.05

        return max(luminance1, luminance2) / min(luminance1, luminance2)
    }

    /**
     * 返回满足最小对比度的最小 alpha。
     *
     * 原 AndroidX 版本返回 0..255 的 Int，失败返回 -1。
     * Compose Color 的 alpha 是 0f..1f，所以这里返回 Float?：
     * - null 表示即使 alpha = 1f 也达不到 minContrastRatio
     * - 非 null 表示 0f..1f 的 alpha
     */
    fun calculateMinimumAlpha(
        foreground: Color,
        background: Color,
        minContrastRatio: Float
    ): Float? {
        require(background.alpha >= 1f - ALPHA_EPSILON) {
            "background can not be translucent: alpha=${background.alpha}"
        }

        val testForeground = setAlphaComponent(foreground, 1f)
        val testRatio = calculateContrast(testForeground, background)

        if (testRatio < minContrastRatio) {
            return null
        }

        var numIterations = 0
        var minAlpha = 0
        var maxAlpha = 255

        while (
            numIterations <= MIN_ALPHA_SEARCH_MAX_ITERATIONS &&
            maxAlpha - minAlpha > 1
        ) {
            val testAlpha = (minAlpha + maxAlpha) / 2
            val testColor = setAlphaComponent(foreground, testAlpha / 255f)
            val ratio = calculateContrast(testColor, background)

            if (ratio < minContrastRatio) {
                minAlpha = testAlpha
            } else {
                maxAlpha = testAlpha
            }

            numIterations++
        }

        return maxAlpha / 255f
    }

    fun rgbToHsl(r: Int, g: Int, b: Int, outHsl: FloatArray) {
        require(outHsl.size == 3) {
            "outHsl must have a length of 3."
        }

        val rf = r.coerceIn(0, 255) / 255f
        val gf = g.coerceIn(0, 255) / 255f
        val bf = b.coerceIn(0, 255) / 255f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val deltaMaxMin = max - min

        var h: Float
        val s: Float
        val l = (max + min) / 2f

        if (max == min) {
            h = 0f
            s = 0f
        } else {
            h = when (max) {
                rf -> ((gf - bf) / deltaMaxMin) % 6f
                gf -> ((bf - rf) / deltaMaxMin) + 2f
                else -> ((rf - gf) / deltaMaxMin) + 4f
            }

            s = deltaMaxMin / (1f - abs(2f * l - 1f))
        }

        h = (h * 60f) % 360f
        if (h < 0f) {
            h += 360f
        }

        outHsl[0] = h.coerceIn(0f, 360f)
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
    }

    fun colorToHsl(color: Color, outHsl: FloatArray) {
        val rgb = colorToRgb255(color)
        rgbToHsl(rgb.r, rgb.g, rgb.b, outHsl)
    }

    fun hslToColor(hsl: FloatArray): Color {
        require(hsl.size == 3) {
            "hsl must have a length of 3."
        }

        val h = (((hsl[0] % 360f) + 360f) % 360f)
        val s = hsl[1].coerceIn(0f, 1f)
        val l = hsl[2].coerceIn(0f, 1f)

        val c = (1f - abs(2f * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f) % 2f - 1f))

        val hueSegment = (h / 60f).toInt()

        val rf: Float
        val gf: Float
        val bf: Float

        when (hueSegment) {
            0 -> {
                rf = c + m
                gf = x + m
                bf = m
            }

            1 -> {
                rf = x + m
                gf = c + m
                bf = m
            }

            2 -> {
                rf = m
                gf = c + m
                bf = x + m
            }

            3 -> {
                rf = m
                gf = x + m
                bf = c + m
            }

            4 -> {
                rf = x + m
                gf = m
                bf = c + m
            }

            else -> {
                rf = c + m
                gf = m
                bf = x + m
            }
        }

        return Color(
            red = rf.coerceIn(0f, 1f),
            green = gf.coerceIn(0f, 1f),
            blue = bf.coerceIn(0f, 1f),
            alpha = 1f,
            colorSpace = ColorSpaces.Srgb,
        )
    }

    fun setAlphaComponent(color: Color, alpha: Float): Color {
        require(alpha in 0f..1f) {
            "alpha must be between 0 and 1."
        }
        return color.copy(alpha = alpha)
    }

    fun colorToLab(color: Color, outLab: DoubleArray) {
        val rgb = colorToRgb255(color)
        rgbToLab(rgb.r, rgb.g, rgb.b, outLab)
    }

    fun rgbToLab(r: Int, g: Int, b: Int, outLab: DoubleArray) {
        rgbToXyz(r, g, b, outLab)
        xyzToLab(outLab[0], outLab[1], outLab[2], outLab)
    }

    fun colorToXyz(color: Color, outXyz: DoubleArray) {
        val rgb = colorToRgb255(color)
        rgbToXyz(rgb.r, rgb.g, rgb.b, outXyz)
    }

    fun rgbToXyz(r: Int, g: Int, b: Int, outXyz: DoubleArray) {
        require(outXyz.size == 3) {
            "outXyz must have a length of 3."
        }

        var sr = r.coerceIn(0, 255) / 255.0
        sr = if (sr < 0.04045) {
            sr / 12.92
        } else {
            ((sr + 0.055) / 1.055).pow(2.4)
        }

        var sg = g.coerceIn(0, 255) / 255.0
        sg = if (sg < 0.04045) {
            sg / 12.92
        } else {
            ((sg + 0.055) / 1.055).pow(2.4)
        }

        var sb = b.coerceIn(0, 255) / 255.0
        sb = if (sb < 0.04045) {
            sb / 12.92
        } else {
            ((sb + 0.055) / 1.055).pow(2.4)
        }

        outXyz[0] = 100.0 * (sr * 0.4124 + sg * 0.3576 + sb * 0.1805)
        outXyz[1] = 100.0 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722)
        outXyz[2] = 100.0 * (sr * 0.0193 + sg * 0.1192 + sb * 0.9505)
    }

    fun xyzToLab(x: Double, y: Double, z: Double, outLab: DoubleArray) {
        require(outLab.size == 3) {
            "outLab must have a length of 3."
        }

        val px = pivotXyzComponent(x / XYZ_WHITE_REFERENCE_X)
        val py = pivotXyzComponent(y / XYZ_WHITE_REFERENCE_Y)
        val pz = pivotXyzComponent(z / XYZ_WHITE_REFERENCE_Z)

        outLab[0] = max(0.0, 116.0 * py - 16.0)
        outLab[1] = 500.0 * (px - py)
        outLab[2] = 200.0 * (py - pz)
    }

    fun labToXyz(l: Double, a: Double, b: Double, outXyz: DoubleArray) {
        require(outXyz.size == 3) {
            "outXyz must have a length of 3."
        }

        val fy = (l + 16.0) / 116.0
        val fx = a / 500.0 + fy
        val fz = fy - b / 200.0

        var tmp = fx.pow(3.0)
        val xr = if (tmp > XYZ_EPSILON) {
            tmp
        } else {
            (116.0 * fx - 16.0) / XYZ_KAPPA
        }

        val yr = if (l > 7.9996247999999985) {
            fy.pow(3.0)
        } else {
            l / XYZ_KAPPA
        }

        tmp = fz.pow(3.0)
        val zr = if (tmp > XYZ_EPSILON) {
            tmp
        } else {
            (116.0 * fz - 16.0) / XYZ_KAPPA
        }

        outXyz[0] = xr * XYZ_WHITE_REFERENCE_X
        outXyz[1] = yr * XYZ_WHITE_REFERENCE_Y
        outXyz[2] = zr * XYZ_WHITE_REFERENCE_Z
    }

    fun xyzToColor(x: Double, y: Double, z: Double): Color {
        var r = (x * 3.2406 + y * -1.5372 + z * -0.4986) / 100.0
        var g = (x * -0.9689 + y * 1.8758 + z * 0.0415) / 100.0
        var b = (x * 0.0557 + y * -0.2040 + z * 1.0570) / 100.0

        r = if (r > 0.0031308) {
            1.055 * r.pow(1.0 / 2.4) - 0.055
        } else {
            12.92 * r
        }

        g = if (g > 0.0031308) {
            1.055 * g.pow(1.0 / 2.4) - 0.055
        } else {
            12.92 * g
        }

        b = if (b > 0.0031308) {
            1.055 * b.pow(1.0 / 2.4) - 0.055
        } else {
            12.92 * b
        }

        val ri = (r * 255.0).roundToInt().coerceIn(0, 255)
        val gi = (g * 255.0).roundToInt().coerceIn(0, 255)
        val bi = (b * 255.0).roundToInt().coerceIn(0, 255)

        return Color(
            red = ri / 255f,
            green = gi / 255f,
            blue = bi / 255f,
            alpha = 1f,
            colorSpace = ColorSpaces.Srgb,
        )
    }

    fun labToColor(l: Double, a: Double, b: Double): Color {
        val result = DoubleArray(3)
        labToXyz(l, a, b, result)
        return xyzToColor(result[0], result[1], result[2])
    }

    fun distanceEuclidean(labX: DoubleArray, labY: DoubleArray): Double {
        require(labX.size == 3 && labY.size == 3) {
            "labX and labY must both have a length of 3."
        }

        return sqrt(
            (labX[0] - labY[0]).pow(2.0) +
                    (labX[1] - labY[1]).pow(2.0) +
                    (labX[2] - labY[2]).pow(2.0),
        )
    }

    fun blendHsl(
        hsl1: FloatArray,
        hsl2: FloatArray,
        ratio: Float,
        outResult: FloatArray
    ) {
        require(hsl1.size == 3 && hsl2.size == 3 && outResult.size == 3) {
            "hsl1, hsl2 and outResult must have a length of 3."
        }
        require(ratio in 0f..1f) {
            "ratio must be between 0 and 1."
        }

        val inverseRatio = 1f - ratio

        outResult[0] = circularInterpolate(hsl1[0], hsl2[0], ratio)
        outResult[1] = hsl1[1] * inverseRatio + hsl2[1] * ratio
        outResult[2] = hsl1[2] * inverseRatio + hsl2[2] * ratio
    }

    fun blendLab(
        lab1: DoubleArray,
        lab2: DoubleArray,
        ratio: Double,
        outResult: DoubleArray
    ) {
        require(lab1.size == 3 && lab2.size == 3 && outResult.size == 3) {
            "lab1, lab2 and outResult must have a length of 3."
        }
        require(ratio in 0.0..1.0) {
            "ratio must be between 0 and 1."
        }

        val inverseRatio = 1.0 - ratio

        outResult[0] = lab1[0] * inverseRatio + lab2[0] * ratio
        outResult[1] = lab1[1] * inverseRatio + lab2[1] * ratio
        outResult[2] = lab1[2] * inverseRatio + lab2[2] * ratio
    }

    private fun circularInterpolate(aInput: Float, bInput: Float, fraction: Float): Float {
        var a = aInput
        var b = bInput

        if (abs(b - a) > 180f) {
            if (b > a) {
                a += 360f
            } else {
                b += 360f
            }
        }

        val result = (a + (b - a) * fraction) % 360f
        return if (result < 0f) result + 360f else result
    }

    private fun pivotXyzComponent(component: Double): Double {
        return if (component > XYZ_EPSILON) {
            component.pow(1.0 / 3.0)
        } else {
            (XYZ_KAPPA * component + 16.0) / 116.0
        }
    }

    private fun colorToRgb255(color: Color): Rgb255 {
        val c = color.convert(ColorSpaces.Srgb)

        return Rgb255(
            r = (c.red.coerceIn(0f, 1f) * 255f).roundToInt(),
            g = (c.green.coerceIn(0f, 1f) * 255f).roundToInt(),
            b = (c.blue.coerceIn(0f, 1f) * 255f).roundToInt(),
        )
    }

    private data class Rgb255(
        val r: Int,
        val g: Int,
        val b: Int
    )
}
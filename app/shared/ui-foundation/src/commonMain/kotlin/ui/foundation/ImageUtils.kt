/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import kotlin.math.pow

expect fun Image.toComposeImageBitmap(): ImageBitmap

expect fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap

/**
 * Determine the main color in a [ImageBitmap].
 *
 * @receiver The [ImageBitmap] to extract colors from.
 * @return The main color.
 */
fun ImageBitmap.themeColor(): Color {
    val width = this.width
    val height = this.height

    val pixels = IntArray(width * height)
    this.readPixels(
        buffer = pixels,
        startX = 0,
        startY = 0,
        width = width,
        height = height,
        bufferOffset = 0,
        stride = width,
    )

    // 将像素转换为带权重的 LAB 点
    val points = mutableListOf<WeightedLabPoint>()
    val centerX = width / 2.0
    val centerY = height / 2.0
    val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            if ((pixel shr 24) and 0xFF <= 128) continue

            // 计算到图片中心的距离作为权重
            val distanceFromCenter = kotlin.math.sqrt(
                (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY),
            )
            val weight = 1.0 - (distanceFromCenter / maxDistance) * 0.5 // 中心权重最高为1，边缘为0.5

            // 转换 RGB 到 LAB
            val rgb = RGBColor(
                r = (pixel shr 16) and 0xFF,
                g = (pixel shr 8) and 0xFF,
                b = pixel and 0xFF,
            )
            val lab = rgb.toLab()
            points.add(WeightedLabPoint(lab, weight))
        }
    }

    if (points.isEmpty()) return Color.Black

    // 使用 K-means++ 进行聚类
    val k = 5
    val clusters = kMeansPlusPlus(points, k, maxIterations = 15)

    // 评估每个聚类的重要性（考虑点数量和权重）
    val dominantCluster = clusters.maxByOrNull { cluster ->
        cluster.points.sumOf { it.weight } * cluster.points.size
    } ?: return Color.Black

    // 将主导聚类的中心点转回 RGB 并返回
    val (r, g, b) = dominantCluster.centroid.toRGB()
    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
    )
}

private data class RGBColor(val r: Int, val g: Int, val b: Int) {
    fun toLab(): LabColor {
        // RGB to XYZ
        var r = this.r / 255.0
        var g = this.g / 255.0
        var b = this.b / 255.0

        // Gamma correction
        r = if (r > 0.04045) ((r + 0.055) / 1.055).pow(2.4) else r / 12.92
        g = if (g > 0.04045) ((g + 0.055) / 1.055).pow(2.4) else g / 12.92
        b = if (b > 0.04045) ((b + 0.055) / 1.055).pow(2.4) else b / 12.92

        r *= 100
        g *= 100
        b *= 100

        val x = r * 0.4124 + g * 0.3576 + b * 0.1805
        val y = r * 0.2126 + g * 0.7152 + b * 0.0722
        val z = r * 0.0193 + g * 0.1192 + b * 0.9505

        // XYZ to Lab
        return LabColor.fromXYZ(x, y, z)
    }
}

private data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distanceTo(other: LabColor): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return kotlin.math.sqrt(dl * dl + da * da + db * db)
    }

    fun toRGB(): RGBColor {
        // Lab to XYZ
        val y = (l + 16) / 116
        val x = a / 500 + y
        val z = y - b / 200

        val x3 = x * x * x
        val y3 = y * y * y
        val z3 = z * z * z

        val xr = if (x3 > 0.008856) x3 else (x - 16.0 / 116) / 7.787
        val yr = if (y3 > 0.008856) y3 else (y - 16.0 / 116) / 7.787
        val zr = if (z3 > 0.008856) z3 else (z - 16.0 / 116) / 7.787

        // XYZ to RGB
        var r = xr * 3.2406 - yr * 1.5372 - zr * 0.4986
        var g = -xr * 0.9689 + yr * 1.8758 + zr * 0.0415
        var b = xr * 0.0557 - yr * 0.2040 + zr * 1.0570

        // Gamma correction
        r = if (r > 0.0031308) 1.055 * r.pow(1 / 2.4) - 0.055 else 12.92 * r
        g = if (g > 0.0031308) 1.055 * g.pow(1 / 2.4) - 0.055 else 12.92 * g
        b = if (b > 0.0031308) 1.055 * b.pow(1 / 2.4) - 0.055 else 12.92 * b

        return RGBColor(
            r = (r * 255).toInt().coerceIn(0, 255),
            g = (g * 255).toInt().coerceIn(0, 255),
            b = (b * 255).toInt().coerceIn(0, 255),
        )
    }

    companion object {
        fun fromXYZ(x: Double, y: Double, z: Double): LabColor {
            val xr = x / 95.047
            val yr = y / 100.0
            val zr = z / 108.883

            val fx = if (xr > 0.008856) xr.pow(1.0 / 3) else (7.787 * xr) + 16.0 / 116
            val fy = if (yr > 0.008856) yr.pow(1.0 / 3) else (7.787 * yr) + 16.0 / 116
            val fz = if (zr > 0.008856) zr.pow(1.0 / 3) else (7.787 * zr) + 16.0 / 116

            val l = (116 * fy) - 16
            val a = 500 * (fx - fy)
            val b = 200 * (fy - fz)

            return LabColor(l, a, b)
        }
    }
}

private data class WeightedLabPoint(
    val lab: LabColor,
    val weight: Double
)

private data class Cluster(
    var centroid: LabColor,
    val points: MutableList<WeightedLabPoint> = mutableListOf()
)

private fun kMeansPlusPlus(
    points: List<WeightedLabPoint>,
    k: Int,
    maxIterations: Int
): List<Cluster> {
    // K-means++ 初始化
    val centroids = mutableListOf<LabColor>()
    val random = kotlin.random.Random.Default

    // 随机选择第一个中心点
    centroids.add(points.random().lab)

    // 选择剩余的中心点
    while (centroids.size < k) {
        var totalDistance = 0.0
        val distances = points.map { point ->
            val minDistance = centroids.minOf { centroid ->
                point.lab.distanceTo(centroid)
            }
            totalDistance += minDistance * minDistance * point.weight
            totalDistance
        }

        // 按距离的平方选择下一个中心点
        val threshold = random.nextDouble() * totalDistance
        val nextCentroid = points[distances.indexOfFirst { it >= threshold }].lab
        centroids.add(nextCentroid)
    }

    val clusters = centroids.map { Cluster(it) }

    // K-means 迭代
    var iteration = 0
    var changed: Boolean

    do {
        clusters.forEach { it.points.clear() }

        // 分配点到最近的聚类
        for (point in points) {
            val nearestCluster = clusters.minByOrNull {
                point.lab.distanceTo(it.centroid)
            } ?: continue
            nearestCluster.points.add(point)
        }

        changed = false

        // 更新聚类中心
        for (cluster in clusters) {
            if (cluster.points.isEmpty()) continue

            // 计算加权平均值作为新的中心点
            val totalWeight = cluster.points.sumOf { it.weight }
            val newCentroid = LabColor(
                l = cluster.points.sumOf { it.lab.l * it.weight } / totalWeight,
                a = cluster.points.sumOf { it.lab.a * it.weight } / totalWeight,
                b = cluster.points.sumOf { it.lab.b * it.weight } / totalWeight,
            )

            if (newCentroid.distanceTo(cluster.centroid) > 0.1) {
                changed = true
                cluster.centroid = newCentroid
            }
        }

        iteration++
    } while (changed && iteration < maxIterations)

    return clusters
}

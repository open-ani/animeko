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

expect fun Image.toComposeImageBitmap(): ImageBitmap

expect fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap

expect fun ImageBitmap.crop(
    x: Int,
    y: Int,
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

    // 将像素转换为带权重的 RGB 点
    val points = mutableListOf<WeightedRGBPoint>()
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
            val weight = 1.0 - (distanceFromCenter / maxDistance) * 0.5 // 中心权重最高为 1，边缘为 0.5

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            points.add(WeightedRGBPoint(RGBColor(r, g, b), weight))
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

    // 返回主导聚类的中心点 RGB
    val (r, g, b) = dominantCluster.centroid
    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
    )
}

private data class RGBColor(val r: Int, val g: Int, val b: Int) {
    fun distanceTo(other: RGBColor): Double {
        val dr = r - other.r
        val dg = g - other.g
        val db = b - other.b
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}

private data class WeightedRGBPoint(
    val rgb: RGBColor,
    val weight: Double
)

private data class Cluster(
    var centroid: RGBColor,
    val points: MutableList<WeightedRGBPoint> = mutableListOf()
)

private fun kMeansPlusPlus(
    points: List<WeightedRGBPoint>,
    k: Int,
    maxIterations: Int
): List<Cluster> {
    // K-means++ 初始化
    val centroids = mutableListOf<RGBColor>()
    val random = kotlin.random.Random.Default

    // 随机选择第一个中心点
    centroids.add(points.random().rgb)

    // 选择剩余的中心点
    while (centroids.size < k) {
        var totalDistance = 0.0
        val distances = points.map { point ->
            val minDistance = centroids.minOf { centroid ->
                point.rgb.distanceTo(centroid)
            }
            totalDistance += minDistance * minDistance * point.weight
            totalDistance
        }

        // 按距离的平方选择下一个中心点
        val threshold = random.nextDouble() * totalDistance
        val nextCentroid = points[distances.indexOfFirst { it >= threshold }].rgb
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
                point.rgb.distanceTo(it.centroid)
            } ?: continue
            nearestCluster.points.add(point)
        }

        changed = false

        // 更新聚类中心
        for (cluster in clusters) {
            if (cluster.points.isEmpty()) continue

            // 计算加权平均值作为新的中心点
            val totalWeight = cluster.points.sumOf { it.weight }
            val newCentroid = RGBColor(
                r = (cluster.points.sumOf { it.rgb.r * it.weight } / totalWeight).toInt(),
                g = (cluster.points.sumOf { it.rgb.g * it.weight } / totalWeight).toInt(),
                b = (cluster.points.sumOf { it.rgb.b * it.weight } / totalWeight).toInt(),
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

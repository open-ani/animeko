/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.min

/**
 * 截图面板的整体轮廓, 一条闭合路径.
 *
 * A 区域 [regionA] 是圆角矩形 (圆角 [cornerRadius]); B 区域 [regionB] 是与 A 底边对齐、从 A 的下角向外伸出的药丸:
 * 两者的底边连成一条直线, B 的伸出端是半圆, B 朝 A 的那一端藏在 A 里, B 的上边与 A 的侧边之间用半径 [neckRadius]
 * 的内凹圆角过渡, 所以 A 和 B 之间没有折角, 读起来是一块面板.
 *
 * @param lobeExtendsRight B 向右伸出 (面板停在左下角); 为 `false` 时 B 向左伸出, 整个轮廓是向右情况的水平镜像.
 * 两个半径都会被限制在不互相冲突的范围内.
 */
internal fun screenshotPanelOutline(
    regionA: Rect,
    regionB: Rect,
    cornerRadius: Float,
    neckRadius: Float,
    lobeExtendsRight: Boolean,
): Path {
    if (lobeExtendsRight) return outlineWithLobeExtendingRight(regionA, regionB, cornerRadius, neckRadius)

    // 向左伸出: 先按向右画, 再整体水平镜像
    val axis = min(regionA.left, regionB.left) + max(regionA.right, regionB.right)
    fun Rect.mirrored() = Rect(axis - right, top, axis - left, bottom)
    val path = outlineWithLobeExtendingRight(regionA.mirrored(), regionB.mirrored(), cornerRadius, neckRadius)
    path.transform(
        Matrix().apply {
            translate(axis, 0f)
            scale(-1f, 1f)
        },
    )
    return path
}

private fun outlineWithLobeExtendingRight(a: Rect, b: Rect, cornerRadius: Float, neckRadius: Float): Path {
    val rA = min(cornerRadius, min(a.width / 2f, a.height / 2f)).coerceAtLeast(0f)
    val rB = min(b.height / 2f, b.width / 2f).coerceAtLeast(0f)
    // 内凹圆角不能越过 A 的右上角, 也不能超过 B 伸出部分可用的宽度
    val rJ = min(neckRadius, min(b.top - (a.top + rA), (b.right - rB) - a.right)).coerceAtLeast(0f)

    return Path().apply {
        // 顺时针: 从 A 顶边左端开始
        moveTo(a.left + rA, a.top)
        lineTo(a.right - rA, a.top)
        arcTo(Rect(Offset(a.right - rA, a.top + rA), rA), 270f, 90f, false) // A 右上角 -> (a.right, a.top + rA)
        lineTo(a.right, b.top - rJ)
        if (rJ > 0f) {
            // 内凹过渡: 从 A 的右边拐向 B 的上边 -> (a.right + rJ, b.top)
            arcTo(Rect(Offset(a.right + rJ, b.top - rJ), rJ), 180f, -90f, false)
        }
        lineTo(b.right - rB, b.top)
        arcTo(Rect(Offset(b.right - rB, b.top + rB), rB), 270f, 180f, false) // B 的半圆伸出端 -> (b.right - rB, b.bottom)
        lineTo(a.left + rA, a.bottom) // B 与 A 连成一条直线的底边
        arcTo(Rect(Offset(a.left + rA, a.bottom - rA), rA), 90f, 90f, false) // A 左下角 -> (a.left, a.bottom - rA)
        lineTo(a.left, a.top + rA)
        arcTo(Rect(Offset(a.left + rA, a.top + rA), rA), 180f, 90f, false) // A 左上角 -> (a.left + rA, a.top)
        close()
    }
}

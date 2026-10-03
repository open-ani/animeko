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
 * A 区域 [regionA] 是圆角矩形 (圆角 [cornerRadius]); B 区域 [regionB] 从 A 的侧边伸出, 外端是半圆,
 * 上下两条边都用半径 [neckRadius] 的内凹圆角接到 A 的侧边, 所以 A 和 B 之间没有折角, 读起来是一块面板.
 *
 * B 在 A 的哪一侧由两者的中心位置决定; B 的上下边应落在 A 侧边的直线段内. 两个半径都会被限制在不互相冲突的范围内.
 */
internal fun screenshotPanelOutline(
    regionA: Rect,
    regionB: Rect,
    cornerRadius: Float,
    neckRadius: Float,
): Path {
    val bOnRight = regionB.center.x >= regionA.center.x
    if (bOnRight) return outlineWithLobeOnRight(regionA, regionB, cornerRadius, neckRadius)

    // 左侧: 先按右侧画, 再整体水平镜像
    val axis = min(regionA.left, regionB.left) + max(regionA.right, regionB.right)
    fun Rect.mirrored() = Rect(axis - right, top, axis - left, bottom)
    val path = outlineWithLobeOnRight(regionA.mirrored(), regionB.mirrored(), cornerRadius, neckRadius)
    path.transform(
        Matrix().apply {
            translate(axis, 0f)
            scale(-1f, 1f)
        },
    )
    return path
}

private fun outlineWithLobeOnRight(a: Rect, b: Rect, cornerRadius: Float, neckRadius: Float): Path {
    // A 的圆角不能侵入 B 所占的那段侧边
    val straightSide = min(b.top - a.top, a.bottom - b.bottom)
    val rA = min(cornerRadius, min(a.width / 2f, min(a.height / 2f, straightSide))).coerceAtLeast(0f)
    val rB = (b.height / 2f).coerceAtLeast(0f)
    // 内凹圆角不能越过 A 的上下角, 也不能超过 B 露出部分可用的宽度
    val rJ = min(
        neckRadius,
        min(min(b.top - (a.top + rA), (a.bottom - rA) - b.bottom), (b.right - rB) - a.right),
    ).coerceAtLeast(0f)

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
        arcTo(Rect(Offset(b.right - rB, b.top + rB), rB), 270f, 180f, false) // B 的半圆外端 -> (b.right - rB, b.bottom)
        lineTo(a.right + rJ, b.bottom)
        if (rJ > 0f) {
            // 内凹过渡: 从 B 的下边拐回 A 的右边 -> (a.right, b.bottom + rJ)
            arcTo(Rect(Offset(a.right + rJ, b.bottom + rJ), rJ), 270f, -90f, false)
        }
        lineTo(a.right, a.bottom - rA)
        arcTo(Rect(Offset(a.right - rA, a.bottom - rA), rA), 0f, 90f, false) // A 右下角 -> (a.right - rA, a.bottom)
        lineTo(a.left + rA, a.bottom)
        arcTo(Rect(Offset(a.left + rA, a.bottom - rA), rA), 90f, 90f, false) // A 左下角 -> (a.left, a.bottom - rA)
        lineTo(a.left, a.top + rA)
        arcTo(Rect(Offset(a.left + rA, a.top + rA), rA), 180f, 90f, false) // A 左上角 -> (a.left + rA, a.top)
        close()
    }
}

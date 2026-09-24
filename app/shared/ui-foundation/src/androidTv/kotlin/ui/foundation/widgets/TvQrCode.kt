/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor

/**
 * 白底黑码的二维码, 自带 [QuietZoneModules] 个模块宽的留白, 可以直接放在深色背景上.
 * 模块按整像素绘制, 避免缩放产生的灰边影响识别. 大小由 [modifier] 决定.
 */
@Composable
fun TvQrCode(content: String, contentDescription: String, modifier: Modifier = Modifier) {
    val code = remember(content) {
        // margin 0: 留白由绘制时统一处理
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0),
        )
    }
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(8.dp.toPx()))
        val module = floor(size.minDimension / (code.width + QuietZoneModules * 2))
        val offset = Offset(
            floor((size.width - module * code.width) / 2),
            floor((size.height - module * code.height) / 2),
        )
        for (y in 0 until code.height) for (x in 0 until code.width) {
            if (code[x, y]) drawRect(Color.Black, offset + Offset(x * module, y * module), Size(module, module))
        }
    }
}

private const val QuietZoneModules = 4

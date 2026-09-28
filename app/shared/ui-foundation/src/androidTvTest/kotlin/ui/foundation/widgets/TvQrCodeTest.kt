/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.widgets

import androidx.compose.ui.geometry.Size
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvQrCodeTest {
    private val contents = listOf("https://t.me/openani", "https://myani.org/?utm_source=tv&lang=zh-Hans")

    /** 176dp 在常见 TV 密度下的边长, 以及一个不能被模块数整除的边长. */
    private val sides = listOf(176, 264, 352, 347)

    @Test
    fun `rendered modules decode to the encoded content`() {
        for (content in contents) for (side in sides) {
            assertEquals(content, decode(render(content, side), side), "side=$side")
        }
    }

    @Test
    fun `modules are whole pixels and the quiet zone spans at least four modules`() {
        for (content in contents) for (side in sides) {
            val code = encodeTvQrCode(content)
            val layout = TvQrCodeLayout(code, Size(side.toFloat(), side.toFloat()))
            assertTrue(layout.module >= 1f && layout.module % 1f == 0f, "module=${layout.module}, side=$side")
            val quietZone = QuietZoneModules * layout.module
            val end = layout.moduleTopLeft(code.width, code.height)
            assertTrue(layout.offset.x >= quietZone && layout.offset.y >= quietZone, "leading zone, side=$side")
            assertTrue(side - end.x >= quietZone && side - end.y >= quietZone, "trailing zone, side=$side")
        }
    }

    /** 按 [TvQrCode] 的绘制方式栅格化: 白色画布上逐个填充黑色模块. */
    private fun render(content: String, side: Int): IntArray {
        val code = encodeTvQrCode(content)
        val layout = TvQrCodeLayout(code, Size(side.toFloat(), side.toFloat()))
        val module = layout.module.toInt()
        val pixels = IntArray(side * side) { White }
        for (y in 0 until code.height) for (x in 0 until code.width) {
            if (!code[x, y]) continue
            val topLeft = layout.moduleTopLeft(x, y)
            for (dy in 0 until module) for (dx in 0 until module) {
                pixels[(topLeft.y.toInt() + dy) * side + topLeft.x.toInt() + dx] = Black
            }
        }
        return pixels
    }

    private fun decode(pixels: IntArray, side: Int): String =
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))).text

    private companion object {
        const val White = 0xFFFFFFFF.toInt()
        const val Black = 0xFF000000.toInt()
    }
}

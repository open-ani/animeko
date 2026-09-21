/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrCodeDecoderTest {
    private val content = "https://api.animeko.org/static/immutable/qrLogin.v1.html?requestId=0a7a3ff9-d601-488a-8131-3f5ed8c4c1eb"

    /** 像相机帧一样的 Y 平面: 白底黑码, 每行末尾有 [padding] 字节的无关数据. */
    private fun yPlane(size: Int, padding: Int, draw: Boolean = true): ByteBuffer {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val rowStride = size + padding
        val bytes = ByteArray(rowStride * size) { 0x55 }
        for (y in 0 until size) for (x in 0 until size) {
            bytes[y * rowStride + x] = if (draw && matrix[x, y]) 0x10 else 0xEB.toByte()
        }
        return ByteBuffer.wrap(bytes)
    }

    @Test
    fun `decodes a frame whose rows are padded`() {
        val decoder = QrCodeDecoder()
        assertEquals(content, decoder.decode(yPlane(480, padding = 32), 480, 480, rowStride = 512))
        // 同一个 decoder 连续处理不同尺寸的帧
        assertEquals(content, decoder.decode(yPlane(320, padding = 0), 320, 320, rowStride = 320))
    }

    @Test
    fun `returns null when there is no code`() {
        assertNull(QrCodeDecoder().decode(yPlane(320, padding = 16, draw = false), 320, 320, rowStride = 336))
    }
}

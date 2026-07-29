/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.captcha

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.DesktopOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 自动解图片验证码依赖两样运行时资源: onnxruntime 与随 app-data 打包的 `captcha-v1.0.onnx`.
 * 两者都是通过 `implementation` 传递进来的, 一旦哪天从 MCP 的运行时 classpath 掉了,
 * 自动解会静默退化成 "永远识别不出" —— 这个 smoke test 就是为了让那种退化立刻可见.
 *
 * 只断言 "模型能加载并推理出 4 位数字", 不断言识别结果 (输入是白底图, 没有正确答案).
 */
class ImageCaptchaRecognizerSmokeTest {
    @Test
    fun `bundled onnx captcha model loads and runs in this module`() = runTest {
        val answer = DesktopOnnxImageCaptchaRecognizer().recognize(
            ImageCaptchaSample(blankPng(), "image/png", "test://blank"),
        )
        assertNotNull(answer, "识别器没能加载模型或推理失败")
        assertTrue(answer.length == 4 && answer.all { it in '0'..'9' }, "预期 4 位数字, 实际: $answer")
    }

    private fun blankPng(): ByteArray {
        val image = BufferedImage(96, 32, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                fillRect(0, 0, width, height)
                dispose()
            }
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }
}

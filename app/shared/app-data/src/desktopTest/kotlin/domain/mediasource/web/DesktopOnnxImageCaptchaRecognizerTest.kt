/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals

@DisabledOnOs(
    value = [OS.WINDOWS],
    architectures = ["aarch64", "arm64", "arm"],
    disabledReason = "Microsoft onnxruntime doesn't support windows aarch64.",
)
class DesktopOnnxImageCaptchaRecognizerTest {
    @Test
    fun `recognizes a labeled real captcha sample`() = runTest {
        val bytes = checkNotNull(javaClass.getResourceAsStream(TEST_SAMPLE_RESOURCE)).use { it.readBytes() }
        val recognizer = DesktopOnnxImageCaptchaRecognizer()

        val result = recognizer.recognize(
            ImageCaptchaSample(
                bytes = bytes,
                mediaType = "image/png",
                sourceUrl = "resource://$TEST_SAMPLE_RESOURCE",
            ),
        )

        assertEquals("0000", result)
    }

    private companion object {
        private const val TEST_SAMPLE_RESOURCE = "/captcha-test/0000.png"
    }
}

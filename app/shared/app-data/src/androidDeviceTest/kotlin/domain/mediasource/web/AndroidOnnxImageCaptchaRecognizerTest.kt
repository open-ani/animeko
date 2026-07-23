/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidOnnxImageCaptchaRecognizerTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `recognizes a labeled real captcha sample`() = runBlocking {
        val bytes = context.assets.open(TEST_SAMPLE_ASSET).use { it.readBytes() }
        val recognizer = AndroidOnnxImageCaptchaRecognizer()

        val result = recognizer.recognize(
            ImageCaptchaSample(
                bytes = bytes,
                mediaType = "image/png",
                sourceUrl = "asset://$TEST_SAMPLE_ASSET",
            ),
        )

        assertEquals("0000", result)
    }

    private companion object {
        private const val TEST_SAMPLE_ASSET = "captcha-test/0000.png"
    }
}

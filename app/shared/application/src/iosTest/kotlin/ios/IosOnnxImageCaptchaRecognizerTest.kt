/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ios

import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
class IosOnnxImageCaptchaRecognizerTest {
    @Test
    fun `recognizes a labeled real captcha sample`() = runBlocking {
        val bytes = Base64.decode(TEST_SAMPLE_BASE64)

        val result = IosOnnxImageCaptchaRecognizer().recognize(
            ImageCaptchaSample(
                bytes = bytes,
                mediaType = "image/png",
                sourceUrl = "bundle://0000.png",
            ),
        )

        assertEquals("0000", result)
    }

    /**
     * 识别器持有的 ORTEnv 若存活到进程 exit, onnxruntime 的静态 OrtEnv 析构会锁已销毁的 mutex 并 terminate.
     * 退出前回收识别器, 让 OrtEnv 随最后一个 ORTEnv 释放.
     */
    @OptIn(NativeRuntimeApi::class)
    @AfterTest
    fun releaseOnnxRuntime() {
        GC.collect()
    }

    private companion object {
        private const val TEST_SAMPLE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAIAAAAAoBAMAAADEX+97AAAAG1BMVEXz+/6AR07WztLHt7zk5OicdHqOXWSripC5oaZxO59X" +
                "AAAACXBIWXMAAA7EAAAOxAGVKw4bAAABQ0lEQVRIie1SMU+DQBi9UrCMPivYkZrYOAIxseO1DnRs1cHR06qMMtQ4QhO1P9sP7iiJ1qvK" +
                "1IS38HF8733fewdjDRrsDMY1+RGmtfinAvtlfbFI/8w/SQUOZGm8o9L6LSz4QLcoIywzHOqajZB/O8uwBHpFKcAz/QYz4OHV/3p2lcDJqzbA" +
                "Ezxr+NSRjtQ0Zq64Eojf4FYCuguxqCMoBfpwPGnBOQeUgJdoLbRwRD5lSqYgYu4mACbSAh1xvcAAPQPK5B4wt92UsRFgYF4IYJuAwK0lh" +
                "zGWAMcif2nBjWJeZeD/zJcZSgGb7v4FuXdaJV5/H4pLzQJ5hpnKsA88fhT338F9NeDG0/BpqhuWGVJ010GRhxmuG1ZnOjphPFwIV3rsoEt5" +
                "OlsIm8DVc/I0gAz/v7AF/Td1BFh7dleL36DBbuETP2wwuSNW2esAAAAASUVORK5CYII="
    }
}

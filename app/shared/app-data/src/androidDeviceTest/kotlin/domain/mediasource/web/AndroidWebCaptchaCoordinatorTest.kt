/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidWebCaptchaCoordinatorTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun htmlDataUrl(html: String): String {
        val encoded = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "data:text/html;base64,$encoded"
    }

    @Test
    fun `tryAutoSolve returns solved for a normal page`() = runBlocking {
        val coordinator = AndroidWebCaptchaCoordinator(context)

        val result = coordinator.tryAutoSolve(
            WebCaptchaRequest(
                mediaSourceId = "normal-page",
                pageUrl = htmlDataUrl("<html><body><h1>OK</h1></body></html>"),
                kind = WebCaptchaKind.Unknown,
            ),
        )

        val solved = assertIs<WebCaptchaSolveResult.Solved>(result)
        assertTrue(solved.finalUrl.startsWith("data:text/html"))
    }

    @Test
    fun `tryAutoSolve handles cloudflare page without crashing`() = runBlocking {
        val coordinator = AndroidWebCaptchaCoordinator(context)

        val result = coordinator.tryAutoSolve(
            WebCaptchaRequest(
                mediaSourceId = "cloudflare-page",
                pageUrl = htmlDataUrl(
                    """
                    <html>
                      <head><title>Just a moment...</title></head>
                      <body>Checking your browser before accessing this website.</body>
                    </html>
                    """.trimIndent(),
                ),
                kind = WebCaptchaKind.Cloudflare,
            ),
        )

        when (result) {
            is WebCaptchaSolveResult.Solved -> {
                assertTrue(result.finalUrl.startsWith("data:text/html"))
            }

            is WebCaptchaSolveResult.StillBlocked -> {
                assertEquals(WebCaptchaKind.Cloudflare, result.kind)
            }

            else -> error("unexpected result: $result")
        }
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.utils.xml.Html
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCaptchaSupportTest {
    @Test
    fun `displayName matches ui copy`() {
        assertEquals("图片验证码", WebCaptchaKind.Image.displayName())
        assertEquals("Cloudflare 验证", WebCaptchaKind.Cloudflare.displayName())
        assertEquals("Cloudflare Turnstile 验证", WebCaptchaKind.CloudflareTurnstile.displayName())
        assertEquals("验证码", WebCaptchaKind.Unknown.displayName())
    }

    @Test
    fun `normalizedSessionHost strips www and lowercases`() {
        assertEquals("example.com", normalizedSessionHost("https://WWW.Example.com/search?q=1"))
        assertEquals("play.example.com", normalizedSessionHost("https://play.example.com/embed/1"))
        assertNull(normalizedSessionHost("not a url ::"))
    }

    @Test
    fun `normalizedStorageOrigin keeps scheme and non-default port`() {
        assertEquals("https://example.com", normalizedStorageOrigin("https://www.example.com/a/b?c=d"))
        assertEquals("http://example.com:8080", normalizedStorageOrigin("http://example.com:8080/a"))
    }

    @Test
    fun `search cooldown page is detected from html content`() {
        val document = Html.parse(
            """
                <html>
                  <body>
                    <div class="msg-jump">
                      <p>親愛的：請不要頻繁操作，搜索時間間隔爲3秒前</p>
                      <p><a id="href" href="javascript:history.back(-1);">跳轉</a></p>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertTrue(document.isSearchCooldownPage())
    }

    @Test
    fun `normal search result page is not treated as cooldown page`() {
        val document = Html.parse(
            """
                <html>
                  <body>
                    <div class="video-info-header">
                      <a href="/subject/1">Frieren</a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertFalse(document.isSearchCooldownPage())
    }
}

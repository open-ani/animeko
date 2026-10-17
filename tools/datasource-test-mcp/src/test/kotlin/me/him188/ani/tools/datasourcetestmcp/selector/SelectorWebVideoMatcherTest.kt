/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.selector

import io.ktor.http.Url
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.utils.xml.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SelectorWebVideoMatcher.patchConfig] 必须与 App 内 `SelectorMediaSource.matcher` 一致:
 * cookie 按名去重, 后面的列表覆盖前面的; 配置了 User-Agent 时覆盖上游的.
 */
class SelectorWebVideoMatcherTest {
    private val engine = object : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url, subjectFormatId: SelectorFormatId): SearchSubjectResult =
            throw UnsupportedOperationException()

        override suspend fun doHttpGet(uri: String): Document =
            throw UnsupportedOperationException()
    }

    private fun matcher(cookies: String = "", userAgent: String? = null) = SelectorWebVideoMatcher(
        engine,
        SelectorSearchConfig.MatchVideoConfig(cookies = cookies),
        userAgent,
    )

    @Test
    fun `configured cookie overrides same-name upstream cookie`() {
        val result = matcher("quality=1080").patchConfig(
            WebViewConfig(cookies = listOf("quality=720", "session=abc")),
        )
        assertEquals(listOf("quality=1080", "session=abc"), result.cookies)
    }

    @Test
    fun `keeps first-seen name order and ignores blank lines`() {
        val result = matcher("b=2\n\n  \nc=3").patchConfig(
            WebViewConfig(cookies = listOf("a=1", "b=1")),
        )
        assertEquals(listOf("a=1", "b=2", "c=3"), result.cookies)
    }

    @Test
    fun `cookie value may contain equals sign`() {
        val result = matcher("token=a=b").patchConfig(WebViewConfig(cookies = emptyList()))
        assertEquals(listOf("token=a=b"), result.cookies)
    }

    @Test
    fun `trims whitespace around cookies`() {
        val result = matcher("  quality=1080  ").patchConfig(
            WebViewConfig(cookies = listOf(" quality=720 ")),
        )
        assertEquals(listOf("quality=1080"), result.cookies)
    }

    @Test
    fun `configured user agent overrides upstream user agent`() {
        val result = matcher(userAgent = "Mobile UA").patchConfig(WebViewConfig(userAgent = "Upstream UA"))
        assertEquals("Mobile UA", result.userAgent)
    }

    @Test
    fun `absent user agent keeps upstream user agent`() {
        assertEquals("Upstream UA", matcher().patchConfig(WebViewConfig(userAgent = "Upstream UA")).userAgent)
        assertNull(matcher().patchConfig(WebViewConfig.Empty).userAgent)
    }
}

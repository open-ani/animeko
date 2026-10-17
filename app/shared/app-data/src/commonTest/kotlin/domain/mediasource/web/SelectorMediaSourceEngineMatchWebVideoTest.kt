/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [SelectorMediaSourceEngine.matchWebVideo] 交给播放器的请求头: User-Agent 与数据源其他步骤一致,
 * 数据源没有配置 UA 时沿用旧配置里只给播放器的 UA.
 */
class SelectorMediaSourceEngineMatchWebVideoTest {
    // matchWebVideo 不发请求, client 只是构造 engine 用
    private val engine = DefaultSelectorMediaSourceEngine(
        HttpClient(MockEngine { respond("") }).asScopedHttpClient(),
    )

    private fun videoHeaders(config: SelectorSearchConfig): Map<String, String> {
        val result = engine.matchWebVideo("https://cdn.example.com/video.mp4", config.matchVideo, config.userAgentOrNull)
        return assertIs<WebVideoMatcher.MatchResult.Matched>(result).video.headers
    }

    @Suppress("DEPRECATION")
    private fun legacyConfig(userAgent: String = "", legacyVideoUserAgent: String) = SelectorSearchConfig(
        userAgent = userAgent,
        matchVideo = SelectorSearchConfig.MatchVideoConfig(
            addHeadersToVideo = SelectorSearchConfig.VideoHeaders(userAgent = legacyVideoUserAgent),
        ),
    )

    @Test
    fun `player uses the source user agent`() {
        val headers = videoHeaders(
            SelectorSearchConfig(
                userAgent = "Mobile UA",
                matchVideo = SelectorSearchConfig.MatchVideoConfig(
                    addHeadersToVideo = SelectorSearchConfig.VideoHeaders(referer = "https://example.com/"),
                ),
            ),
        )
        assertEquals("Mobile UA", headers["User-Agent"])
        assertEquals("https://example.com/", headers["Referer"])
    }

    @Test
    fun `player uses the legacy video user agent when source user agent is empty`() {
        assertEquals("Legacy UA", videoHeaders(legacyConfig(legacyVideoUserAgent = "Legacy UA"))["User-Agent"])
    }

    @Test
    fun `source user agent wins over the legacy video user agent`() {
        val headers = videoHeaders(legacyConfig(userAgent = "Mobile UA", legacyVideoUserAgent = "Legacy UA"))
        assertEquals("Mobile UA", headers["User-Agent"])
    }

    @Test
    fun `player falls back to the default legacy video user agent`() {
        @Suppress("DEPRECATION")
        val default = SelectorSearchConfig.VideoHeaders().userAgent
        assertEquals(default, videoHeaders(SelectorSearchConfig())["User-Agent"])
    }
}

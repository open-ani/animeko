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
 * [SelectorMediaSourceEngine.matchWebVideo] 交给播放器的请求头: User-Agent 与数据源其他步骤一致.
 */
class SelectorMediaSourceEngineMatchWebVideoTest {
    // matchWebVideo 不发请求, client 只是构造 engine 用
    private val engine = DefaultSelectorMediaSourceEngine(
        HttpClient(MockEngine { respond("") }).asScopedHttpClient(),
    )

    private fun videoHeaders(userAgent: String?): Map<String, String> {
        val result = engine.matchWebVideo(
            "https://cdn.example.com/video.mp4",
            SelectorSearchConfig.MatchVideoConfig(addHeadersToVideo = SelectorSearchConfig.VideoHeaders(referer = "https://example.com/")),
            userAgent,
        )
        return assertIs<WebVideoMatcher.MatchResult.Matched>(result).video.headers
    }

    @Test
    fun `player uses the source user agent`() {
        val headers = videoHeaders("Mobile UA")
        assertEquals("Mobile UA", headers["User-Agent"])
        assertEquals("https://example.com/", headers["Referer"])
    }

    @Test
    fun `player falls back to the default video user agent`() {
        assertEquals(SelectorSearchConfig.DEFAULT_VIDEO_USER_AGENT, videoHeaders(null)["User-Agent"])
    }
}

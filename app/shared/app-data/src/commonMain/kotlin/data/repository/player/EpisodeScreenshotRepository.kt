/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

sealed class EpisodeScreenshotRepository : Repository() {
    abstract suspend fun getScreenshots(magnetUri: String): ApiResponse<List<String>>
}

// https://whatslink.info/
// 这玩意虽然能跑但是限制阈值有点太低了, 估计实际使用的时候会很容易被限调用速度, 得考虑别的方案
class WhatslinkEpisodeScreenshotRepository : EpisodeScreenshotRepository(), KoinComponent {
    private val clientProvider: HttpClientProvider by inject()
    private val client = clientProvider.get(ScopedHttpClientUserAgent.BROWSER)

    @Serializable
    private data class WhatslinkResponse(
        val screenshots: List<WhatslinkScreenshot>
    )

    @Serializable
    private data class WhatslinkScreenshot(
        val time: String,
        val screenshot: String,
    )

    override suspend fun getScreenshots(magnetUri: String): ApiResponse<List<String>> {
        return runApiRequest {
            client.use {
                get("https://whatslink.info/api/v1/link") {
                    parameter("url", magnetUri)
                }.body<WhatslinkResponse>()
                    .screenshots.map { it.screenshot }
            }
        }
    }
}

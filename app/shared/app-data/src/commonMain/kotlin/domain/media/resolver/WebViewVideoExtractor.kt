/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import me.him188.ani.app.platform.Context
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.utils.platform.annotations.TestOnly

interface WebViewVideoExtractor {
    sealed class Instruction {
        /**
         * 继续加载这个链接
         */
        data object LoadPage : Instruction()

        /**
         * 已经找到资源, 停止加载
         */
        data object FoundResource : Instruction()

        data object Continue : Instruction()
    }

    suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource?

    companion object {
        const val DEFAULT_TIMEOUT = 8_000L
    }
}

data class WebResource(
    val url: String
)

expect fun WebViewVideoExtractor(
    proxyConfig: ProxyConfig?,
    videoResolverSettings: VideoResolverSettings,
): WebViewVideoExtractor

@TestOnly
class TestWebViewVideoExtractor(
    private val urls: (pageUrl: String) -> List<String>,
) : WebViewVideoExtractor {
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource {
        urls(pageUrl).forEach {
            if (resourceMatcher(it) is Instruction.FoundResource) {
                return WebResource(it)
            }
        }
        throw IllegalStateException("No match found")
    }
}

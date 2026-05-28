package me.him188.ani.app.domain.media.resolver

import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.platform.Context
import me.him188.ani.datasources.api.matcher.WebViewConfig

actual fun WebViewVideoExtractor(
    proxyConfig: ProxyConfig?,
    videoResolverSettings: VideoResolverSettings,
): WebViewVideoExtractor = object : WebViewVideoExtractor {
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? = null
}

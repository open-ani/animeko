/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.matcher

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSource

/**
 * 匹配 WebView 拦截到的资源.
 */ // see also: SelectorMediaSource
fun interface WebVideoMatcher { // SPI service load
    sealed class MatchResult {
        data class Matched(
            val video: WebVideo
        ) : MatchResult()

        data object Continue : MatchResult()
        data object LoadPage : MatchResult()
    }

    fun match(
        url: String,
        context: WebVideoMatcherContext
    ): MatchResult

    fun patchConfig(config: WebViewConfig): WebViewConfig = config
}

data class WebViewConfig(
    val cookies: List<String> = emptyList(),
    val scanDomMediaUrls: Boolean = false,
    val scanInlineScriptUrls: Boolean = false,
) {
    companion object {
        val Empty = WebViewConfig()
    }
}

val WebVideoMatcher.MatchResult.videoOrNull get() = (this as? WebVideoMatcher.MatchResult.Matched)?.video

class WebVideoMatcherContext(
    val media: Media,
//    requestInfoLazy: () -> WebVideoRequestInfo,
) {
//    val requestInfo by lazy { requestInfoLazy() }
}

/**
 * 由 [MediaSource] 实现
 */
interface WebVideoMatcherProvider {
    val matcher: WebVideoMatcher
}

/**
 * 用于加载各个数据源实例提供的 [WebVideoMatcher]. 因为数据源实例 [MediaSource] 是创建动态的.
 */
class MediaSourceWebVideoMatcherLoader(
    private val mediaSources: Flow<List<MediaSource>>
) {
    suspend fun loadMatchers(mediaSourceId: String): List<WebVideoMatcher> {
        return mediaSources.first().asSequence()
            .filter { it.mediaSourceId == mediaSourceId }
            .filterIsInstance<WebVideoMatcherProvider>()
            .map { it.matcher }
            .toList()
    }
}

class WebVideoRequestInfo(
    val url: String,
    val headers: Map<String, String>
)

data class WebVideo(
    /**
     * 视频数据地址
     */
    val m3u8Url: String,
    /**
     * 请求视频数据时需要的 headers
     *
     * 建议提供:
     *
     * - `User-Agent`
     * - `Referer`
     */
    val headers: Map<String, String>
)

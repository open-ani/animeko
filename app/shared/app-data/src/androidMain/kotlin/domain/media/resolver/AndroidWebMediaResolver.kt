/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Companion.DEFAULT_TIMEOUT
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.matcher.videoOrNull
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentSkipListSet


/**
 * 用 WebView 加载网站, 拦截 WebView 加载资源, 用各数据源提供的 [WebVideoMatcher]
 */
class AndroidWebMediaResolver(
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
) : MediaResolver {
    private companion object {
        private val logger = logger<AndroidWebMediaResolver>()
    }

    private val matchersFromClasspath by lazy {
        java.util.ServiceLoader.load(WebVideoMatcher::class.java, this::class.java.classLoader).filterNotNull()
    }

    override fun supports(media: Media): Boolean = media.download is ResourceLocation.WebVideo

    private var attached: Context? = null

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun ComposeContent() {
        super.ComposeContent()

        val context = LocalContext.current
        DisposableEffect(true) {
            attached = context
            onDispose {
                attached = null
            }
        }
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)

        val matchersFromMediaSource = matcherLoader.loadMatchers(media.mediaSourceId)
        val allMatchers = matchersFromMediaSource + matchersFromClasspath

        val context = WebVideoMatcherContext(media)
        fun match(url: String): WebVideoMatcher.MatchResult? {
            return allMatchers
                .asSequence()
                .map { matcher ->
                    matcher.match(url, context)
                }
                .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
        }

        val config = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
            matcher.patchConfig(acc)
        }
        logger.info { "Final config: $config" }

        val webVideo = AndroidWebViewVideoExtractor().getVideoResourceUrl(
            attached ?: throw IllegalStateException("WebVideoSourceResolver not attached"),
            media.download.uri,
            config,
        ) {
            when (match(it)) {
                WebVideoMatcher.MatchResult.Continue -> Instruction.Continue
                WebVideoMatcher.MatchResult.LoadPage -> Instruction.LoadPage
                is WebVideoMatcher.MatchResult.Matched -> Instruction.FoundResource
                null -> Instruction.Continue
            }
        }?.let { resource ->
            allMatchers.firstNotNullOfOrNull { matcher ->
                matcher.match(resource.url, context).videoOrNull
            }
        } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        return HttpStreamingMediaDataProvider(
            webVideo.m3u8Url,
            media.originalTitle,
            webVideo.headers,
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }
}

class AndroidWebViewVideoExtractor : WebViewVideoExtractor {
    private companion object {
        private val logger = logger<AndroidWebViewVideoExtractor>()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource? {
        // WebView requires same thread
//        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<WebResource>()
            val loadedNestedUrls = ConcurrentSkipListSet<String>()

            runCatching {
                for (string in config.cookies) {
                    CookieManager.getInstance().setCookie(pageUrl, string)
                }
            }.onFailure { exception ->
                logger.error("Failed to set cookie", exception)
            }

            /**
             * @return if the url has been consumed
             */
            fun handleUrl(webView: WebView, url: String): Boolean {
                val matched = resourceMatcher(url)
                when (matched) {
                    Instruction.Continue -> return false
                    Instruction.FoundResource -> {
                        deferred.complete(WebResource(url))
                        return true
                    }

                    Instruction.LoadPage -> {
                        logger.info { "WebView loading nested page: $url" }
                        launch(Dispatchers.Main) {
                            if (webView.url == url) return@launch // avoid infinite loop
                            if (!loadedNestedUrls.add(url)) return@launch
                            logger.info { "WebView navigating to new url: $url" }
                            webView.loadUrl(url)
//                            createWebView(context, deferred, ::handleUrl).loadUrl(url)
                        }
                        return false
                    }
                }
            }

            loadedNestedUrls.add(pageUrl)
            createWebView(context, deferred, ::handleUrl).loadUrl(pageUrl)

            //            webView.webChromeClient = object : WebChromeClient() {
            //                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            //                    consoleMessage ?: return false
            //                    val message = consoleMessage.message() ?: return false
            //                    // HTTPS 页面加载 HTTP 的视频时会有日志
            //                    for (matchResult in consoleMessageUrlRegex.findAll(message)) {
            //                        val url = matchResult.value.removeSurrounding("'")
            //                        logger.info { "WebView console get url: $url" }
            //                        handleUrl(url)
            //                    }
            //                    return false
            //                }
            //            }

            try {
                withTimeoutOrNull(DEFAULT_TIMEOUT) {
                    deferred.await()
                }
            } catch (e: Throwable) {
                if (deferred.isActive) {
                    deferred.cancel()
                }
                throw e
            }
        }
//        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(DelicateCoroutinesApi::class)
    private fun createWebView(
        context: Context,
        deferred: CompletableDeferred<WebResource>,
        handleUrl: (WebView, String) -> Boolean,
    ): WebView = WebView(context).apply {
        val webView = this
        deferred.invokeOnCompletion {
            GlobalScope.launch(Dispatchers.Main.immediate) {
                webView.destroy()
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url ?: return super.shouldInterceptRequest(view, request)
                if (handleUrl(view, url.toString())) {
                    logger.info { "Found video resource via shouldInterceptRequest: $url" }
                    // 拦截, 以防资源只能加载一次
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8", 500,
                        "Internal Server Error",
                        mapOf(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView, url: String) {
                if (handleUrl(view, url)) {
                    logger.info { "Found video resource via onLoadResource: $url" }
                }
                super.onLoadResource(view, url)
            }
        }
    }
}

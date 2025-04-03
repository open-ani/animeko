/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.domain.media.resolver

import androidx.compose.runtime.Composable
import io.ktor.http.decodeURLPart
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.platform.Context
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.UrlHelpers
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.source.MediaData
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class IosWebMediaResolver(
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
    private val iosContext: Context,
) : MediaResolver {
    private companion object {
        private val logger = logger<IosWebMediaResolver>()
    }

    override fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.WebVideo
    }

    @Composable
    override fun ComposeContent() {
    }

    @Throws(
        MediaResolutionException::class,
        CancellationException::class,
    )
    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<MediaData> {
        if (!supports(media)) throw UnsupportedMediaException(media)

        // Gather all matchers: from the media source + from the classpath
        val matchersFromMediaSource = matcherLoader.loadMatchers(media.mediaSourceId)

        logger.info { "Loaded matchers: ${matchersFromMediaSource.joinToString(", ")}" }

        val allMatchers = matchersFromMediaSource
        val context = WebVideoMatcherContext(media)

        fun match(url: String): WebVideoMatcher.MatchResult? {
            return allMatchers
                .asSequence()
                .map { matcher -> matcher.match(url, context) }
                .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
        }

        // Merge config from all matchers
        val config = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
            matcher.patchConfig(acc)
        }
        logger.info { "Final config: $config" }

        // Use the iOS webview-based extractor
        val extractor = IosWebViewVideoExtractor()

        var video: WebVideo? = null
        withContext(Dispatchers.Default) {
            extractor.getVideoResourceUrl(
                context = this@IosWebMediaResolver.iosContext,
                pageUrl = media.download.uri,
                config = config,
            ) {
                when (val result = match(it)) {
                    WebVideoMatcher.MatchResult.Continue -> WebViewVideoExtractor.Instruction.Continue
                    is WebVideoMatcher.MatchResult.Matched -> {
                        video = result.video
                        WebViewVideoExtractor.Instruction.FoundResource
                    }

                    WebVideoMatcher.MatchResult.LoadPage -> WebViewVideoExtractor.Instruction.LoadPage
                    null -> WebViewVideoExtractor.Instruction.Continue
                }
            }
        } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)

        val matchedVideo = video ?: error("getVideoResourceUrl completed but no video found")

        return HttpStreamingMediaDataProvider(
            matchedVideo.m3u8Url,
            media.originalTitle,
            matchedVideo.headers,
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }
}

/**
 * Using WKWebView + JavaScript injection to intercept requests for video resources.
 */
class IosWebViewVideoExtractor : WebViewVideoExtractor {
    private companion object {
        private val logger = logger<IosWebViewVideoExtractor>()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction
    ): WebResource? = withContext(Dispatchers.Main) {
        Handler(pageUrl, config, resourceMatcher, this).run()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private inner class Handler(
        private val pageUrl: String,
        private val config: WebViewConfig,
        private val resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
        private val scope: CoroutineScope,
    ) {
        private val deferred = CompletableDeferred<WebResource>()

        // Hold strong reference to avoid GC
        private val aniInterceptContentController = WKUserContentController().apply {
            addScriptMessageHandler(
                object : NSObject(), WKScriptMessageHandlerProtocol {
                    override fun userContentController(
                        userContentController: WKUserContentController,
                        didReceiveScriptMessage: WKScriptMessage
                    ) {
                        val body = didReceiveScriptMessage.body.toString()
                        handleInterceptedUrl(body)
                    }
                },
                name = "AniIntercept",
            )
        }
        private lateinit var webView: WKWebView

        suspend fun run(): WebResource? {
            logger.info { "Starting webview for $pageUrl" }

            webView = WKWebView(
                frame = CGRectMake(0.0, 0.0, 100.0, 100.0), // offscreen or minimal
                configuration = WKWebViewConfiguration().apply {
                    // Add a user content controller for injecting JS
                    userContentController = aniInterceptContentController
                    // Possibly allow inline media playback, JavaScript, etc.:
                    allowsInlineMediaPlayback = true
                    // If you want to set cookies, you typically do so via NSHTTPCookieStorage
                    // or the WKWebsiteDataStore before loading. We'll show an example below.
                },
            )
            try {
                // Set cookies from config
                setCookiesFor(pageUrl, config.cookies)

                // Inject JS to intercept all fetch / XHR / <video> tags, etc.
                // This is a minimal approach. You can enhance to intercept other requests as needed.
                val injectionScript = """
                // Overwrite fetch:
                (function(){
                  const oldFetch = window.fetch;
                  window.fetch = function() {
                    const url = arguments[0];
                    window.webkit.messageHandlers.AniIntercept.postMessage(url);
                    return oldFetch.apply(this, arguments);
                  };
                  // Also intercept XHR:
                  const oldOpen = XMLHttpRequest.prototype.open;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    window.webkit.messageHandlers.AniIntercept.postMessage(url);
                    return oldOpen.apply(this, arguments);
                  };
                })();
            """.trimIndent()

                // Allow executing JS
                webView.configuration.preferences.javaScriptCanOpenWindowsAutomatically = true

                webView.configuration.userContentController.addUserScript(
                    WKUserScript(
                        source = injectionScript,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false,
                    ),
                )

                // WKNavigationDelegate: watch for new main-frame navigations
                val navDelegate = object : NSObject(), WKNavigationDelegateProtocol {
                    override fun webView(
                        webView: WKWebView,
                        decidePolicyForNavigationAction: WKNavigationAction,
                        decisionHandler: (WKNavigationActionPolicy) -> Unit
                    ) {
                        val policy = decidePolicyForNavigationAction.request.URL?.absoluteString()?.let {
                            handleInterceptedUrl(it)
                        } ?: WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                        decisionHandler(policy)
                    }
                }
                webView.navigationDelegate = navDelegate


                logger.info { "Loading page: $pageUrl" }
                // Finally, load the initial page:
                webView.loadRequest(NSURLRequest.requestWithURL(NSURL(string = pageUrl)))

                // Wait up to 15 seconds or user cancel
                val result = withTimeoutOrNull(15.seconds) {
                    deferred.await()
                }
                return result
            } finally {
                webView.stopLoading()
                webView.navigationDelegate = null // help GC    
            }
        }

        // Function to handle any URL that the JS intercepts (or main frame load):
        fun doHandleUrl(url: String): WKNavigationActionPolicy {
            if (deferred.isCompleted) return WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            @Suppress("NAME_SHADOWING")
            val url = UrlHelpers.computeAbsoluteUrl(webView.URL?.absoluteString ?: pageUrl, url)
            logger.info { "Processing url: $url" }
            val instruction = resourceMatcher(url.runCatching { decodeURLPart() }.getOrElse { url })
            when (instruction) {
                WebViewVideoExtractor.Instruction.Continue -> Unit
                WebViewVideoExtractor.Instruction.FoundResource -> {
                    logger.info { "Found resource: $url" }
                    deferred.complete(WebResource(url))
                    return WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                }

                WebViewVideoExtractor.Instruction.LoadPage -> {
                    logger.info { "Load nested page: $url" }
                    // Navigate the WKWebView to the new URL:
                    webView.loadRequest(NSURLRequest.requestWithURL(NSURL(string = url)))
                }
            }
            return WKNavigationActionPolicy.WKNavigationActionPolicyAllow
        }

        fun handleInterceptedUrl(url: String) = doHandleUrl(url)
    }

    /**
     * In iOS, you typically set cookies via NSHTTPCookieStorage / WKWebsiteDataStore.
     */
    private fun setCookiesFor(url: String, cookies: List<String>) {
        // Very minimal approach:
        // Each cookie is a Set-Cookie header string. We'll parse them and set them in NSHTTPCookieStorage.
        val nsCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage
        cookies.forEach { cookieString ->
            val cookieMap = NSHTTPCookie.cookiesWithResponseHeaderFields(
                mapOf("Set-Cookie" to cookieString),
                NSURL(string = url),
            ).firstOrNull()?.let { it as? NSHTTPCookie }?.properties
            if (cookieMap != null) {
                val newCookie = NSHTTPCookie.cookieWithProperties(cookieMap)
                if (newCookie != null) {
                    nsCookieStorage.setCookie(newCookie)
                }
            }
        }
    }
}

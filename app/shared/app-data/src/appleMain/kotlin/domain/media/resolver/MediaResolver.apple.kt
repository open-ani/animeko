/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
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
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKDownload
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
import platform.WebKit.WKWebpagePreferences
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

        val extractor = IosWebViewVideoExtractor()
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

        var video: WebVideo? = null
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Main) { extractor!! }.getVideoResourceUrl(
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

    private val aniInterceptContentController = WKUserContentController().apply {
        // Add the message handler for "AniIntercept"
        addScriptMessageHandler(
            object : NSObject(), WKScriptMessageHandlerProtocol {
                override fun userContentController(
                    userContentController: WKUserContentController,
                    didReceiveScriptMessage: WKScriptMessage
                ) {
                    val body = didReceiveScriptMessage.body.toString()
                    logger.info { "JS -> Native: $body" }
                    currentHandler?.handleInterceptedUrl(body)
                }
            },
            name = "AniIntercept",
        )

        // Insert a small test to ensure the script actually runs
        val injectionScript = """
            console.log("[AniIntercept] Script injected at documentStart.");
            (function() {
                const oldFetch = window.fetch;
                window.fetch = function() {
                    const url = arguments[0];
                    console.log("[AniIntercept] fetch called:", url);
                    window.webkit.messageHandlers.AniIntercept.postMessage(url);
                    return oldFetch.apply(this, arguments);
                };

                const oldOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    console.log("[AniIntercept] XHR open:", url);
                    window.webkit.messageHandlers.AniIntercept.postMessage(url);
                    return oldOpen.apply(this, arguments);
                };
            })();
            
            (function() {
              const origSetSrc = HTMLMediaElement.prototype.setAttribute;
              HTMLMediaElement.prototype.setAttribute = function(name, value) {
                if (name === 'src') {
                  window.webkit.messageHandlers.AniIntercept.postMessage(value);
                }
                return origSetSrc.apply(this, arguments);
              };
            })();
        """.trimIndent()

        addUserScript(
            WKUserScript(
                source = injectionScript,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false,
            ),
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    val webView: WKWebView = WKWebView(
        frame = CGRectMake(0.0, 0.0, 1000.0, 1000.0),
        configuration = WKWebViewConfiguration().apply {
            allowsInlineMediaPlayback = true
            mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
            preferences.javaScriptCanOpenWindowsAutomatically = true
            userContentController = aniInterceptContentController

            // For demonstration; tweak if needed
            defaultWebpagePreferences.allowsContentJavaScript = true
            preferences.fraudulentWebsiteWarningEnabled = false
            limitsNavigationsToAppBoundDomains = false
            upgradeKnownHostsToHTTPS = false
        },
    )

    private var currentHandler: Handler? = null

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction
    ): WebResource? = withContext(Dispatchers.Main) {
        // Create a new handler for each request to avoid re-entrancy issues
        val handler = Handler(pageUrl, config, resourceMatcher)
        currentHandler = handler
        handler.run()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private inner class Handler(
        private val pageUrl: String,
        private val config: WebViewConfig,
        private val resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ) {
        private val deferred = CompletableDeferred<WebResource>()

        suspend fun run(): WebResource? {
            logger.info { "Starting webview for $pageUrl" }
            webView.setCustomUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/58.0.3029.110 Safari/537.3",
            )

            try {
                // Set cookies from config
                setCookiesFor(pageUrl, config.cookies)

                // Set up the WKNavigationDelegate to intercept main-frame navigations
                webView.navigationDelegate = navDelegate

                logger.info { "Loading page: $pageUrl" }
                webView.loadRequest(NSURLRequest.requestWithURL(NSURL(string = pageUrl)))

                val result = withTimeoutOrNull(15.seconds) {
                    deferred.await()
                }
                return result
            } finally {
                logger.info { "Cleaning up webview" }
                webView.stopLoading()
                webView.loadHTMLString("", baseURL = null)
                webView.navigationDelegate = null
                // Remove the injected script so subsequent calls can re-inject if needed.
            }
        }

        private val handledUrls = mutableSetOf<String>()

        fun handleInterceptedUrl(url: String) = doHandleUrl(url)

        // Shared logic for any new request we want to evaluate
        private fun doHandleUrl(rawUrl: String): WKNavigationActionPolicy {
            if (deferred.isCompleted) return WKNavigationActionPolicy.WKNavigationActionPolicyAllow

            val currentWebViewUrl = webView.URL?.absoluteString ?: pageUrl
            val url = UrlHelpers.computeAbsoluteUrl(currentWebViewUrl, rawUrl)

            if (handledUrls.contains(url)) {
                logger.info { "Already handled url: $url" }
                return WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            }
            handledUrls.add(url)

            logger.info { "Processing url: $url" }
            val instruction = resourceMatcher(url)
            when (instruction) {
                WebViewVideoExtractor.Instruction.Continue -> Unit
                WebViewVideoExtractor.Instruction.FoundResource -> {
                    logger.info { "Found resource: $url" }
                    deferred.complete(WebResource(url))
                    return WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                }

                WebViewVideoExtractor.Instruction.LoadPage -> {
                    logger.info { "Load nested page: $url" }
                    webView.stopLoading()
                    webView.loadRequest(NSURLRequest.requestWithURL(NSURL(string = url)))
                    return WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                }
            }
            return WKNavigationActionPolicy.WKNavigationActionPolicyAllow
        }

        // A simple delegate to catch main-frame navigations
        private val navDelegate = object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit
            ) {
                try {
                    val policy = decidePolicyForNavigationAction.request.URL?.absoluteString?.let {
                        handleInterceptedUrl(it)
                    } ?: WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    decisionHandler(policy)
                } catch (e: Throwable) {
                    logger.info { "Error in navigation action: ${e.message}" }
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                }
            }

            override fun webView(
                webView: WKWebView,
                navigationAction: WKNavigationAction,
                didBecomeDownload: WKDownload
            ) {
                didBecomeDownload.originalRequest?.URL?.absoluteString?.let {
                    handleInterceptedUrl(it)
                }
                logger.info {
                    "Navigation became download: ${didBecomeDownload.originalRequest?.URL?.absoluteString}"
                }
            }

            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                preferences: WKWebpagePreferences,
                decisionHandler: (WKNavigationActionPolicy, WKWebpagePreferences?) -> Unit
            ) {
                try {
                    val policy = decidePolicyForNavigationAction.request.URL?.absoluteString?.let {
                        handleInterceptedUrl(it)
                    } ?: WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    decisionHandler(policy, preferences)
                } catch (e: Throwable) {
                    logger.info { "Error in navigation action: ${e.message}" }
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow, preferences)
                }
            }
        }
    }

    /**
     * In iOS, you typically set cookies via NSHTTPCookieStorage / WKWebsiteDataStore.
     */
    private fun setCookiesFor(url: String, cookies: List<String>) {
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

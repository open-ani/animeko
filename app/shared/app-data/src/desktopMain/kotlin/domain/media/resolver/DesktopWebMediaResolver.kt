/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.http.Cookie
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.util.date.toJvmDate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.applyUserAgent
import me.him188.ani.app.domain.mediasource.web.captcha.browserCreatedSignal
import me.him188.ani.app.domain.mediasource.web.captcha.overrideNavigatorUserAgent
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.platform.AniCefApp
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 用 WebView 加载网站, 拦截 WebView 加载资源, 用各数据源提供的 [WebVideoMatcher]
 */
class DesktopWebMediaResolver(
    private val context: DesktopContext,
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
    private val webSessionManager: WebSessionManager,
) : MediaResolver, KoinComponent {
    private companion object {
        private val logger = logger<DesktopWebMediaResolver>()
    }

    private val matchersFromClasspath by lazy {
        java.util.ServiceLoader.load(WebVideoMatcher::class.java).filterNotNull()
    }
    private val settings: SettingsRepository by inject()
    private val proxyProvider: ProxyProvider by inject()

    // API 签发地址的网页视频由 ApiVideoMediaResolver 解析, 不打开页面
    override fun supports(media: Media): Boolean =
        (media.download as? ResourceLocation.WebVideo)?.isResolvedByApi == false

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return withContext(Dispatchers.Default) {
            if (!supports(media)) throw UnsupportedMediaException(media)

            val resolverSettings = settings.videoResolverSettings.flow.first()
            val matchersFromMediaSource = matcherLoader.loadMatchers(media.mediaSourceId)
            val allMatchers = matchersFromMediaSource + matchersFromClasspath

            val webViewConfig = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
                matcher.patchConfig(acc)
            }
            logger.info { "Final config: $webViewConfig" }


            val context = WebVideoMatcherContext(media)
            fun match(url: String): WebVideoMatcher.MatchResult? {
                return allMatchers
                    .asSequence()
                    .map { matcher ->
                        matcher.match(url, context)
                    }
                    .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
            }

            val resourceMatcher = { url: String ->
                when (match(url)) {
                    WebVideoMatcher.MatchResult.Continue -> Instruction.Continue
                    WebVideoMatcher.MatchResult.LoadPage -> Instruction.LoadPage
                    is WebVideoMatcher.MatchResult.Matched -> Instruction.FoundResource
                    null -> Instruction.Continue
                }
            }

            val webVideo = (
                    webSessionManager.extractVideoResource(
                        pageUrl = media.download.uri,
                        userAgent = webViewConfig.userAgent,
                        timeoutMillis = resolverSettings.effectiveResourceExtractionTimeoutMillis,
                        resourceMatcher = resourceMatcher,
                    ) ?: CefVideoExtractor(proxyProvider.proxy.first(), resolverSettings)
                        .getVideoResourceUrl(
                            this@DesktopWebMediaResolver.context,
                            media.download.uri,
                            webViewConfig,
                            resourceMatcher = resourceMatcher,
                        )
                    )?.let {
                    (match(it.url) as? WebVideoMatcher.MatchResult.Matched)?.video
                } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
            return@withContext HttpStreamingMediaDataProvider(
                webVideo.m3u8Url,
                media.originalTitle,
                webVideo.headers,
                media.extraFiles.toMediampMediaExtraFiles(),
            )
        }
    }
}

class CefVideoExtractor(
    private val proxyConfig: ProxyConfig?,
    private val videoResolverSettings: VideoResolverSettings,
) : WebViewVideoExtractor {
    private companion object {
        private val logger = logger<WebViewVideoExtractor>()
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 每次解析前愿意为 JCEF 初始化等待的时间. 比启动时的上限短, 让真正坏掉的 CEF 尽快报错而不是每次播放都长等.
         */
        private val ON_DEMAND_CEF_INITIALIZATION_TIMEOUT = 15.seconds
    }

    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction
    ): WebResource? = withContext(Dispatchers.IO) {
        var client: org.cef.CefClient? = null
        var browser: CefBrowser? = null
        val deferred = CompletableDeferred<WebResource>()
        val userAgent = config.userAgent

        try {
            // 启动时 JCEF 初始化可能因机器卡顿而超时, 实例仍在后台初始化, 这里接着等它就绪.
            if (!AniCefApp.awaitInitialized(ON_DEMAND_CEF_INITIALIZATION_TIMEOUT)) {
                logger.warn { "AniCefApp isn't initialized, web media cannot be resolved." }
                throw MediaResolutionException(ResolutionFailures.WEB_ENGINE_UNAVAILABLE)
            }
            val lastUrl = object {
                // browser.url is not updated immediately, so we need to keep track of the current url.
                var value: String? by atomic(null)
            }
            val resourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
                override fun onBeforeResourceLoad(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?
                ): Boolean {
                    if (request != null && browser != null) {
                        if (handleUrl(request, browser)) {
                            return true
                        }
                        request.applyUserAgent(userAgent)
                    }
                    return super.onBeforeResourceLoad(browser, frame, request)
                }

                /**
                 * @return `true` to intercept
                 */
                private fun handleUrl(
                    request: CefRequest,
                    browser: CefBrowser
                ): Boolean = synchronized(this) {
                    val url = request.url
                    val matched = resourceMatcher(url)
                    when (matched) {
                        Instruction.Continue -> return false
                        Instruction.FoundResource -> {
                            deferred.complete(WebResource(url))
                            logger.info { "Found video stream resource: $url" }
                            return true
                        }

                        Instruction.LoadPage -> {
                            if (browser.url == url || lastUrl.value == url) return false // don't recurse
                            logger.info { "CEF loading nested page: $url, lastUrl=${lastUrl.value}" }
                            lastUrl.value = url
                            val escapedUrl = json.encodeToString(String.serializer(), url)
                            AniCefApp.runOnCefContext {
                                browser.executeJavaScript("window.location.href=$escapedUrl;", "", 1)
                            }
                            return true
                        }
                    }
                }
            }
            val createdClient = AniCefApp.suspendCoroutineOnCefContext {
                AniCefApp.createClient(resourceRequestHandler)
            } ?: run {
                logger.warn { "AniCefApp was never initialized, web media cannot be resolved." }
                throw MediaResolutionException(ResolutionFailures.WEB_ENGINE_UNAVAILABLE)
            }
            client = createdClient
            val browserCreated = AniCefApp.suspendCoroutineOnCefContext { createdClient.browserCreatedSignal() }

            val createdBrowser = AniCefApp.suspendCoroutineOnCefContext {
                createdClient.createBrowser(
                    // 指定了 UA 时先打开空白页, 等覆写 navigator.userAgent 之后再加载 pageUrl
                    if (userAgent == null) pageUrl else "about:blank",
                    CefRendering.DEFAULT,
                    true,
                    // 全局上下文带磁盘缓存, 同一站点再次解析时播放页的脚本、样式直接命中缓存, 省下大部分加载时间.
                    // cookie 也与验证码浏览器共用. 拦截在 client 的 request handler 上, 只作用于这个 client 的浏览器.
                    CefRequestContext.getGlobalContext(),
                )
            }
            browser = createdBrowser

            AniCefApp.suspendCoroutineOnCefContext {
                createdBrowser.setCloseAllowed()
                createdClient.addDisplayHandler(
                    object : CefDisplayHandlerAdapter() {
                        override fun onConsoleMessage(
                            browser: CefBrowser?,
                            level: CefSettings.LogSeverity?,
                            message: String?,
                            source: String?,
                            line: Int
                        ): Boolean {
                            logger.info { "CEF client console: ${message?.replace("\n", "\\n")} ($source:$line)" }
                            return super.onConsoleMessage(browser, level, message, source, line)
                        }
                    },
                )

                // set cookie. CEF 只接受完整 URL, 只传 host 时会静默丢弃这条 cookie.
                val cookieManager = CefCookieManager.getGlobalManager()
                for (cookie in config.cookies) {
                    val ktorCookie = parseServerSetCookieHeader(cookie)
                    cookieManager.setCookie(pageUrl, ktorCookie.toCefCookie())
                }

                logger.info { "Fetching $pageUrl, userAgent=$userAgent" }
                // start browser immediately
                createdBrowser.createImmediately()
            }

            withTimeoutOrNull(videoResolverSettings.effectiveResourceExtractionTimeoutMillis) {
                if (userAgent != null) {
                    browserCreated.await()
                    createdBrowser.overrideNavigatorUserAgent(userAgent)
                    AniCefApp.suspendCoroutineOnCefContext {
                        createdBrowser.loadURL(pageUrl)
                    }
                }
                deferred.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaResolutionException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to get video url." }
            if (deferred.isActive) {
                deferred.cancel()
            }
            null
        } finally {
            withContext(NonCancellable) {
                AniCefApp.closeBrowserAndDisposeClient(browser, client)
            }
            logger.info { "CEF client is disposed." }
        }
    }
}

private fun Cookie.toCefCookie() =
    CefCookie(
        name,
        value,
        domain,
        path,
        secure,
        httpOnly,
        null,
        null,
        expires != null,
        expires?.toJvmDate(),
    )

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.util.date.toJvmDate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.configIfEnabledOrNull
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
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
import org.openani.mediamp.source.MediaSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 用 WebView 加载网站, 拦截 WebView 加载资源, 用各数据源提供的 [WebVideoMatcher]
 */
class DesktopWebVideoSourceResolver(
    private val context: DesktopContext,
    private val matcherLoader: MediaSourceWebVideoMatcherLoader
) : VideoSourceResolver, KoinComponent {
    private companion object {
        private val logger = logger<DesktopWebVideoSourceResolver>()
    }

    private val matchersFromClasspath by lazy {
        java.util.ServiceLoader.load(WebVideoMatcher::class.java).filterNotNull()
    }
    private val settings: SettingsRepository by inject()

    override suspend fun supports(media: Media): Boolean = media.download is ResourceLocation.WebVideo

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaSource<*> {
        return withContext(Dispatchers.Default) {
            if (!supports(media)) throw UnsupportedMediaException(media)

            val config = settings.proxySettings.flow.first().default
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

            val webVideo = CefVideoExtractor(config.configIfEnabledOrNull, resolverSettings)
                .getVideoResourceUrl(
                    this@DesktopWebVideoSourceResolver.context,
                    media.download.uri,
                    webViewConfig,
                    resourceMatcher = {
                        when (match(it)) {
                            WebVideoMatcher.MatchResult.Continue -> Instruction.Continue
                            WebVideoMatcher.MatchResult.LoadPage -> Instruction.LoadPage
                            is WebVideoMatcher.MatchResult.Matched -> Instruction.FoundResource
                            null -> Instruction.Continue
                        }
                    },
                )?.let {
                    (match(it.url) as? WebVideoMatcher.MatchResult.Matched)?.video
                } ?: throw VideoSourceResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
            return@withContext HttpStreamingMediaSource(
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
    }

    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction
    ): WebResource? = withContext(Dispatchers.IO) {
        val client = AniCefApp.suspendCoroutineOnCefContext {
            AniCefApp.createClient()
        } ?: kotlin.run {
            logger.warn { "AniCefApp isn't initialized yet." }
            return@withContext null
        }

        val deferred = CompletableDeferred<WebResource>()

        val browser = AniCefApp.suspendCoroutineOnCefContext {
            val lastUrl = object {
                // broswer.url is not updated immediately, so we need to keep track of the current url.
                var value: String? by atomic(null)
            }
            client.createBrowser(
                pageUrl,
                CefRendering.DEFAULT,
                true,
                CefRequestContext.createContext { _, _, _, _, _, _, _ ->
                    object : CefResourceRequestHandlerAdapter() {
                        override fun onBeforeResourceLoad(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            request: CefRequest?
                        ): Boolean {
                            if (request != null && browser != null) {
                                if (handleUrl(request, browser)) {
                                    return true
                                }
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
                                    AniCefApp.runOnCefContext {
                                        browser.executeJavaScript("window.location.href='$url';", "", 1)
                                    }
                                    return true
                                }
                            }
                        }
                    }
                },
            )
        }
        browser.setCloseAllowed() // browser should be allowed to close.

        try {
            AniCefApp.runOnCefContext {
                client.addDisplayHandler(
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

                // set cookie
                val cookieManager = CefCookieManager.getGlobalManager()
                val url = Url(pageUrl)
                for (cookie in config.cookies) {
                    val ktorCookie = parseServerSetCookieHeader(cookie)
                    cookieManager.setCookie(url.host, ktorCookie.toCefCookie())
                }

                logger.info { "Fetching $pageUrl" }
                // start browser immediately
                browser.createImmediately()
            }

            withTimeoutOrNull(15.seconds) {
                deferred.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to get video url." }
            if (deferred.isActive) {
                deferred.cancel()
            }
            null
        } finally {
            // close browser and client asynchronously.
            AniCefApp.runOnCefContext {
                browser.close(true)
                client.dispose()
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

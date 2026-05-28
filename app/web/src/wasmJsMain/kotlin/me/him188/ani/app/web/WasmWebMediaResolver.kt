package me.him188.ani.app.web

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaDataProvider
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.source.MediaExtraFiles as MediampMediaExtraFiles
import org.openani.mediamp.source.Subtitle as MediampSubtitle

/**
 * Browser-side resolver for selector Web sources.
 *
 * Native targets use WebView/CEF to execute player pages and intercept network
 * requests. In the browser target we cannot embed that machinery, so this
 * resolver fetches the play page through the same-origin dev/prod proxy, follows
 * common iframe/player indirections, and resolves direct m3u8/mp4 URLs with the
 * source-provided [WebVideoMatcher].
 */
internal class WasmWebMediaResolver(
    private val httpClientProvider: HttpClientProvider,
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
) : MediaResolver {
    private companion object {
        private val logger = logger<WasmWebMediaResolver>()
        private val absoluteUrlRegex = Regex(
            """https?:(?:\\?/\\?/)[^"'\\\s<>]+""",
            RegexOption.IGNORE_CASE,
        )
        private val attributeUrlRegex = Regex(
            """(?:src|href|data-url|url)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
    }

    override fun supports(media: Media): Boolean = media.download is ResourceLocation.WebVideo

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): HttpStreamingMediaDataProvider {
        if (!supports(media)) throw UnsupportedMediaException(media)

        val pageUrl = (media.download as ResourceLocation.WebVideo).uri
        val matchers = matcherLoader.loadMatchers(media.mediaSourceId)
        val context = WebVideoMatcherContext(media)
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(pageUrl)

        while (queue.isNotEmpty() && visited.size < 8) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue

            matchers.matchVideo(current, context)?.let {
                logger.info { "Resolved web media from URL: ${it.m3u8Url}" }
                return it.toProvider(media, referer = pageUrl)
            }

            val html = fetchText(current, referer = pageUrl)
            val discovered = html.discoverUrls(current)
            logger.info { "Discovered ${discovered.size} URL(s) from web play page: $current" }

            for (url in discovered) {
                when (val matched = matchers.match(url, context)) {
                    is WebVideoMatcher.MatchResult.Matched -> return matched.video.toProvider(media, referer = current)
                    WebVideoMatcher.MatchResult.LoadPage -> queue.add(url)
                    WebVideoMatcher.MatchResult.Continue, null -> {
                        if (url.isLikelyVideoUrl()) {
                            return WebVideo(
                                m3u8Url = url,
                                headers = mapOf("Referer" to current),
                            ).toProvider(media, referer = current)
                        }
                        if (url.isLikelyPlayerPage()) queue.add(url)
                    }
                }
            }
        }

        resolveWithBrowser(pageUrl, media, context, matchers)?.let { return it }

        throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
    }

    private suspend fun resolveWithBrowser(
        pageUrl: String,
        media: Media,
        context: WebVideoMatcherContext,
        matchers: List<WebVideoMatcher>,
    ): HttpStreamingMediaDataProvider? {
        val response = try {
            httpClientProvider.get().use {
                get(rewriteBrowserResolverUrl(pageUrl)).bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.info(e) { "Browser web media resolver is unavailable" }
            return null
        }

        val root = try {
            Json.parseToJsonElement(response).jsonObject
        } catch (e: Throwable) {
            logger.info(e) { "Browser web media resolver returned invalid JSON" }
            return null
        }
        if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            logger.info { "Browser web media resolver failed: ${root["error"]?.jsonPrimitive?.contentOrNull}" }
            return null
        }

        val candidates = root["candidates"]?.jsonArray.orEmpty()
        logger.info { "Browser web media resolver captured ${candidates.size} candidate URL(s) for $pageUrl" }
        for (candidateElement in candidates) {
            val candidate = candidateElement as? JsonObject ?: continue
            val url = candidate["url"]?.jsonPrimitive?.contentOrNull ?: continue
            val referer = candidate["referer"]?.jsonPrimitive?.contentOrNull ?: pageUrl

            when (val matched = matchers.match(url, context)) {
                is WebVideoMatcher.MatchResult.Matched -> return matched.video.toProvider(media, referer = referer)
                WebVideoMatcher.MatchResult.LoadPage,
                WebVideoMatcher.MatchResult.Continue,
                null,
                    -> {
                    if (url.isLikelyVideoUrl()) {
                        return WebVideo(
                            m3u8Url = url,
                            headers = mapOf("Referer" to referer),
                        ).toProvider(media, referer = referer)
                    }
                }
            }
        }

        return null
    }

    private suspend fun fetchText(url: String, referer: String): String {
        return try {
            httpClientProvider.get().use {
                get(rewriteSameOriginProxyUrl(url, referer)).bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)
        }
    }

    private fun List<WebVideoMatcher>.match(
        url: String,
        context: WebVideoMatcherContext,
    ): WebVideoMatcher.MatchResult? {
        return asSequence()
            .map { matcher -> matcher.match(url, context) }
            .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
    }

    private fun List<WebVideoMatcher>.matchVideo(url: String, context: WebVideoMatcherContext): WebVideo? {
        return (match(url, context) as? WebVideoMatcher.MatchResult.Matched)?.video
    }

    private fun WebVideo.toProvider(media: Media, referer: String): HttpStreamingMediaDataProvider {
        val videoReferer = headers["Referer"] ?: headers["referer"] ?: referer
        return HttpStreamingMediaDataProvider(
            rewriteSameOriginProxyUrl(m3u8Url, videoReferer),
            media.originalTitle,
            headers,
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }

    private fun String.discoverUrls(baseUrl: String): List<String> {
        return buildList {
            absoluteUrlRegex.findAll(this@discoverUrls)
                .map { it.value.unescapeJsUrl().unescapeHtmlUrl() }
                .forEach { add(it) }

            attributeUrlRegex.findAll(this@discoverUrls)
                .mapNotNull { it.groups[1]?.value }
                .map { it.unescapeJsUrl().unescapeHtmlUrl() }
                .mapNotNull { resolveUrl(baseUrl, it) }
                .forEach { add(it) }
        }.distinct()
    }

    private fun String.isLikelyVideoUrl(): Boolean {
        val path = substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mp4")
    }

    private fun String.isLikelyPlayerPage(): Boolean {
        val lower = lowercase()
        return lower.contains("player") || lower.contains("play") || lower.contains("m3u8")
    }

    private fun resolveUrl(baseUrl: String, value: String): String? {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        if (!value.startsWith("/")) return null
        val schemeEnd = baseUrl.indexOf("://")
        if (schemeEnd == -1) return null
        val pathStart = baseUrl.indexOf('/', schemeEnd + 3).let { if (it == -1) baseUrl.length else it }
        return baseUrl.substring(0, pathStart) + value
    }

    private fun String.unescapeJsUrl(): String = replace("\\/", "/")
        .replace("\\u002F", "/")
        .replace("\\u002f", "/")

    private fun String.unescapeHtmlUrl(): String = replace("&amp;", "&")
}

internal fun rewriteSameOriginProxyUrl(url: String, referer: String? = null): String {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return url
    val refererParam = referer?.let { "&referer=${encodeURIComponent(it)}" }.orEmpty()
    return "${window.location.origin}/__animeko_proxy?url=${encodeURIComponent(url)}$refererParam"
}

internal fun rewriteBrowserResolverUrl(url: String): String {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return url
    return "${window.location.origin}/__animeko_browser_resolve?url=${encodeURIComponent(url)}"
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")

private fun MediaExtraFiles.toMediampMediaExtraFiles(): MediampMediaExtraFiles {
    return MediampMediaExtraFiles(
        subtitles = subtitles.map {
            MediampSubtitle(
                uri = it.uri,
                mimeType = it.mimeType,
                language = it.language,
            )
        },
    )
}

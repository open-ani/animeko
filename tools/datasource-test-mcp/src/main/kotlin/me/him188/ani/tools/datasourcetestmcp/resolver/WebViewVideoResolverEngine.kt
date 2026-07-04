/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.domain.media.resolver.CefVideoExtractor
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import me.him188.ani.app.platform.AniCefApp
import me.him188.ani.app.platform.Context
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcher.MatchResult
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import java.io.File
import java.util.ServiceLoader

class WebViewVideoResolverEngine(
    private val workDir: File = defaultWorkDir(),
) : CandidateVideoResolver {
    private val classpathMatchers by lazy {
        ServiceLoader.load(WebVideoMatcher::class.java).filterNotNull().toList()
    }

    override suspend fun resolve(
        media: Media,
        matchers: List<WebVideoMatcher>,
    ): ResolveResult {
        return when (val download = media.download) {
            is ResourceLocation.HttpStreamingFile -> {
                ResolveResult(
                    resolvedVideo = ResolvedVideoResult(
                        url = download.uri,
                        strategy = "http-streaming-direct",
                    ),
                    diagnostics = buildJsonObject {
                        put("mode", "direct")
                    },
                )
            }

            is ResourceLocation.WebVideo -> resolvePage(media, download.uri, matchers)
            else -> ResolveResult(
                diagnostics = buildJsonObject {
                    put("unsupportedDownloadType", download::class.simpleName ?: "unknown")
                },
                errors = listOf("Unsupported download type: ${download::class.simpleName}"),
            )
        }
    }

    override suspend fun resolvePage(
        media: Media,
        pageUrl: String,
        matchers: List<WebVideoMatcher>,
    ): ResolveResult {
        return runCatching {
            initializeCef()

            val allMatchers = (matchers + classpathMatchers).distinctBy { it.javaClass.name }
            val context = WebVideoMatcherContext(media)
            val webViewConfig = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
                matcher.patchConfig(acc)
            }

            var matchedBy: String? = null
            var matchedUrl: String? = null

            fun match(url: String): MatchResult {
                return allMatchers.asSequence()
                    .map { matcher ->
                        matcher.match(url, context).also { result ->
                            if (result is MatchResult.Matched && matchedBy == null) {
                                matchedBy = matcher.javaClass.name
                                matchedUrl = url
                            }
                        }
                    }
                    .firstOrNull { it !is MatchResult.Continue }
                    ?: MatchResult.Continue
            }

            val resource = CefVideoExtractor(
                proxyConfig = null,
                videoResolverSettings = VideoResolverSettings.Default,
            ).getVideoResourceUrl(
                context = object : Context() {},
                pageUrl = pageUrl,
                config = webViewConfig,
                resourceMatcher = { url ->
                    when (match(url)) {
                        MatchResult.Continue -> Instruction.Continue
                        MatchResult.LoadPage -> Instruction.LoadPage
                        is MatchResult.Matched -> Instruction.FoundResource
                    }
                },
            )

            val video = resource?.let {
                (match(it.url) as? MatchResult.Matched)?.video
            }

            ResolveResult(
                resolvedVideo = video?.let {
                    ResolvedVideoResult(
                        url = it.m3u8Url,
                        headers = it.headers.ifEmpty {
                            mapOf(HttpHeaders.Referrer to pageUrl)
                        },
                        strategy = "webview-cef",
                        matchedBy = matchedBy,
                        pageChain = listOf(pageUrl),
                    )
                },
                diagnostics = diagnostics(
                    pageUrl = pageUrl,
                    matchers = allMatchers,
                    matchedBy = matchedBy,
                    matchedUrl = matchedUrl,
                    cookieCount = webViewConfig.cookies.size,
                ),
                errors = if (video == null) listOf("No video URL matched by the webview resolver engine") else emptyList(),
            )
        }.getOrElse { exception ->
            ResolveResult(
                diagnostics = diagnostics(
                    pageUrl = pageUrl,
                    matchers = matchers + classpathMatchers,
                    matchedBy = null,
                    matchedUrl = null,
                    cookieCount = 0,
                    exception = exception,
                ),
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    private suspend fun initializeCef() {
        val logsDir = workDir.resolve("logs").also(File::mkdirs)
        val cacheDir = workDir.resolve("cache").also(File::mkdirs)
        AniCefApp.initialize(
            logDir = logsDir,
            cacheDir = cacheDir,
        )
    }

    private fun diagnostics(
        pageUrl: String,
        matchers: List<WebVideoMatcher>,
        matchedBy: String?,
        matchedUrl: String?,
        cookieCount: Int,
        exception: Throwable? = null,
    ): JsonElement {
        return buildJsonObject {
            put("engine", "webview-cef")
            put("pageUrl", pageUrl)
            put("workDir", workDir.absolutePath)
            put("cefLogFile", AniCefApp.currentAppLogFile?.absolutePath ?: "")
            put("matcherCount", matchers.size)
            put("cookieCount", cookieCount)
            put("matchedBy", matchedBy ?: "")
            put("matchedUrl", matchedUrl ?: "")
            put("matchers", buildJsonArray {
                matchers.forEach { add(JsonPrimitive(it.javaClass.name)) }
            })
            if (exception != null) {
                put("exception", "${exception::class.simpleName}: ${exception.message.orEmpty()}")
            }
        }
    }

    companion object {
        private fun defaultWorkDir(): File {
            return File(System.getProperty("java.io.tmpdir"))
                .resolve("ani-datasource-test-mcp")
                .resolve("cef")
        }
    }
}

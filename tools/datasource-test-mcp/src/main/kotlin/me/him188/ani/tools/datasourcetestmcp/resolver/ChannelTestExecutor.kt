/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.ApiVideoResolver
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.tools.datasourcetestmcp.video.VideoUrlProbeEngine

interface CandidateVideoResolver {
    suspend fun resolve(media: Media, matchers: List<WebVideoMatcher>): ResolveResult
    suspend fun resolvePage(media: Media, pageUrl: String, matchers: List<WebVideoMatcher>): ResolveResult
}

internal class ChannelTestExecutor(
    private val resolver: CandidateVideoResolver,
    private val probe: VideoUrlProbeEngine,
) {
    /**
     * @param apiResolverFor 产生该候选的数据源的 [ApiVideoResolver]. 由接口签发地址的资源
     * (`WebVideo.isResolvedByApi`) 不打开播放页, 直接向它取地址.
     */
    suspend fun execute(
        playableCandidates: List<MediaMatch>,
        matchersFor: (MediaMatch) -> List<WebVideoMatcher>,
        probeTimeoutMillis: Long,
        candidateTestMode: CandidateTestMode,
        probeEnabled: Boolean = true,
        apiResolverFor: (MediaMatch) -> ApiVideoResolver? = { null },
    ): List<ChannelTestResult> {
        val results = mutableListOf<ChannelTestResult>()
        playableCandidates.forEachIndexed { index, match ->
            val result = try {
                testCandidate(
                    order = index + 1,
                    match = match,
                    matchers = matchersFor(match),
                    apiResolver = apiResolverFor(match),
                    probeTimeoutMillis = probeTimeoutMillis,
                    probeEnabled = probeEnabled,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                ChannelTestResult(
                    order = index + 1,
                    candidate = match.toCandidateResult(),
                    resolveStatus = "failed",
                    probeStatus = "not_run",
                    ok = false,
                    summary = "Candidate test threw ${exception::class.simpleName}",
                    errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
                )
            }
            results += result
            if (candidateTestMode == CandidateTestMode.FIRST_SUCCESS && result.ok) {
                return results
            }
        }
        return results
    }

    private suspend fun resolveByApi(media: Media, apiResolver: ApiVideoResolver?): ResolveResult {
        if (apiResolver == null) {
            return ResolveResult(
                diagnostics = buildJsonObject { put("mode", "api") },
                errors = listOf("Media is resolved by API but its source does not provide an ApiVideoResolver"),
            )
        }
        val video = apiResolver.resolveApiVideo(media)
            ?: return ResolveResult(
                diagnostics = buildJsonObject { put("mode", "api") },
                errors = listOf("The API issued no video URL for this media"),
            )
        return ResolveResult(
            resolvedVideo = ResolvedVideoResult(
                url = video.m3u8Url,
                headers = video.headers,
                strategy = "api-issued",
            ),
            diagnostics = buildJsonObject { put("mode", "api") },
        )
    }

    private suspend fun testCandidate(
        order: Int,
        match: MediaMatch,
        matchers: List<WebVideoMatcher>,
        apiResolver: ApiVideoResolver?,
        probeTimeoutMillis: Long,
        probeEnabled: Boolean,
    ): ChannelTestResult {
        val candidate = match.toCandidateResult()
        val resolveStart = System.currentTimeMillis()
        val resolveResult = if ((match.media.download as? ResourceLocation.WebVideo)?.isResolvedByApi == true) {
            resolveByApi(match.media, apiResolver)
        } else {
            resolver.resolve(match.media, matchers)
        }
        val resolveDuration = System.currentTimeMillis() - resolveStart
        val resolvedVideo = resolveResult.resolvedVideo ?: return ChannelTestResult(
            order = order,
            candidate = candidate,
            resolveStatus = "failed",
            probeStatus = "not_run",
            ok = false,
            summary = "Failed to resolve final video URL",
            resolveDiagnostics = resolveResult.diagnostics,
            errors = resolveResult.errors,
            resolveDurationMillis = resolveDuration,
        )

        if (!probeEnabled) {
            return ChannelTestResult(
                order = order,
                candidate = candidate,
                resolveStatus = "success",
                probeStatus = "not_run",
                ok = true,
                summary = "Resolved final video URL (probe skipped)",
                resolvedVideo = resolvedVideo,
                resolveDiagnostics = resolveResult.diagnostics,
                errors = resolveResult.errors,
                resolveDurationMillis = resolveDuration,
            )
        }

        val probeStart = System.currentTimeMillis()
        val probeResult = try {
            withTimeoutOrNull(probeTimeoutMillis) {
                probe.probe(resolvedVideo.url, resolvedVideo.headers)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            return ChannelTestResult(
                order = order,
                candidate = candidate,
                resolveStatus = "success",
                probeStatus = "failed",
                ok = false,
                summary = "Resolved final video URL, but HTTP probe threw ${exception::class.simpleName}",
                resolvedVideo = resolvedVideo,
                resolveDiagnostics = resolveResult.diagnostics,
                errors = resolveResult.errors +
                        "${exception::class.simpleName}: ${exception.message.orEmpty()}",
                resolveDurationMillis = resolveDuration,
                probeDurationMillis = System.currentTimeMillis() - probeStart,
            )
        }
        if (probeResult == null) {
            return ChannelTestResult(
                order = order,
                candidate = candidate,
                resolveStatus = "success",
                probeStatus = "timeout",
                ok = false,
                summary = "Resolved final video URL, but HTTP probe timed out",
                resolvedVideo = resolvedVideo,
                resolveDiagnostics = resolveResult.diagnostics,
                errors = resolveResult.errors +
                        "HTTP probe timed out after ${probeTimeoutMillis}ms",
                resolveDurationMillis = resolveDuration,
                probeDurationMillis = System.currentTimeMillis() - probeStart,
            )
        }
        val probeDuration = System.currentTimeMillis() - probeStart
        return ChannelTestResult(
            order = order,
            candidate = candidate,
            resolveStatus = "success",
            probeStatus = if (probeResult.ok) "success" else "failed",
            ok = probeResult.ok,
            summary = probeResult.summary,
            resolvedVideo = resolvedVideo,
            probe = probeResult,
            resolveDiagnostics = resolveResult.diagnostics,
            errors = resolveResult.errors + probeResult.errors,
            resolveDurationMillis = resolveDuration,
            probeDurationMillis = probeDuration,
        )
    }
}

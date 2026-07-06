/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import kotlinx.coroutines.withTimeout
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.tools.datasourcetestmcp.video.VideoUrlProbeEngine

interface CandidateVideoResolver {
    suspend fun resolve(media: Media, matchers: List<WebVideoMatcher>): ResolveResult
    suspend fun resolvePage(media: Media, pageUrl: String, matchers: List<WebVideoMatcher>): ResolveResult
}

internal class ChannelTestExecutor(
    private val resolver: CandidateVideoResolver,
    private val probe: VideoUrlProbeEngine,
) {
    suspend fun execute(
        playableCandidates: List<MediaMatch>,
        matchersFor: (MediaMatch) -> List<WebVideoMatcher>,
        probeTimeoutMillis: Long,
        candidateTestMode: CandidateTestMode,
        probeEnabled: Boolean = true,
    ): List<ChannelTestResult> {
        val results = mutableListOf<ChannelTestResult>()
        playableCandidates.forEachIndexed { index, match ->
            val result = runCatching {
                testCandidate(
                    order = index + 1,
                    match = match,
                    matchers = matchersFor(match),
                    probeTimeoutMillis = probeTimeoutMillis,
                    probeEnabled = probeEnabled,
                )
            }.getOrElse { exception ->
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

    private suspend fun testCandidate(
        order: Int,
        match: MediaMatch,
        matchers: List<WebVideoMatcher>,
        probeTimeoutMillis: Long,
        probeEnabled: Boolean,
    ): ChannelTestResult {
        val candidate = match.toCandidateResult()
        val resolveStart = System.currentTimeMillis()
        val resolveResult = resolver.resolve(match.media, matchers)
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
        val probeResult = withTimeout(probeTimeoutMillis) {
            probe.probe(resolvedVideo.url, resolvedVideo.headers)
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

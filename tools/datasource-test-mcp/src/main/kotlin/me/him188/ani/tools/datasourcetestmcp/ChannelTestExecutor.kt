/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp

import kotlinx.coroutines.withTimeout
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource

interface CandidateVideoResolver {
    suspend fun resolve(media: Media, matchers: List<WebVideoMatcher>): ResolveResult
    suspend fun resolvePage(media: Media, pageUrl: String, matchers: List<WebVideoMatcher>): ResolveResult
}

interface VideoUrlProbeEngine {
    suspend fun probe(url: String, headers: Map<String, String>): VideoProbeResult
}

internal class ChannelTestExecutor(
    private val resolver: CandidateVideoResolver,
    private val probe: VideoUrlProbeEngine,
) {
    suspend fun execute(
        playableCandidates: List<MediaMatch>,
        sourceById: Map<String, MediaSource>,
        probeTimeoutMillis: Long,
        candidateTestMode: CandidateTestMode,
    ): List<ChannelTestResult> {
        val results = mutableListOf<ChannelTestResult>()
        playableCandidates.forEachIndexed { index, match ->
            val source = sourceById[match.media.mediaSourceId]
            val provider = source as? WebVideoMatcherProvider
            val result = runCatching {
                testCandidate(
                    order = index + 1,
                    match = match,
                    matchers = provider?.let { listOf(it.matcher) }.orEmpty(),
                    probeTimeoutMillis = probeTimeoutMillis,
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
    ): ChannelTestResult {
        val candidate = match.toCandidateResult()
        val resolveResult = resolver.resolve(match.media, matchers)
        val resolvedVideo = resolveResult.resolvedVideo ?: return ChannelTestResult(
            order = order,
            candidate = candidate,
            resolveStatus = "failed",
            probeStatus = "not_run",
            ok = false,
            summary = "Failed to resolve final video URL",
            resolveDiagnostics = resolveResult.diagnostics,
            errors = resolveResult.errors,
        )

        val probeResult = withTimeout(probeTimeoutMillis) {
            probe.probe(resolvedVideo.url, resolvedVideo.headers)
        }
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
        )
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.him188.ani.tools.datasourcetestmcp.DEFAULT_ANI_API_BASE_URL
import me.him188.ani.tools.datasourcetestmcp.StageResult
import me.him188.ani.tools.datasourcetestmcp.resolver.CandidateTestMode
import me.him188.ani.tools.datasourcetestmcp.resolver.ChannelTestResult
import me.him188.ani.tools.datasourcetestmcp.resolver.MediaCandidateResult
import me.him188.ani.tools.datasourcetestmcp.resolver.ResolvedVideoResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbeResult

@Serializable
data class MediaSourceSpec(
    val factoryId: String,
    val mediaSourceId: String? = null,
    val serializedArguments: JsonElement? = null,
    val arguments: Map<String, String?> = emptyMap(),
)

@Serializable
data class TestSubjectEpisodeSourceInput(
    val subjectId: Long,
    val episodeId: Long,
    val mediaSource: MediaSourceSpec? = null,
    val aniApiBaseUrl: String = DEFAULT_ANI_API_BASE_URL,
    val aniBearerToken: String? = null,
    val maxCandidates: Int = 10,
    val fetchTimeoutMillis: Long = 30_000,
    val probeTimeoutMillis: Long = 15_000,
    val candidateTestMode: CandidateTestMode = CandidateTestMode.ALL_CHANNELS,
)

@Serializable
data class TestResourcePageUrlInput(
    val pageUrl: String,
    val mediaSource: MediaSourceSpec? = null,
    val probeTimeoutMillis: Long = 15_000,
    val resolveDepth: Int = 3,
)

@Serializable
data class SourceTestResult(
    val ok: Boolean,
    val summary: String,
    val input: JsonElement,
    val stages: List<StageResult>,
    val candidates: List<MediaCandidateResult> = emptyList(),
    val channelResults: List<ChannelTestResult> = emptyList(),
    val selectedCandidate: MediaCandidateResult? = null,
    val resolvedVideo: ResolvedVideoResult? = null,
    val probe: VideoProbeResult? = null,
    val errors: List<String> = emptyList(),
    val totalDurationMillis: Long? = null,
)

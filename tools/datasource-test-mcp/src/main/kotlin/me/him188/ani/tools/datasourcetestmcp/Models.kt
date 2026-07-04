/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
data class ProbeVideoUrlInput(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val probeTimeoutMillis: Long = 15_000,
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
)

@Serializable
data class StageResult(
    val name: String,
    val status: String,
    val summary: String,
    val details: JsonElement? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class MediaCandidateResult(
    val mediaId: String,
    val mediaSourceId: String,
    val originalTitle: String,
    val originalUrl: String,
    val downloadUri: String,
    val downloadType: String,
    val kind: String,
    val matchKind: String,
    val episodeRange: String? = null,
)

@Serializable
enum class CandidateTestMode {
    @SerialName("all_channels")
    ALL_CHANNELS,

    @SerialName("first_success")
    FIRST_SUCCESS,
}

@Serializable
data class ChannelTestResult(
    val order: Int,
    val candidate: MediaCandidateResult,
    val resolveStatus: String,
    val probeStatus: String,
    val ok: Boolean,
    val summary: String,
    val resolvedVideo: ResolvedVideoResult? = null,
    val probe: VideoProbeResult? = null,
    val resolveDiagnostics: JsonElement? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class ResolvedVideoResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val strategy: String,
    val matchedBy: String? = null,
    val pageChain: List<String> = emptyList(),
)

@Serializable
data class VideoProbeResult(
    val ok: Boolean,
    val url: String,
    val finalUrl: String? = null,
    val kind: String,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val summary: String,
    val playlistEntries: Int? = null,
    val nestedPlaylistUrl: String? = null,
    val sampledSegmentUrl: String? = null,
    val sampledSegmentStatusCode: Int? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

const val DEFAULT_ANI_API_BASE_URL = "https://api.animeko.org"

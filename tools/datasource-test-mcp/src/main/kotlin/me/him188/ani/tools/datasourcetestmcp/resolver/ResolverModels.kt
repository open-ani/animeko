/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbeResult

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
    /**
     * WebView 解析播放页到视频 URL 的耗时
     */
    val resolveDurationMillis: Long? = null,
    /**
     * HTTP 播放探测耗时
     */
    val probeDurationMillis: Long? = null,
)

@Serializable
data class ResolvedVideoResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val strategy: String,
    val matchedBy: String? = null,
    val pageChain: List<String> = emptyList(),
)

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.selector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.him188.ani.tools.datasourcetestmcp.DEFAULT_ANI_API_BASE_URL
import me.him188.ani.tools.datasourcetestmcp.StageResult
import me.him188.ani.tools.datasourcetestmcp.resolver.CandidateTestMode
import me.him188.ani.tools.datasourcetestmcp.resolver.ChannelTestResult

@Serializable
data class ValidateSelectorConfigInput(
    /**
     * 支持三种形态: ExportedMediaSourceData / SelectorMediaSourceArguments / 裸 SelectorSearchConfig.
     * 可以直接传 JSON object, 也可以传 JSON 字符串.
     */
    val config: JsonElement,
)

@Serializable
data class ValidateSelectorConfigResult(
    val ok: Boolean,
    val summary: String,
    /**
     * `exported` / `exported-list` / `arguments` / `search-config`
     */
    val detectedFormat: String? = null,
    val sourceName: String? = null,
    val issues: List<ConfigIssue> = emptyList(),
)

@Serializable
data class ConfigIssue(
    /**
     * `error` / `warning` / `info`
     */
    val severity: String,
    /**
     * JSON 字段路径, 例如 `searchConfig.searchUrl`
     */
    val path: String,
    val message: String,
)

@Serializable
data class SelectorResolveEpisodeInput(
    val subjectId: Long,
    val episodeId: Long,
    /**
     * Selector 数据源配置 JSON, 与 [ValidateSelectorConfigInput.config] 接受相同形态.
     */
    val config: JsonElement,
    val aniApiBaseUrl: String = DEFAULT_ANI_API_BASE_URL,
    val aniBearerToken: String? = null,
    /**
     * 每个搜索词最多访问多少个条目详情页
     */
    val maxSubjectsPerName: Int = 3,
    /**
     * 是否用 WebView (CEF) 把播放页解析成最终视频 URL
     */
    val extractVideo: Boolean = true,
    /**
     * 解析出视频 URL 后是否做 HTTP 可达性探测
     */
    val probeVideo: Boolean = true,
    val maxCandidatesToExtract: Int = 3,
    val extractMode: CandidateTestMode = CandidateTestMode.FIRST_SUCCESS,
    val probeTimeoutMillis: Long = 15_000,
)

@Serializable
data class SelectorResolveEpisodeResult(
    val ok: Boolean,
    val summary: String,
    /**
     * 每个 engine 步骤的执行记录, 按时间顺序. 同一步骤名可能出现多次 (对应不同搜索词/条目).
     */
    val steps: List<StageResult>,
    /**
     * selectMedia 之后的最终候选 (filteredList)
     */
    val medias: List<WebMediaCandidate> = emptyList(),
    val extractResults: List<ChannelTestResult> = emptyList(),
    val errors: List<String> = emptyList(),
    val totalDurationMillis: Long? = null,
)

@Serializable
data class WebMediaCandidate(
    val mediaId: String,
    val subjectName: String,
    val channel: String? = null,
    val episodeName: String,
    val episodeSort: String? = null,
    val playUrl: String,
)

@Serializable
enum class SelectorStep {
    @SerialName("searchSubjects")
    SEARCH_SUBJECTS,

    @SerialName("selectSubjects")
    SELECT_SUBJECTS,

    @SerialName("searchEpisodes")
    SEARCH_EPISODES,

    @SerialName("selectEpisodes")
    SELECT_EPISODES,

    @SerialName("selectMedia")
    SELECT_MEDIA,

    @SerialName("matchWebVideo")
    MATCH_WEB_VIDEO,

    @SerialName("extractVideo")
    EXTRACT_VIDEO,
}

@Serializable
data class SelectorRunStepInput(
    val step: SelectorStep,
    /**
     * Selector 配置 JSON. 除 searchEpisodes 外的步骤都需要.
     */
    val config: JsonElement? = null,
    /**
     * searchSubjects: 搜索关键词 (通常是番剧名)
     */
    val keyword: String? = null,
    /**
     * selectSubjects / selectEpisodes: 页面 URL (在线获取);
     * searchEpisodes: 条目详情页 URL;
     * matchWebVideo: 要测试的 URL;
     * extractVideo: 播放页 URL
     */
    val url: String? = null,
    /**
     * selectSubjects / selectEpisodes: 直接提供 HTML (离线调试), 优先于 [url]
     */
    val html: String? = null,
    /**
     * selectEpisodes: episode 所属条目详情页的完整 URL (用于计算相对链接), html 模式下必填
     */
    val subjectUrl: String? = null,
    /**
     * selectMedia: 剧集列表 (通常来自 selectEpisodes 的输出)
     */
    val episodes: List<EpisodeInfoInput>? = null,
    /**
     * selectMedia: 查询上下文
     */
    val query: SelectorQueryInput? = null,
    /**
     * 返回的 HTML 最大长度, 超出截断
     */
    val maxHtmlLength: Int = 100_000,
    /**
     * extractVideo: 解析成功后是否 HTTP 探测
     */
    val probeResolvedVideo: Boolean = false,
    val probeTimeoutMillis: Long = 15_000,
)

@Serializable
data class EpisodeInfoInput(
    val channel: String? = null,
    val name: String,
    val episodeSort: String? = null,
    val playUrl: String,
)

@Serializable
data class SelectorQueryInput(
    val subjectName: String,
    val episodeSort: String,
    val allSubjectNames: List<String> = emptyList(),
    val episodeEp: String? = null,
    val episodeName: String? = null,
)

@Serializable
data class SelectorRunStepResult(
    val step: SelectorStep,
    val ok: Boolean,
    val summary: String,
    val durationMillis: Long,
    val details: JsonElement? = null,
    val errors: List<String> = emptyList(),
)

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.tools.datasourcetestmcp.DEFAULT_ANI_API_BASE_URL
import me.him188.ani.tools.datasourcetestmcp.info.AniInfoService
import me.him188.ani.tools.datasourcetestmcp.info.GetSubjectEpisodesInput
import me.him188.ani.tools.datasourcetestmcp.info.GetSubjectEpisodesResult
import me.him188.ani.tools.datasourcetestmcp.info.GetTrendsInput
import me.him188.ani.tools.datasourcetestmcp.info.GetTrendsResult
import me.him188.ani.tools.datasourcetestmcp.info.SearchSubjectsInput
import me.him188.ani.tools.datasourcetestmcp.info.SearchSubjectsResult
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorConfigSupport
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorEngineService
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorResolveEpisodeInput
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorResolveEpisodeResult
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorRunStepInput
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorRunStepResult
import me.him188.ani.tools.datasourcetestmcp.selector.ValidateSelectorConfigInput
import me.him188.ani.tools.datasourcetestmcp.selector.ValidateSelectorConfigResult
import me.him188.ani.tools.datasourcetestmcp.source.SourceTestResult
import me.him188.ani.tools.datasourcetestmcp.source.SourceTestService
import me.him188.ani.tools.datasourcetestmcp.source.TestResourcePageUrlInput
import me.him188.ani.tools.datasourcetestmcp.source.TestSubjectEpisodeSourceInput
import me.him188.ani.tools.datasourcetestmcp.video.DetectHlsAdsInput
import me.him188.ani.tools.datasourcetestmcp.video.DetectHlsAdsResult
import me.him188.ani.tools.datasourcetestmcp.video.ProbeVideoInput
import me.him188.ani.tools.datasourcetestmcp.video.ProbeVideoResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoService

/**
 * MCP 能力清单:
 *
 * 信息能力
 * - `search_subjects` 用名字搜索番剧
 * - `get_subject_episodes` 获取番剧的剧集列表
 * - `get_trends` 获取热门趋势番剧排行
 *
 * 数据源能力 (selector / CSS-selector 数据源)
 * - `validate_selector_config` 校验配置 JSON
 * - `selector_resolve_episode` 全流程解析 episode 的可播放链接, 带每步 trace
 * - `selector_run_step` 单步调试
 * - `get_selector_engine_docs` 引擎步骤文档
 *
 * 视频能力
 * - `probe_video` 探测视频 URL 的可播放性与真实媒体信息
 * - `detect_hls_ads` 不播放, 仅从 m3u8 结构 + Ani 客户端广告过滤器判断插入广告
 *
 * 兼容保留
 * - `test_subject_episode_source` 任意数据源端到端测试 (含 BT 源)
 * - `test_resource_page_url` 任意播放页 WebView 解析
 */
fun buildToolRegistrations(
    json: Json,
    aniInfoService: AniInfoService,
    selectorEngineService: SelectorEngineService,
    videoService: VideoService,
    sourceTestService: SourceTestService,
): List<McpToolRegistration> = listOf(
    // region 信息能力
    McpToolRegistration(
        McpTool(
            name = "search_subjects",
            description = "Search Animeko subjects (anime) by name via the Ani API. " +
                    "Returns subject IDs, names, air dates; set includeEpisodes to also return each subject's episode list.",
            inputSchema = objectSchema(required = listOf("query")) {
                put("query", stringSchema("Subject name or keyword to search for"))
                put("limit", integerSchema("Max subjects to return, default 10"))
                put("offset", integerSchema("Pagination offset, default 0"))
                put("includeEpisodes", booleanSchema("Also fetch each subject's episode list, default false"))
                put("aniApiBaseUrl", stringSchema("Ani API base URL, default $DEFAULT_ANI_API_BASE_URL"))
                put("aniBearerToken", stringSchema("Optional Ani bearer token"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(SearchSubjectsInput.serializer(), args)
        json.encodeToJsonElement(SearchSubjectsResult.serializer(), aniInfoService.searchSubjects(input))
    },

    McpToolRegistration(
        McpTool(
            name = "get_subject_episodes",
            description = "Get an Animeko subject's info and full episode list (episode IDs, sort numbers, names) by subject ID.",
            inputSchema = objectSchema(required = listOf("subjectId")) {
                put("subjectId", integerSchema("Ani subject ID (from search_subjects)"))
                put("aniApiBaseUrl", stringSchema("Ani API base URL, default $DEFAULT_ANI_API_BASE_URL"))
                put("aniBearerToken", stringSchema("Optional Ani bearer token"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(GetSubjectEpisodesInput.serializer(), args)
        json.encodeToJsonElement(GetSubjectEpisodesResult.serializer(), aniInfoService.getSubjectEpisodes(input))
    },

    McpToolRegistration(
        McpTool(
            name = "get_trends",
            description = "Get the current trending (hot) anime subjects ranked by popularity, from the Ani API. " +
                    "Returned subjectId can be passed to get_subject_episodes / selector_resolve_episode. " +
                    "Useful for picking well-known, currently-airing subjects to test a media source against.",
            inputSchema = objectSchema(required = emptyList()) {
                put("limit", integerSchema("Max subjects to return, default 20; <= 0 for all"))
                put("aniApiBaseUrl", stringSchema("Ani API base URL, default $DEFAULT_ANI_API_BASE_URL"))
                put("aniBearerToken", stringSchema("Optional Ani bearer token"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(GetTrendsInput.serializer(), args)
        json.encodeToJsonElement(GetTrendsResult.serializer(), aniInfoService.getTrends(input))
    },
    // endregion

    // region 数据源能力
    McpToolRegistration(
        McpTool(
            name = "validate_selector_config",
            description = "Validate a selector (CSS-selector web) media source config JSON without network access. " +
                    "Accepts an exported config {factoryId, version, arguments}, bare arguments {name, searchConfig}, " +
                    "a bare searchConfig {searchUrl, ...}, or a subscription list {mediaSources: [...]}. " +
                    "Checks required fields, CSS selector syntax, JsonPath syntax, and regex syntax/named groups; " +
                    "reports per-field errors and warnings.",
            inputSchema = objectSchema(required = listOf("config")) {
                put("config", anySchema("The selector media source config, as a JSON object or a JSON string"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(ValidateSelectorConfigInput.serializer(), args)
        json.encodeToJsonElement(ValidateSelectorConfigResult.serializer(), SelectorConfigSupport.validate(input, json))
    },

    McpToolRegistration(
        McpTool(
            name = "selector_resolve_episode",
            description = "Resolve playable video links for an Ani episode using a selector media source config, " +
                    "by running the full SelectorMediaSourceEngine pipeline: " +
                    "searchSubjects -> selectSubjects -> searchEpisodes -> selectEpisodes -> selectMedia, " +
                    "then optionally WebView (CEF) video extraction and a playback probe. " +
                    "Returns a per-step trace (inputs, parsed results, errors) for debugging. " +
                    "Call get_selector_engine_docs for step semantics.",
            inputSchema = objectSchema(required = listOf("subjectId", "episodeId", "config")) {
                put("subjectId", integerSchema("Ani subject ID"))
                put("episodeId", integerSchema("Ani episode ID (from get_subject_episodes)"))
                put("config", anySchema("Selector media source config JSON, same forms as validate_selector_config"))
                put("maxSubjectsPerName", integerSchema("Max subject detail pages to visit per search keyword, default 3"))
                put("extractVideo", booleanSchema("Run WebView video URL extraction on matched medias, default true"))
                put("probeVideo", booleanSchema("HTTP-probe extracted video URLs, default true"))
                put("maxCandidatesToExtract", integerSchema("Max media candidates to run extraction on, default 3"))
                put(
                    "extractMode",
                    enumSchema(
                        "Extraction mode, default first_success",
                        "first_success", "all_channels",
                    ),
                )
                put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds, default 15000"))
                put("aniApiBaseUrl", stringSchema("Ani API base URL, default $DEFAULT_ANI_API_BASE_URL"))
                put("aniBearerToken", stringSchema("Optional Ani bearer token"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(SelectorResolveEpisodeInput.serializer(), args)
        json.encodeToJsonElement(SelectorResolveEpisodeResult.serializer(), selectorEngineService.resolveEpisode(input))
    },

    McpToolRegistration(
        McpTool(
            name = "selector_run_step",
            description = "Run a single SelectorMediaSourceEngine step, for debugging why a step yields no data. " +
                    "Steps and their params: " +
                    "searchSubjects(config, keyword) fetches the search page and returns HTML; " +
                    "selectSubjects(config, url|html) parses subjects from a search result page; " +
                    "searchEpisodes(url) fetches a subject detail page and returns HTML; " +
                    "selectEpisodes(config, url|html+subjectUrl) parses channels/episodes from a detail page; " +
                    "selectMedia(config, episodes, query) converts episodes to medias and applies filters; " +
                    "matchWebVideo(config, url) tests whether a URL matches the video/nested-page regexes (offline); " +
                    "extractVideo(url, config?) loads a play page in a real WebView and intercepts the video URL. " +
                    "Call get_selector_engine_docs for details.",
            inputSchema = objectSchema(required = listOf("step")) {
                put(
                    "step",
                    enumSchema(
                        "Engine step to run",
                        "searchSubjects", "selectSubjects", "searchEpisodes",
                        "selectEpisodes", "selectMedia", "matchWebVideo", "extractVideo",
                    ),
                )
                put("config", anySchema("Selector config JSON; required by all steps except searchEpisodes/extractVideo"))
                put("keyword", stringSchema("searchSubjects: search keyword, usually the subject name"))
                put(
                    "url",
                    stringSchema(
                        "Page URL to fetch (selectSubjects/selectEpisodes), subject detail page URL (searchEpisodes), " +
                                "URL to test (matchWebVideo), or play page URL (extractVideo)",
                    ),
                )
                put("html", stringSchema("selectSubjects/selectEpisodes: parse this HTML instead of fetching url (offline debug)"))
                put("subjectUrl", stringSchema("selectEpisodes: full URL of the subject detail page, used to resolve relative links"))
                put(
                    "episodes",
                    buildJsonObject {
                        put("type", "array")
                        put("description", JsonPrimitive("selectMedia: episode list, usually from selectEpisodes output"))
                        put(
                            "items",
                            objectSchema(required = listOf("name", "playUrl")) {
                                put("channel", stringSchema("Channel (播放线路) name"))
                                put("name", stringSchema("Episode display name, e.g. 第1集"))
                                put("episodeSort", stringSchema("Parsed episode sort, e.g. 1"))
                                put("playUrl", stringSchema("Play page URL"))
                            },
                        )
                    },
                )
                put(
                    "query",
                    objectSchema(required = listOf("subjectName", "episodeSort")) {
                        put("subjectName", stringSchema("Subject name used for filtering"))
                        put("episodeSort", stringSchema("Target episode sort, e.g. 1"))
                        put(
                            "allSubjectNames",
                            buildJsonObject {
                                put("type", "array")
                                put("items", stringSchema("Alias"))
                                put("description", JsonPrimitive("All known subject names, default [subjectName]"))
                            },
                        )
                        put("episodeEp", stringSchema("Target episode ep (season-local sort)"))
                        put("episodeName", stringSchema("Target episode name"))
                    },
                )
                put("maxHtmlLength", integerSchema("Max HTML characters to return, default 100000"))
                put("probeResolvedVideo", booleanSchema("extractVideo: HTTP-probe the resolved video URL, default false"))
                put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds, default 15000"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(SelectorRunStepInput.serializer(), args)
        json.encodeToJsonElement(SelectorRunStepResult.serializer(), selectorEngineService.runStep(input))
    },

    McpToolRegistration(
        McpTool(
            name = "get_selector_engine_docs",
            description = "Get the markdown documentation of SelectorMediaSourceEngine: what each pipeline step does, " +
                    "its inputs/outputs, the related config fields, and how steps map to the selector_* MCP tools.",
            inputSchema = objectSchema(required = emptyList()) {},
        ),
    ) {
        buildJsonObject {
            put("ok", true)
            put("markdown", SelectorEngineDocs.load())
        }
    },
    // endregion

    // region 视频能力
    McpToolRegistration(
        McpTool(
            name = "probe_video",
            description = "Test whether a final video URL (m3u8/mp4/mkv...) is actually playable in Animeko and report its real media info. " +
                    "Runs an HTTP reachability probe, then plays the URL for a few seconds with Animeko's own mpv pipeline " +
                    "(mediamp-mpv, the same player core the desktop app uses on this platform), " +
                    "reporting resolution, duration, codecs, frame rate and bitrate. " +
                    "By default a Compose test window pops up showing the actual playback. " +
                    "mpv native libs are loaded automatically (override the directory with -Dani.mpv.native.dir); " +
                    "without them only the HTTP probe runs.",
            inputSchema = objectSchema(required = listOf("videoUrl")) {
                put("videoUrl", stringSchema("Final video URL"))
                put(
                    "headers",
                    buildJsonObject {
                        put("type", "object")
                        put("description", JsonPrimitive("Request headers, e.g. Referer / User-Agent"))
                    },
                )
                put("probeTimeoutMillis", integerSchema("HTTP probe timeout in milliseconds, default 15000"))
                put("analyze", booleanSchema("Run the real-player playback test, default true"))
                put("playSeconds", integerSchema("Seconds to actually play for the playability test, default 5"))
                put("playTimeoutMillis", integerSchema("Timeout for entering/finishing playback in milliseconds, default 60000"))
                put("showWindow", booleanSchema("Show a Compose window with the live playback, default true"))
                put(
                    "detectAds",
                    booleanSchema(
                        "Analyze the HLS playlist for ad splicing (discontinuities/foreign hosts), default true; " +
                                "also reports whether Animeko's client-side HLS ad filter would remove the segments",
                    ),
                )
                put("captureFramesDir", stringSchema("If set, save first-frame and mid PNG screenshots into this dir for visual ad inspection"))
                put(
                    "captureAtSeconds",
                    buildJsonObject {
                        put("type", "array")
                        put("items", integerSchema("Playback position in seconds"))
                        put("description", JsonPrimitive("Also capture a frame at each of these playback positions (frame_XXs.png); playSeconds must cover the max"))
                    },
                )
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(ProbeVideoInput.serializer(), args)
        json.encodeToJsonElement(ProbeVideoResult.serializer(), videoService.probeVideo(input))
    },

    McpToolRegistration(
        McpTool(
            name = "detect_hls_ads",
            description = "Heuristically detect injected ads in an HLS (m3u8) stream WITHOUT playing it. " +
                    "Fetches the playlist (follows master -> media), runs structural heuristics " +
                    "(EXT-X-DISCONTINUITY markers, foreign segment hosts, duration outliers) AND Animeko's real " +
                    "client-side HLS ad filter (HlsManifestFilter) to report whether the ad segments would be " +
                    "auto-filtered in the app. " +
                    "Returns suspected ad groups with start/end time offsets in seconds, so the caller can capture " +
                    "frames at those positions (e.g. probe_video captureAtSeconds) for visual confirmation. " +
                    "Note: burned-in watermark/banner ads are invisible to playlist analysis - " +
                    "frame inspection is still required for those.",
            inputSchema = objectSchema(required = listOf("url")) {
                put("url", stringSchema("HLS playlist (m3u8) URL, master or media"))
                put(
                    "headers",
                    buildJsonObject {
                        put("type", "object")
                        put("description", JsonPrimitive("Request headers, e.g. Referer / User-Agent"))
                    },
                )
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(DetectHlsAdsInput.serializer(), args)
        json.encodeToJsonElement(DetectHlsAdsResult.serializer(), videoService.detectHlsAds(input))
    },
    // endregion

    // region 兼容保留: 通用数据源端到端测试
    McpToolRegistration(
        McpTool(
            name = "test_subject_episode_source",
            description = "End-to-end test of any media source factory (dmhy/mikan/selector/...): " +
                    "fetch Ani subject/episode metadata, query the datasource, resolve a final video URL, and probe playback. " +
                    "For debugging selector sources prefer selector_resolve_episode which returns per-step traces.",
            inputSchema = objectSchema(required = listOf("subjectId", "episodeId")) {
                put("subjectId", integerSchema("Ani subject ID"))
                put("episodeId", integerSchema("Ani episode ID"))
                put("aniApiBaseUrl", stringSchema("Ani API base URL"))
                put("aniBearerToken", stringSchema("Optional Ani bearer token"))
                put("maxCandidates", integerSchema("Max media candidates to collect"))
                put("fetchTimeoutMillis", integerSchema("Fetch timeout in milliseconds"))
                put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds"))
                put(
                    "candidateTestMode",
                    enumSchema("Candidate testing mode, default all_channels", "all_channels", "first_success"),
                )
                put("mediaSource", mediaSourceSchema())
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(TestSubjectEpisodeSourceInput.serializer(), args)
        json.encodeToJsonElement(SourceTestResult.serializer(), sourceTestService.testSubjectEpisodeSource(input))
    },

    McpToolRegistration(
        McpTool(
            name = "test_resource_page_url",
            description = "Resolve a final video URL from a resource or playback page with the real WebView-based resolver, " +
                    "then probe playback reachability. Equivalent to selector_run_step(step=extractVideo) plus probing.",
            inputSchema = objectSchema(required = listOf("pageUrl")) {
                put("pageUrl", stringSchema("Resource page or playback page URL"))
                put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds"))
                put("resolveDepth", integerSchema("Max nested page traversal depth"))
                put("mediaSource", mediaSourceSchema())
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(TestResourcePageUrlInput.serializer(), args)
        json.encodeToJsonElement(SourceTestResult.serializer(), sourceTestService.testResourcePageUrl(input))
    },
    // endregion
)

/**
 * 引擎步骤文档, 打包在 jar 资源中.
 */
object SelectorEngineDocs {
    fun load(): String {
        val stream = checkNotNull(SelectorEngineDocs::class.java.getResourceAsStream("/selector-engine-docs.md")) {
            "selector-engine-docs.md not found in classpath"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}

// region schema helpers

private inline fun objectSchema(
    required: List<String>,
    properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
): JsonObject = buildJsonObject {
    put("type", "object")
    if (required.isNotEmpty()) {
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
    put("properties", buildJsonObject(properties))
}

private fun stringSchema(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", JsonPrimitive(description))
}

private fun integerSchema(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", JsonPrimitive(description))
}

private fun booleanSchema(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", JsonPrimitive(description))
}

/** 不限制类型 (JSON object 或 JSON 字符串均可) */
private fun anySchema(description: String): JsonObject = buildJsonObject {
    put("description", JsonPrimitive(description))
}

private fun enumSchema(description: String, vararg values: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", JsonPrimitive(description))
    put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}

private fun mediaSourceSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    put("required", buildJsonArray { add(JsonPrimitive("factoryId")) })
    put(
        "properties",
        buildJsonObject {
            put("factoryId", stringSchema("Datasource factory ID"))
            put("mediaSourceId", stringSchema("Datasource instance ID"))
            put(
                "serializedArguments",
                buildJsonObject {
                    put("description", JsonPrimitive("Datasource serialized arguments JSON"))
                },
            )
            put(
                "arguments",
                buildJsonObject {
                    put("type", "object")
                    put("description", JsonPrimitive("Legacy string arguments for datasource factories"))
                },
            )
        },
    )
}

// endregion

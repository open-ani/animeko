/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.selector

import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.him188.ani.app.domain.mediasource.web.DefaultSelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.domain.mediasource.web.WebPageCaptchaException
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.tools.datasourcetestmcp.StageResult
import me.him188.ani.tools.datasourcetestmcp.info.AniInfoService
import me.him188.ani.tools.datasourcetestmcp.resolver.CandidateVideoResolver
import me.him188.ani.tools.datasourcetestmcp.resolver.ChannelTestExecutor
import me.him188.ani.tools.datasourcetestmcp.resolver.ResolvedVideoResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbeResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoUrlProbeEngine
import me.him188.ani.utils.xml.Document

private const val MCP_MEDIA_SOURCE_ID = "mcp-selector"

/** 与 SelectorMediaSource 保持一致: 搜索 OVA 条目时用 "OVA" 作为 epSort */
private val REGEX_OVA_TAILING = Regex(".+OVA\\s*\\d*$", RegexOption.IGNORE_CASE)

/**
 * 数据源能力: 直接驱动 [DefaultSelectorMediaSourceEngine] 跑完整解析流程或单个步骤,
 * 并记录每一步的输入输出, 用于调试 selector 配置.
 */
class SelectorEngineService(
    private val engine: DefaultSelectorMediaSourceEngine,
    private val aniInfoService: AniInfoService,
    private val json: Json,
    private val resolver: CandidateVideoResolver,
    private val probe: VideoUrlProbeEngine,
) {
    private val channelTestExecutor = ChannelTestExecutor(resolver, probe)

    // region 全流程

    suspend fun resolveEpisode(input: SelectorResolveEpisodeInput): SelectorResolveEpisodeResult {
        val start = System.currentTimeMillis()
        return resolveEpisodeImpl(input).copy(totalDurationMillis = System.currentTimeMillis() - start)
    }

    private suspend fun resolveEpisodeImpl(input: SelectorResolveEpisodeInput): SelectorResolveEpisodeResult {
        val steps = mutableListOf<StageResult>()
        val errors = mutableListOf<String>()

        val arguments = try {
            SelectorConfigSupport.parseSelectorArguments(input.config, json).arguments
        } catch (e: IllegalArgumentException) {
            return SelectorResolveEpisodeResult(
                ok = false,
                summary = "配置解析失败: ${e.message.orEmpty()}",
                steps = steps,
                errors = listOf(e.message.orEmpty()),
            )
        }
        val config = arguments.searchConfig

        val context = runStage(
            "aniMetadata", steps,
            describe = { context ->
                StageOutput(
                    summary = "条目 \"${context.subjectDisplayName}\" 第 ${context.episodeSort} 话, " +
                            "共 ${context.subjectNames.size} 个搜索词",
                    details = buildJsonObject {
                        putJsonArray("subjectNames") { context.subjectNames.forEach { add(JsonPrimitive(it)) } }
                        put("episodeSort", context.episodeSort)
                        put("episodeEp", context.episodeEp ?: "")
                        put("episodeName", context.episodeName ?: "")
                    },
                )
            },
        ) {
            aniInfoService.fetchEpisodeQueryContext(
                input.subjectId, input.episodeId, input.aniApiBaseUrl, input.aniBearerToken,
            )
        } ?: return SelectorResolveEpisodeResult(
            ok = false,
            summary = "Ani API 元数据获取失败",
            steps = steps,
            errors = steps.lastOrNull()?.errors.orEmpty(),
        )

        val allSubjectNames = context.subjectNames.toSet()
        val medias = mutableListOf<DefaultMedia>()

        val namesToSearch = context.subjectNames.take(config.searchUseSubjectNamesCount.coerceAtLeast(1))
        for ((nameIndex, subjectName) in namesToSearch.withIndex()) {
            if (nameIndex > 0) {
                delay(config.requestInterval)
            }
            val query = SelectorSearchQuery(
                subjectName = subjectName,
                episodeSort = if (subjectName.matches(REGEX_OVA_TAILING)) {
                    EpisodeSort("OVA")
                } else {
                    EpisodeSort(context.episodeSort)
                },
                allSubjectNames = allSubjectNames,
                episodeEp = context.episodeEp?.let(::EpisodeSort),
                episodeName = context.episodeName,
            )

            val searchResult = runStage(
                "searchSubjects", steps,
                describe = { result ->
                    StageOutput(
                        summary = when {
                            result.captchaKind != null ->
                                "搜索 \"$subjectName\" 被拦截, 需要人机验证 (${result.captchaKind})"

                            result.document == null -> "搜索 \"$subjectName\" 返回 404"
                            else -> "搜索 \"$subjectName\" 成功"
                        },
                        details = buildJsonObject {
                            put("subjectName", subjectName)
                            put("finalUrl", result.url.toString())
                            put("captchaKind", result.captchaKind?.toString() ?: "")
                            put("documentLength", result.document?.outerHtml()?.length ?: 0)
                        },
                        failed = result.document == null,
                    )
                },
            ) {
                engine.searchSubjects(
                    searchUrl = config.searchUrl,
                    subjectName = subjectName,
                    useOnlyFirstWord = config.searchUseOnlyFirstWord,
                    removeSpecial = config.searchRemoveSpecial,
                )
            } ?: continue
            val document = searchResult.document ?: continue

            val subjects = runStage(
                "selectSubjects", steps,
                describe = { subjects ->
                    StageOutput(
                        summary = "解析出 ${subjects.size} 个条目",
                        details = buildJsonObject {
                            put("subjectName", subjectName)
                            put("count", subjects.size)
                            put("subjects", subjectsJson(subjects, cap = 20))
                        },
                        failed = subjects.isEmpty(),
                    )
                },
            ) {
                engine.selectSubjects(document, config)
                    ?: error("配置无效: 条目格式 (subjectFormat) 的必填项为空或 selector 语法错误")
            } ?: continue
            if (subjects.isEmpty()) continue

            for (subjectInfo in subjects.take(input.maxSubjectsPerName.coerceAtLeast(1))) {
                val episodeDocument = runStage(
                    "searchEpisodes", steps,
                    describe = { document: Document ->
                        StageOutput(
                            summary = "已获取条目 \"${subjectInfo.name}\" 的详情页",
                            details = buildJsonObject {
                                put("subjectUrl", subjectInfo.fullUrl)
                                put("documentLength", document.outerHtml().length)
                            },
                        )
                    },
                ) {
                    engine.searchEpisodes(subjectInfo.fullUrl)
                        ?: error("条目详情页返回 404: ${subjectInfo.fullUrl}")
                } ?: continue

                val selected = runStage(
                    "selectEpisodes", steps,
                    describe = { selected ->
                        StageOutput(
                            summary = "解析出 ${selected.episodes.size} 个剧集" +
                                    (selected.channels?.let { ", ${it.size} 条线路" } ?: ""),
                            details = buildJsonObject {
                                put("subjectUrl", subjectInfo.fullUrl)
                                selected.channels?.let { channels ->
                                    putJsonArray("channels") { channels.forEach { add(JsonPrimitive(it)) } }
                                }
                                put("episodes", episodesJson(selected.episodes, cap = 50))
                            },
                            failed = selected.episodes.isEmpty(),
                        )
                    },
                ) {
                    engine.selectEpisodes(episodeDocument, subjectInfo.fullUrl, config)
                        ?: error("配置无效: 剧集格式 (channelFormat) 的必填项为空或 selector/正则语法错误")
                } ?: continue

                runStage(
                    "selectMedia", steps,
                    describe = { result ->
                        val filteredOut = result.originalList - result.filteredList.toSet()
                        StageOutput(
                            summary = "过滤后剩 ${result.filteredList.size}/${result.originalList.size} 个候选 " +
                                    "(目标: 第 ${query.episodeSort} 话)",
                            details = buildJsonObject {
                                put("originalCount", result.originalList.size)
                                put("filteredCount", result.filteredList.size)
                                put("filteredOut", mediasJson(filteredOut.map { it.toWebMediaCandidate() }, cap = 20))
                            },
                            failed = result.filteredList.isEmpty(),
                        )
                    },
                ) {
                    engine.selectMedia(
                        episodes = selected.episodes.asSequence(),
                        config = config,
                        query = query,
                        mediaSourceId = MCP_MEDIA_SOURCE_ID,
                        subjectName = subjectInfo.name,
                    )
                }?.let { result ->
                    medias += result.filteredList
                }
            }
        }

        val distinctMedias = medias.distinctBy { it.mediaId }
        val candidates = distinctMedias.map { it.toWebMediaCandidate() }

        if (distinctMedias.isEmpty()) {
            return SelectorResolveEpisodeResult(
                ok = false,
                summary = "引擎全流程执行完毕, 但没有找到匹配的剧集",
                steps = steps,
                errors = steps.filter { it.status == "failed" }.flatMap { it.errors },
            )
        }

        if (!input.extractVideo) {
            return SelectorResolveEpisodeResult(
                ok = true,
                summary = "找到 ${distinctMedias.size} 个候选播放页 (未运行视频解析)",
                steps = steps,
                medias = candidates,
            )
        }

        val matcher = SelectorWebVideoMatcher(engine, config.matchVideo)
        val extractStart = System.currentTimeMillis()
        val extractResults = channelTestExecutor.execute(
            playableCandidates = distinctMedias
                .take(input.maxCandidatesToExtract.coerceAtLeast(1))
                .map { MediaMatch(it, MatchKind.FUZZY) },
            matchersFor = { listOf(matcher) },
            probeTimeoutMillis = input.probeTimeoutMillis,
            candidateTestMode = input.extractMode,
            probeEnabled = input.probeVideo,
        )
        errors += extractResults.flatMap { it.errors }

        val resolved = extractResults.filter { it.resolvedVideo != null }
        val playable = extractResults.filter { it.ok }
        steps += StageResult(
            name = "extractVideo",
            status = if (resolved.isNotEmpty()) "success" else "failed",
            summary = if (input.probeVideo) {
                "视频解析: ${resolved.size}/${extractResults.size} 个候选解析出视频 URL, ${playable.size} 个通过播放探测"
            } else {
                "视频解析: ${resolved.size}/${extractResults.size} 个候选解析出视频 URL (未探测)"
            },
            errors = extractResults.filter { it.resolveStatus == "failed" }.flatMap { it.errors },
            durationMillis = System.currentTimeMillis() - extractStart,
        )

        return SelectorResolveEpisodeResult(
            ok = playable.isNotEmpty(),
            summary = when {
                playable.isNotEmpty() -> "成功: ${playable.size}/${extractResults.size} 个候选可播放"
                resolved.isNotEmpty() -> "解析出视频 URL, 但未通过播放探测"
                else -> "找到候选播放页, 但未能解析出视频 URL"
            },
            steps = steps,
            medias = candidates,
            extractResults = extractResults,
            errors = errors,
        )
    }

    // endregion

    // region 单步执行

    suspend fun runStep(input: SelectorRunStepInput): SelectorRunStepResult {
        val start = System.currentTimeMillis()
        return try {
            val result = when (input.step) {
                SelectorStep.SEARCH_SUBJECTS -> stepSearchSubjects(input)
                SelectorStep.SELECT_SUBJECTS -> stepSelectSubjects(input)
                SelectorStep.SEARCH_EPISODES -> stepSearchEpisodes(input)
                SelectorStep.SELECT_EPISODES -> stepSelectEpisodes(input)
                SelectorStep.SELECT_MEDIA -> stepSelectMedia(input)
                SelectorStep.MATCH_WEB_VIDEO -> stepMatchWebVideo(input)
                SelectorStep.EXTRACT_VIDEO -> stepExtractVideo(input)
            }
            result.copy(durationMillis = System.currentTimeMillis() - start)
        } catch (e: WebPageCaptchaException) {
            SelectorRunStepResult(
                step = input.step,
                ok = false,
                summary = "页面被人机验证拦截 (${e.kind})",
                durationMillis = System.currentTimeMillis() - start,
                errors = listOf("WebPageCaptchaException: url=${e.url}, kind=${e.kind}"),
            )
        } catch (e: Exception) {
            SelectorRunStepResult(
                step = input.step,
                ok = false,
                summary = "步骤执行失败: ${e.message.orEmpty()}",
                durationMillis = System.currentTimeMillis() - start,
                errors = listOf("${e::class.simpleName}: ${e.message.orEmpty()}"),
            )
        }
    }

    private suspend fun stepSearchSubjects(input: SelectorRunStepInput): SelectorRunStepResult {
        val config = requireConfig(input)
        val keyword = requireNotNull(input.keyword) { "searchSubjects 步骤需要 keyword 参数" }
        val result = engine.searchSubjects(
            searchUrl = config.searchUrl,
            subjectName = keyword,
            useOnlyFirstWord = config.searchUseOnlyFirstWord,
            removeSpecial = config.searchRemoveSpecial,
        )
        val html = result.document?.outerHtml()
        return SelectorRunStepResult(
            step = input.step,
            ok = result.document != null,
            summary = when {
                result.captchaKind != null -> "被人机验证拦截 (${result.captchaKind})"
                result.document == null -> "搜索页返回 404"
                else -> "搜索成功, HTML 共 ${html!!.length} 字符"
            },
            durationMillis = 0,
            details = buildJsonObject {
                put("finalUrl", result.url.toString())
                put("captchaKind", result.captchaKind?.toString() ?: "")
                put("htmlLength", html?.length ?: 0)
                put("html", html.orEmpty().truncate(input.maxHtmlLength))
            },
            errors = result.captchaKind?.let { listOf("Captcha required: $it") }.orEmpty(),
        )
    }

    private suspend fun stepSelectSubjects(input: SelectorRunStepInput): SelectorRunStepResult {
        val config = requireConfig(input)
        val (document, pageUrl) = loadDocumentForSearchResult(input, config)
            ?: return captchaBlockedResult(input)
        val subjects = engine.selectSubjects(document, config)
            ?: return invalidConfigResult(input, "条目格式 (subjectFormat) 的必填项为空或 selector 语法错误")
        return SelectorRunStepResult(
            step = input.step,
            ok = subjects.isNotEmpty(),
            summary = "解析出 ${subjects.size} 个条目",
            durationMillis = 0,
            details = buildJsonObject {
                put("pageUrl", pageUrl)
                put("baseUrl", config.finalBaseUrl)
                put("subjects", subjectsJson(subjects, cap = 100))
            },
        )
    }

    private suspend fun stepSearchEpisodes(input: SelectorRunStepInput): SelectorRunStepResult {
        val url = requireNotNull(input.url) { "searchEpisodes 步骤需要 url 参数 (条目详情页 URL)" }
        val document = engine.searchEpisodes(url)
        val html = document?.outerHtml()
        return SelectorRunStepResult(
            step = input.step,
            ok = document != null,
            summary = if (document == null) "详情页返回 404" else "已获取详情页, HTML 共 ${html!!.length} 字符",
            durationMillis = 0,
            details = buildJsonObject {
                put("url", url)
                put("htmlLength", html?.length ?: 0)
                put("html", html.orEmpty().truncate(input.maxHtmlLength))
            },
        )
    }

    private suspend fun stepSelectEpisodes(input: SelectorRunStepInput): SelectorRunStepResult {
        val config = requireConfig(input)
        val subjectUrl = input.subjectUrl ?: input.url
        ?: throw IllegalArgumentException("selectEpisodes 步骤需要 subjectUrl (或 url) 参数")
        val document = if (input.html != null) {
            engine.parseDocument(subjectUrl, input.html)
        } else {
            engine.searchEpisodes(subjectUrl)
                ?: throw IllegalArgumentException("详情页返回 404: $subjectUrl")
        }
        val selected = engine.selectEpisodes(document, subjectUrl, config)
            ?: return invalidConfigResult(input, "剧集格式 (channelFormat) 的必填项为空或 selector/正则语法错误")
        return SelectorRunStepResult(
            step = input.step,
            ok = selected.episodes.isNotEmpty(),
            summary = "解析出 ${selected.episodes.size} 个剧集" +
                    (selected.channels?.let { ", ${it.size} 条线路" } ?: ""),
            durationMillis = 0,
            details = buildJsonObject {
                put("subjectUrl", subjectUrl)
                selected.channels?.let { channels ->
                    putJsonArray("channels") { channels.forEach { add(JsonPrimitive(it)) } }
                }
                put("episodes", episodesJson(selected.episodes, cap = 200))
            },
        )
    }

    private fun stepSelectMedia(input: SelectorRunStepInput): SelectorRunStepResult {
        val config = requireConfig(input)
        val episodes = requireNotNull(input.episodes) { "selectMedia 步骤需要 episodes 参数" }
        val queryInput = requireNotNull(input.query) { "selectMedia 步骤需要 query 参数" }
        val query = SelectorSearchQuery(
            subjectName = queryInput.subjectName,
            episodeSort = EpisodeSort(queryInput.episodeSort),
            allSubjectNames = queryInput.allSubjectNames.ifEmpty { listOf(queryInput.subjectName) }.toSet(),
            episodeEp = queryInput.episodeEp?.let(::EpisodeSort),
            episodeName = queryInput.episodeName,
        )
        val result = engine.selectMedia(
            episodes = episodes.asSequence().map { it.toWebSearchEpisodeInfo() },
            config = config,
            query = query,
            mediaSourceId = MCP_MEDIA_SOURCE_ID,
            subjectName = queryInput.subjectName,
        )
        val filteredOut = result.originalList - result.filteredList.toSet()
        return SelectorRunStepResult(
            step = input.step,
            ok = result.filteredList.isNotEmpty(),
            summary = "过滤后剩 ${result.filteredList.size}/${result.originalList.size} 个候选",
            durationMillis = 0,
            details = buildJsonObject {
                put("medias", mediasJson(result.filteredList.map { it.toWebMediaCandidate() }, cap = 100))
                put("filteredOut", mediasJson(filteredOut.map { it.toWebMediaCandidate() }, cap = 100))
            },
        )
    }

    private fun stepMatchWebVideo(input: SelectorRunStepInput): SelectorRunStepResult {
        val config = requireConfig(input)
        val url = requireNotNull(input.url) { "matchWebVideo 步骤需要 url 参数" }
        val matchResult = engine.matchWebVideo(url, config.matchVideo)
        val (kind, video) = when (matchResult) {
            is WebVideoMatcher.MatchResult.Matched -> "matched" to matchResult.video
            WebVideoMatcher.MatchResult.LoadPage -> "loadPage" to null
            WebVideoMatcher.MatchResult.Continue -> "continue" to null
        }
        return SelectorRunStepResult(
            step = input.step,
            ok = matchResult is WebVideoMatcher.MatchResult.Matched,
            summary = when (kind) {
                "matched" -> "URL 匹配为视频链接"
                "loadPage" -> "URL 匹配 matchNestedUrl, 播放时会作为嵌套页面加载"
                else -> "URL 不匹配任何规则, 会被忽略"
            },
            durationMillis = 0,
            details = buildJsonObject {
                put("url", url)
                put("result", kind)
                video?.let {
                    put("videoUrl", it.m3u8Url)
                    put(
                        "headers",
                        buildJsonObject {
                            it.headers.forEach { (k, v) -> put(k, v) }
                        },
                    )
                }
            },
        )
    }

    private suspend fun stepExtractVideo(input: SelectorRunStepInput): SelectorRunStepResult {
        val url = requireNotNull(input.url) { "extractVideo 步骤需要 url 参数 (播放页 URL)" }
        val matchers = input.config
            ?.let { SelectorConfigSupport.parseSelectorArguments(it, json).arguments }
            ?.searchConfig
            ?.let { listOf(SelectorWebVideoMatcher(engine, it.matchVideo)) }
            .orEmpty()

        val media = createWebMediaForPage(url, MCP_MEDIA_SOURCE_ID)
        val resolveResult = resolver.resolvePage(media, url, matchers)
        val resolved = resolveResult.resolvedVideo

        val probeResult = if (resolved != null && input.probeResolvedVideo) {
            withTimeout(input.probeTimeoutMillis) {
                probe.probe(resolved.url, resolved.headers)
            }
        } else {
            null
        }

        return SelectorRunStepResult(
            step = input.step,
            ok = resolved != null && (probeResult?.ok != false),
            summary = when {
                resolved == null -> "WebView 未拦截到匹配的视频 URL"
                probeResult == null -> "解析出视频 URL: ${resolved.url}"
                probeResult.ok -> "解析出视频 URL 且探测通过: ${resolved.url}"
                else -> "解析出视频 URL 但探测失败: ${probeResult.summary}"
            },
            durationMillis = 0,
            details = buildJsonObject {
                put("pageUrl", url)
                resolved?.let {
                    put("resolvedVideo", json.encodeToJsonElement(ResolvedVideoResult.serializer(), it))
                }
                probeResult?.let {
                    put("probe", json.encodeToJsonElement(VideoProbeResult.serializer(), it))
                }
                resolveResult.diagnostics?.let { put("diagnostics", it) }
            },
            errors = resolveResult.errors + probeResult?.errors.orEmpty(),
        )
    }

    private fun requireConfig(input: SelectorRunStepInput): SelectorSearchConfig {
        val element = requireNotNull(input.config) { "${input.step} 步骤需要 config 参数" }
        return SelectorConfigSupport.parseSelectorArguments(element, json).arguments.searchConfig
    }

    /**
     * @return `null` 表示被人机验证拦截
     */
    private suspend fun loadDocumentForSearchResult(
        input: SelectorRunStepInput,
        config: SelectorSearchConfig,
    ): Pair<Document, String>? {
        return if (input.html != null) {
            val pageUrl = input.url ?: config.finalBaseUrl
            val result = engine.parseSearchResult(Url(pageUrl), input.html)
            result.document?.let { it to pageUrl }
        } else {
            val url = requireNotNull(input.url) { "selectSubjects 步骤需要 html 或 url 参数" }
            engine.doHttpGet(url) to url
        }
    }

    private fun captchaBlockedResult(input: SelectorRunStepInput): SelectorRunStepResult {
        return SelectorRunStepResult(
            step = input.step,
            ok = false,
            summary = "页面被人机验证拦截",
            durationMillis = 0,
            errors = listOf("Captcha required"),
        )
    }

    private fun invalidConfigResult(input: SelectorRunStepInput, message: String): SelectorRunStepResult {
        return SelectorRunStepResult(
            step = input.step,
            ok = false,
            summary = "配置无效: $message",
            durationMillis = 0,
            errors = listOf(message),
        )
    }

    // endregion

    // region helpers

    private class StageOutput(
        val summary: String,
        val details: JsonElement? = null,
        val failed: Boolean = false,
    )

    /**
     * 执行 [block] 并把结果 (由 [describe] 描述) 记录到 [steps]. [block] 抛异常时记为 failed 并返回 `null`.
     */
    private inline fun <T> runStage(
        name: String,
        steps: MutableList<StageResult>,
        describe: (T) -> StageOutput,
        block: () -> T,
    ): T? {
        val start = System.currentTimeMillis()
        val value = try {
            block()
        } catch (e: Exception) {
            steps += StageResult(
                name = name,
                status = "failed",
                summary = "${e::class.simpleName}: ${e.message.orEmpty()}",
                errors = listOf("${e::class.simpleName}: ${e.message.orEmpty()}"),
                durationMillis = System.currentTimeMillis() - start,
            )
            return null
        }
        val output = describe(value)
        steps += StageResult(
            name = name,
            status = if (output.failed) "failed" else "success",
            summary = output.summary,
            details = output.details,
            durationMillis = System.currentTimeMillis() - start,
        )
        return value
    }

    private fun subjectsJson(subjects: List<WebSearchSubjectInfo>, cap: Int): JsonElement {
        return cappedJsonArray(subjects, cap) { info ->
            buildJsonObject {
                put("name", info.name)
                put("fullUrl", info.fullUrl)
                put("partialUrl", info.partialUrl)
            }
        }
    }

    private fun episodesJson(episodes: List<WebSearchEpisodeInfo>, cap: Int): JsonElement {
        return cappedJsonArray(episodes, cap) { info ->
            buildJsonObject {
                put("channel", info.channel ?: "")
                put("name", info.name)
                put("episodeSort", info.episodeSortOrEp?.toString() ?: "")
                put("playUrl", info.playUrl)
            }
        }
    }

    private fun mediasJson(medias: List<WebMediaCandidate>, cap: Int): JsonElement {
        return cappedJsonArray(medias, cap) { json.encodeToJsonElement(WebMediaCandidate.serializer(), it) }
    }

    private inline fun <T> cappedJsonArray(list: List<T>, cap: Int, transform: (T) -> JsonElement): JsonElement {
        return buildJsonObject {
            put("total", list.size)
            if (list.size > cap) {
                put("truncated", true)
            }
            put("items", buildJsonArray { list.take(cap).forEach { add(transform(it)) } })
        }
    }

    // endregion
}

/**
 * 与 SelectorMediaSource.matcher 行为一致的 [WebVideoMatcher], 直接由配置构造, 无需创建 MediaSource 实例.
 */
class SelectorWebVideoMatcher(
    private val engine: SelectorMediaSourceEngine,
    private val matchVideoConfig: SelectorSearchConfig.MatchVideoConfig,
) : WebVideoMatcher {
    override fun match(
        url: String,
        context: WebVideoMatcherContext,
    ): WebVideoMatcher.MatchResult = engine.matchWebVideo(url, matchVideoConfig)

    override fun patchConfig(config: WebViewConfig): WebViewConfig {
        val configuredCookies = matchVideoConfig.cookies.lines().filter { it.isNotBlank() }
        return config.copy(cookies = (config.cookies + configuredCookies).distinct())
    }
}

internal fun createWebMediaForPage(pageUrl: String, mediaSourceId: String): DefaultMedia {
    return DefaultMedia(
        mediaId = "$mediaSourceId.manual",
        mediaSourceId = mediaSourceId,
        originalUrl = pageUrl,
        download = ResourceLocation.WebVideo(pageUrl),
        originalTitle = pageUrl,
        publishedTime = 0L,
        properties = MediaProperties(
            subjectName = pageUrl,
            episodeName = pageUrl,
            subtitleLanguageIds = listOf("CHS"),
            resolution = "1080P",
            alliance = mediaSourceId,
            size = FileSize.Unspecified,
            subtitleKind = SubtitleKind.EMBEDDED,
        ),
        episodeRange = null,
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )
}

private fun DefaultMedia.toWebMediaCandidate(): WebMediaCandidate {
    return WebMediaCandidate(
        mediaId = mediaId,
        subjectName = properties.subjectName ?: "",
        channel = properties.alliance.ifBlank { null },
        episodeName = properties.episodeName ?: "",
        episodeSort = episodeRange?.knownSorts?.firstOrNull()?.toString(),
        playUrl = originalUrl,
    )
}

private fun EpisodeInfoInput.toWebSearchEpisodeInfo(): WebSearchEpisodeInfo {
    return WebSearchEpisodeInfo(
        channel = channel,
        name = name,
        episodeSortOrEp = episodeSort?.let(::EpisodeSort)
            ?: SelectorChannelFormat.convertSpecialEpisodes(name, null),
        playUrl = playUrl,
    )
}

private fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "\n... (truncated, total $length chars)"
}

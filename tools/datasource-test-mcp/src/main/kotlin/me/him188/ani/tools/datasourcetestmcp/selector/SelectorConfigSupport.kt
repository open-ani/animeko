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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.xml.QueryParser
import me.him188.ani.utils.xml.parseSelectorOrNull

/**
 * 解析与校验 Selector 数据源配置 JSON.
 *
 * 接受三种形态:
 * - `ExportedMediaSourceData`: `{"factoryId": "web-selector", "version": 2, "arguments": {...}}` (App 导出格式)
 * - `SelectorMediaSourceArguments`: `{"name": ..., "searchConfig": {...}}`
 * - 裸 `SelectorSearchConfig`: `{"searchUrl": ...}`
 *
 * 以及包含多个源的订阅格式 `{"mediaSources": [...]}` (仅校验其中的 web-selector 源).
 */
object SelectorConfigSupport {
    const val FORMAT_EXPORTED = "exported"
    const val FORMAT_EXPORTED_LIST = "exported-list"
    const val FORMAT_ARGUMENTS = "arguments"
    const val FORMAT_SEARCH_CONFIG = "search-config"

    class ParsedConfig(
        val detectedFormat: String,
        val arguments: SelectorMediaSourceArguments,
    )

    /**
     * 解析出单个 Selector 配置. 输入为订阅列表时取第一个 web-selector 源.
     *
     * @throws IllegalArgumentException 无法解析时, message 为可读的错误说明.
     */
    fun parseSelectorArguments(raw: JsonElement, json: Json): ParsedConfig {
        val element = unwrapStringJson(raw, json)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("配置必须是 JSON object, 实际为 ${element::class.simpleName}")

        return when {
            "mediaSources" in obj -> {
                val entries = selectorEntriesOfList(obj)
                val first = entries.firstOrNull()
                    ?: throw IllegalArgumentException("订阅列表中没有 factoryId 为 ${SelectorMediaSource.FactoryId.value} 的数据源")
                ParsedConfig(FORMAT_EXPORTED_LIST, parseExported(first.second, json))
            }

            "factoryId" in obj && "arguments" in obj -> ParsedConfig(FORMAT_EXPORTED, parseExported(obj, json))

            "searchConfig" in obj -> ParsedConfig(
                FORMAT_ARGUMENTS,
                decodeArguments(obj, json),
            )

            "searchUrl" in obj -> ParsedConfig(
                FORMAT_SEARCH_CONFIG,
                SelectorMediaSourceArguments.Default.copy(
                    searchConfig = decodeSearchConfig(obj, json),
                ),
            )

            else -> throw IllegalArgumentException(
                "无法识别的配置形态: 期望包含 factoryId+arguments / searchConfig / searchUrl / mediaSources 之一",
            )
        }
    }

    fun validate(input: ValidateSelectorConfigInput, json: Json): ValidateSelectorConfigResult {
        val issues = mutableListOf<ConfigIssue>()
        val element = try {
            unwrapStringJson(input.config, json)
        } catch (e: Exception) {
            return invalidResult("配置不是合法 JSON: ${e.message.orEmpty()}")
        }
        val obj = element as? JsonObject
            ?: return invalidResult("配置必须是 JSON object")

        val validated = mutableListOf<String>()
        return try {
            when {
                "mediaSources" in obj -> {
                    val entries = selectorEntriesOfList(obj)
                    if (entries.isEmpty()) {
                        return invalidResult(
                            "订阅列表中没有 ${SelectorMediaSource.FactoryId.value} 数据源",
                            detectedFormat = FORMAT_EXPORTED_LIST,
                        )
                    }
                    for ((index, entry) in entries) {
                        val arguments = parseExported(entry, json, pathPrefix = "mediaSources[$index].", issues = issues)
                        validated += arguments.name
                        validateArguments(arguments, pathPrefix = "mediaSources[$index].arguments.", issues = issues)
                    }
                    buildResult(FORMAT_EXPORTED_LIST, validated.joinToString(", "), issues)
                }

                "factoryId" in obj && "arguments" in obj -> {
                    val arguments = parseExported(obj, json, pathPrefix = "", issues = issues)
                    validateArguments(arguments, pathPrefix = "arguments.", issues = issues)
                    buildResult(FORMAT_EXPORTED, arguments.name, issues)
                }

                "searchConfig" in obj -> {
                    val arguments = decodeArguments(obj, json)
                    validateArguments(arguments, pathPrefix = "", issues = issues)
                    buildResult(FORMAT_ARGUMENTS, arguments.name, issues)
                }

                "searchUrl" in obj -> {
                    val config = decodeSearchConfig(obj, json)
                    validateSearchConfig(config, pathPrefix = "", issues = issues)
                    buildResult(FORMAT_SEARCH_CONFIG, sourceName = null, issues)
                }

                else -> invalidResult(
                    "无法识别的配置形态: 期望包含 factoryId+arguments / searchConfig / searchUrl / mediaSources 之一",
                )
            }
        } catch (e: IllegalArgumentException) {
            invalidResult(e.message ?: "配置解析失败")
        }
    }

    // region parsing helpers

    private fun unwrapStringJson(raw: JsonElement, json: Json): JsonElement {
        return if (raw is JsonPrimitive && raw.isString) {
            json.parseToJsonElement(raw.content)
        } else {
            raw
        }
    }

    private fun selectorEntriesOfList(obj: JsonObject): List<Pair<Int, JsonObject>> {
        return obj.getValue("mediaSources").jsonArray
            .mapIndexedNotNull { index, entry ->
                val entryObj = entry as? JsonObject ?: return@mapIndexedNotNull null
                val factoryId = (entryObj["factoryId"] as? JsonPrimitive)?.content
                if (factoryId == SelectorMediaSource.FactoryId.value) index to entryObj else null
            }
    }

    private fun parseExported(
        obj: JsonObject,
        json: Json,
        pathPrefix: String = "",
        issues: MutableList<ConfigIssue>? = null,
    ): SelectorMediaSourceArguments {
        val factoryId = (obj["factoryId"] as? JsonPrimitive)?.content
        if (factoryId != SelectorMediaSource.FactoryId.value) {
            throw IllegalArgumentException(
                "factoryId 为 \"$factoryId\", 不是 selector 数据源 (期望 \"${SelectorMediaSource.FactoryId.value}\")",
            )
        }
        val version = (obj["version"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (version != null && version > 2) {
            issues?.add(
                ConfigIssue(
                    severity = "warning",
                    path = "${pathPrefix}version",
                    message = "导出版本 $version 高于当前支持的 2, 可能包含无法识别的字段",
                ),
            )
        }
        val arguments = obj["arguments"]
            ?: throw IllegalArgumentException("缺少 arguments 字段")
        return decodeArguments(arguments, json)
    }

    private fun decodeArguments(element: JsonElement, json: Json): SelectorMediaSourceArguments {
        return try {
            json.decodeFromJsonElement(SelectorMediaSourceArguments.serializer(), element)
        } catch (e: Exception) {
            throw IllegalArgumentException("SelectorMediaSourceArguments 反序列化失败: ${e.message.orEmpty()}")
        }
    }

    private fun decodeSearchConfig(element: JsonElement, json: Json): SelectorSearchConfig {
        return try {
            json.decodeFromJsonElement(SelectorSearchConfig.serializer(), element)
        } catch (e: Exception) {
            throw IllegalArgumentException("SelectorSearchConfig 反序列化失败: ${e.message.orEmpty()}")
        }
    }

    // endregion

    // region validation

    private fun validateArguments(
        arguments: SelectorMediaSourceArguments,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        if (arguments.name.isBlank()) {
            issues += ConfigIssue("warning", "${pathPrefix}name", "数据源名称为空")
        }
        validateSearchConfig(arguments.searchConfig, "${pathPrefix}searchConfig.", issues)
    }

    fun validateSearchConfig(
        config: SelectorSearchConfig,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        validateSearchUrl(config, pathPrefix, issues)

        if (config.searchUseSubjectNamesCount < 1) {
            issues += ConfigIssue(
                "warning", "${pathPrefix}searchUseSubjectNamesCount",
                "searchUseSubjectNamesCount 为 ${config.searchUseSubjectNamesCount}, 实际运行时至少使用 1 个搜索词",
            )
        }
        if (config.rawBaseUrl.isBlank() && config.searchUrl.isNotBlank()) {
            issues += ConfigIssue(
                "info", "${pathPrefix}rawBaseUrl",
                "未指定 baseUrl, 将从 searchUrl 推断为 \"${config.finalBaseUrl}\"",
            )
        }

        validateSubjectFormat(config, pathPrefix, issues)
        validateChannelFormat(config, pathPrefix, issues)
        validateMatchVideo(config.matchVideo, "${pathPrefix}matchVideo.", issues)
    }

    private fun validateSearchUrl(
        config: SelectorSearchConfig,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        val path = "${pathPrefix}searchUrl"
        if (config.searchUrl.isBlank()) {
            issues += ConfigIssue("error", path, "searchUrl 为空, 无法搜索")
            return
        }
        if ("{keyword}" !in config.searchUrl) {
            issues += ConfigIssue(
                "warning", path,
                "searchUrl 不包含 {keyword} 占位符, 搜索时不会代入关键词",
            )
        }
        runCatching { Url(config.searchUrl.replace("{keyword}", "test")) }
            .onFailure {
                issues += ConfigIssue("error", path, "searchUrl 不是合法 URL: ${it.message.orEmpty()}")
            }
    }

    private fun validateSubjectFormat(
        config: SelectorSearchConfig,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        val format = SelectorSubjectFormat.findById(config.subjectFormatId)
        if (format == null) {
            issues += ConfigIssue(
                "error", "${pathPrefix}subjectFormatId",
                "未知的条目解析格式 \"${config.subjectFormatId.value}\", 支持: " +
                        SelectorSubjectFormat.entries.joinToString { it.id.value },
            )
            return
        }
        when (format) {
            SelectorSubjectFormatA -> {
                val c = config.selectorSubjectFormatA
                checkCssSelector(c.selectLists, "${pathPrefix}selectorSubjectFormatA.selectLists", issues)
            }

            SelectorSubjectFormatIndexed -> {
                val c = config.selectorSubjectFormatIndexed
                checkCssSelector(c.selectNames, "${pathPrefix}selectorSubjectFormatIndexed.selectNames", issues)
                checkCssSelector(c.selectLinks, "${pathPrefix}selectorSubjectFormatIndexed.selectLinks", issues)
            }

            SelectorSubjectFormatJsonPathIndexed -> {
                val c = config.selectorSubjectFormatJsonPathIndexed
                checkJsonPath(c.selectLinks, "${pathPrefix}selectorSubjectFormatJsonPathIndexed.selectLinks", issues)
                checkJsonPath(c.selectNames, "${pathPrefix}selectorSubjectFormatJsonPathIndexed.selectNames", issues)
            }
        }
    }

    private fun validateChannelFormat(
        config: SelectorSearchConfig,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        val format = SelectorChannelFormat.findById(config.channelFormatId)
        if (format == null) {
            issues += ConfigIssue(
                "error", "${pathPrefix}channelFormatId",
                "未知的剧集解析格式 \"${config.channelFormatId.value}\", 支持: " +
                        SelectorChannelFormat.entries.joinToString { it.id.value },
            )
            return
        }
        when (format) {
            SelectorChannelFormatIndexGrouped -> {
                val prefix = "${pathPrefix}selectorChannelFormatFlattened."
                val c = config.selectorChannelFormatFlattened
                checkCssSelector(c.selectChannelNames, "${prefix}selectChannelNames", issues)
                checkCssSelector(c.selectEpisodeLists, "${prefix}selectEpisodeLists", issues)
                checkCssSelector(c.selectEpisodesFromList, "${prefix}selectEpisodesFromList", issues)
                if (c.selectEpisodeLinksFromList.isNotBlank()) {
                    checkCssSelector(c.selectEpisodeLinksFromList, "${prefix}selectEpisodeLinksFromList", issues)
                }
                if (c.matchChannelName.isNotEmpty()) {
                    checkRegex(c.matchChannelName, "${prefix}matchChannelName", issues)
                    if ("(?<ch>" !in c.matchChannelName) {
                        issues += ConfigIssue(
                            "info", "${prefix}matchChannelName",
                            "正则没有 (?<ch>...) 命名分组, 将使用整个匹配文本作为线路名",
                        )
                    }
                }
                checkEpisodeSortRegex(c.matchEpisodeSortFromName, "${prefix}matchEpisodeSortFromName", issues)
            }

            SelectorChannelFormatNoChannel -> {
                val prefix = "${pathPrefix}selectorChannelFormatNoChannel."
                val c = config.selectorChannelFormatNoChannel
                checkCssSelector(c.selectEpisodes, "${prefix}selectEpisodes", issues)
                if (c.selectEpisodeLinks.isNotBlank()) {
                    checkCssSelector(c.selectEpisodeLinks, "${prefix}selectEpisodeLinks", issues)
                }
                checkEpisodeSortRegex(c.matchEpisodeSortFromName, "${prefix}matchEpisodeSortFromName", issues)
            }
        }
    }

    private fun validateMatchVideo(
        config: SelectorSearchConfig.MatchVideoConfig,
        pathPrefix: String,
        issues: MutableList<ConfigIssue>,
    ) {
        val videoUrlPath = "${pathPrefix}matchVideoUrl"
        if (config.matchVideoUrl.isBlank()) {
            issues += ConfigIssue("error", videoUrlPath, "matchVideoUrl 为空, 无法从播放页匹配视频链接")
        } else {
            checkRegex(config.matchVideoUrl, videoUrlPath, issues)
            if ("(?<v>" !in config.matchVideoUrl) {
                issues += ConfigIssue(
                    "info", videoUrlPath,
                    "正则没有 (?<v>...) 命名分组, 将使用整个匹配到的 URL 作为视频链接",
                )
            }
        }
        if (config.enableNestedUrl && config.matchNestedUrl.isNotBlank()) {
            checkRegex(config.matchNestedUrl, "${pathPrefix}matchNestedUrl", issues)
        }
        config.cookies.lines()
            .filter { it.isNotBlank() }
            .forEachIndexed { index, line ->
                if ('=' !in line) {
                    issues += ConfigIssue(
                        "warning", "${pathPrefix}cookies",
                        "第 ${index + 1} 行 cookie \"$line\" 不是 name=value 格式",
                    )
                }
            }
    }

    private fun checkCssSelector(selector: String, path: String, issues: MutableList<ConfigIssue>) {
        if (selector.isBlank()) {
            issues += ConfigIssue("error", path, "CSS selector 为空")
            return
        }
        if (QueryParser.parseSelectorOrNull(selector) == null) {
            issues += ConfigIssue("error", path, "CSS selector 语法错误: \"$selector\"")
        }
    }

    private fun checkJsonPath(expression: String, path: String, issues: MutableList<ConfigIssue>) {
        if (expression.isBlank()) {
            issues += ConfigIssue("error", path, "JsonPath 为空")
            return
        }
        if (JsonPath.compileOrNull(expression) == null) {
            issues += ConfigIssue("error", path, "JsonPath 语法错误: \"$expression\"")
        }
    }

    private fun checkRegex(pattern: String, path: String, issues: MutableList<ConfigIssue>) {
        runCatching { pattern.toRegex() }
            .onFailure {
                issues += ConfigIssue("error", path, "正则语法错误: ${it.message.orEmpty()}")
            }
    }

    private fun checkEpisodeSortRegex(pattern: String, path: String, issues: MutableList<ConfigIssue>) {
        if (pattern.isBlank()) {
            issues += ConfigIssue("error", path, "matchEpisodeSortFromName 为空, 无法解析剧集序号")
            return
        }
        checkRegex(pattern, path, issues)
        if ("(?<ep>" !in pattern) {
            issues += ConfigIssue(
                "warning", path,
                "正则没有 (?<ep>...) 命名分组, 无法从剧集名中提取序号",
            )
        }
    }

    // endregion

    private fun buildResult(
        detectedFormat: String,
        sourceName: String?,
        issues: List<ConfigIssue>,
    ): ValidateSelectorConfigResult {
        val errors = issues.count { it.severity == "error" }
        val warnings = issues.count { it.severity == "warning" }
        return ValidateSelectorConfigResult(
            ok = errors == 0,
            summary = when {
                errors == 0 && warnings == 0 -> "配置有效"
                errors == 0 -> "配置有效, 有 $warnings 个警告"
                else -> "配置无效: $errors 个错误, $warnings 个警告"
            },
            detectedFormat = detectedFormat,
            sourceName = sourceName,
            issues = issues,
        )
    }

    private fun invalidResult(message: String, detectedFormat: String? = null): ValidateSelectorConfigResult {
        return ValidateSelectorConfigResult(
            ok = false,
            summary = message,
            detectedFormat = detectedFormat,
            issues = listOf(ConfigIssue("error", "", message)),
        )
    }
}

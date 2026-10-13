/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiTemplate.Escaping
import me.him188.ani.app.domain.mediasource.web.format.parseOrNull
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.jsonpath.resolveOrNull
import org.intellij.lang.annotations.Language

/**
 * 决定怎样从条目阶段的响应里分出线路. 剧集字段由 [JsonApiEpisodeFields] 统一描述, 格式只负责线路.
 *
 * @see JsonApiChannelFormatNoChannel
 * @see JsonApiChannelFormatGrouped
 * @see JsonApiChannelFormatDeclared
 */
sealed class JsonApiChannelFormat<in Config>(val id: JsonApiFormatId) {
    /**
     * @return `null` 表示配置无效 (JSONPath 或正则写错).
     */
    abstract fun select(
        json: JsonElement,
        config: Config,
        episodes: JsonApiEpisodeFields,
        subject: JsonApiSubjectInfo,
    ): List<JsonApiChannelInfo>?

    companion object {
        val entries: List<JsonApiChannelFormat<*>> by lazy {
            listOf(JsonApiChannelFormatNoChannel, JsonApiChannelFormatGrouped, JsonApiChannelFormatDeclared)
        }

        fun findById(id: JsonApiFormatId): JsonApiChannelFormat<*>? = entries.find { it.id == id }
    }
}

/**
 * 没有线路: 响应就是剧集列表, 得到一条名称为 `null` 的线路.
 */
data object JsonApiChannelFormatNoChannel : JsonApiChannelFormat<Unit>(JsonApiFormatId("no-channel")) {
    override fun select(
        json: JsonElement,
        config: Unit,
        episodes: JsonApiEpisodeFields,
        subject: JsonApiSubjectInfo,
    ): List<JsonApiChannelInfo>? {
        val parsed = JsonApiEpisodeParser.create(episodes) ?: return null
        val list = parsed.parse(json, subject, channel = null)
        return listOf(JsonApiChannelInfo(name = null, match = null, episodes = list))
    }
}

/**
 * 响应里按线路分组: 先选出线路数组, 每条线路里再选剧集.
 */
data object JsonApiChannelFormatGrouped : JsonApiChannelFormat<JsonApiChannelFormatGrouped.Config>(JsonApiFormatId("grouped")) {
    @Immutable
    @Serializable
    data class Config(
        /**
         * 选出线路数组.
         */
        @param:Language("jsonpath")
        val selectChannels: String = "$.lines[*]",
        /**
         * 相对每条线路: 线路名称. 它同时是播放阶段候选的匹配值.
         */
        @param:Language("jsonpath")
        val selectChannelName: String = "$.name",
    ) {
        fun isValid(): Boolean = selectChannels.isNotBlank() && selectChannelName.isNotBlank()
    }

    override fun select(
        json: JsonElement,
        config: Config,
        episodes: JsonApiEpisodeFields,
        subject: JsonApiSubjectInfo,
    ): List<JsonApiChannelInfo>? {
        val selectChannels = JsonPath.compileOrNull(config.selectChannels) ?: return null
        val selectChannelName = JsonPath.compileOrNull(config.selectChannelName) ?: return null
        val parsed = JsonApiEpisodeParser.create(episodes) ?: return null
        return json.resolveOrNull(selectChannels).items().mapNotNull { channelJson ->
            val name = channelJson.resolveOrNull(selectChannelName)?.stringValueOrNull()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val channel = JsonApiChannelInfo(name = name, match = name, episodes = emptyList())
            channel.copy(episodes = parsed.parse(channelJson, subject, channel))
        }
    }
}

/**
 * 线路在配置里声明: 响应只有一份剧集列表, 每集在每条声明的线路上各有一个资源, 播放时按线路匹配值选候选.
 * 适合线路只在播放响应里出现的站点.
 */
data object JsonApiChannelFormatDeclared :
    JsonApiChannelFormat<JsonApiChannelFormatDeclared.Config>(JsonApiFormatId("declared")) {
    @Immutable
    @Serializable
    data class Config(
        val channels: List<JsonApiDeclaredChannel> = emptyList(),
    ) {
        fun isValid(): Boolean = channels.isNotEmpty() && channels.all { it.name.isNotBlank() } &&
                channels.distinctBy { it.name }.size == channels.size

        /**
         * 把每行 `名称=匹配值` (或只有名称) 解析为声明的线路. 供编辑器用.
         */
        companion object {
            fun parseChannelLines(text: String): List<JsonApiDeclaredChannel> = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    val index = line.indexOf('=')
                    if (index < 0) {
                        JsonApiDeclaredChannel(line)
                    } else {
                        val name = line.substring(0, index).trim()
                        val match = line.substring(index + 1).trim()
                        JsonApiDeclaredChannel(name, match.ifEmpty { name })
                    }
                }
                .toList()

            fun formatChannelLines(channels: List<JsonApiDeclaredChannel>): String =
                channels.joinToString("\n") { if (it.match == it.name) it.name else "${it.name}=${it.match}" }
        }
    }

    override fun select(
        json: JsonElement,
        config: Config,
        episodes: JsonApiEpisodeFields,
        subject: JsonApiSubjectInfo,
    ): List<JsonApiChannelInfo>? {
        if (!config.isValid()) return null
        val parsed = JsonApiEpisodeParser.create(episodes) ?: return null
        return config.channels.map { declared ->
            val channel = JsonApiChannelInfo(name = declared.name, match = declared.match, episodes = emptyList())
            channel.copy(episodes = parsed.parse(json, subject, channel))
        }
    }
}

/**
 * 搜索到的一个条目.
 */
data class JsonApiSubjectInfo(
    /**
     * 站点 id, 由 [JsonApiSearchStage.selectId] 取得, 后续请求里的 `{id}`.
     */
    val id: String,
    val name: String,
    /**
     * 条目页面地址, 由 [JsonApiSearchStage.subjectUrlTemplate] 生成. 条目的稳定标识.
     */
    val url: String,
    /**
     * 站点给出的别名, 由 [JsonApiSearchStage.selectAliases] 取得, 不含 [name] 本身. 只参与自动匹配的名字比较.
     */
    val aliases: List<String> = emptyList(),
)

/**
 * 一条线路及其剧集.
 */
data class JsonApiChannelInfo(
    /**
     * 线路名称. 资源的 `alliance`. 没有线路概念时为 `null`.
     */
    val name: String?,
    /**
     * 播放阶段候选的匹配值. 没有线路概念时为 `null`.
     */
    val match: String?,
    val episodes: List<JsonApiEpisodeInfo>,
)

/**
 * 一集.
 */
data class JsonApiEpisodeInfo(
    /**
     * 所在线路, 没有线路概念时为 `null`.
     */
    val channel: String?,
    /**
     * 所在线路的播放匹配值.
     */
    val channelMatch: String?,
    /**
     * 显示名称, 由 [JsonApiEpisodeFields.nameTemplate] 生成. 同一线路内唯一.
     */
    val name: String,
    /**
     * 解析出的集号, 解析不出为 `null`.
     */
    val episodeSortOrEp: EpisodeSort?,
    /**
     * 站点 id, 播放请求里的 `{id}`.
     */
    val id: String,
    /**
     * 剧集页面地址, 由 [JsonApiEpisodeFields.episodeUrlTemplate] 生成. 剧集的稳定标识.
     */
    val url: String,
    /**
     * 列表里给出的可直接播放的视频地址, 由 [JsonApiEpisodeFields.selectVideoUrl] 取得. 没有配置或这一集没有时为 `null`.
     */
    val videoUrl: String? = null,
)

/**
 * 从剧集数组元素里读出剧集. 配置编译一次, 对多条线路重复使用.
 */
internal class JsonApiEpisodeParser private constructor(
    private val fields: JsonApiEpisodeFields,
    private val selectEpisodes: JsonPath,
    private val selectName: JsonPath,
    private val selectId: JsonPath,
    private val selectSort: JsonPath?,
    private val selectType: JsonPath?,
    private val selectVideoUrl: JsonPath?,
    private val matchSortFromName: Regex?,
) {
    companion object {
        /**
         * @return `null` 表示配置无效.
         */
        fun create(fields: JsonApiEpisodeFields): JsonApiEpisodeParser? {
            if (!fields.isValid()) return null
            val matchSortFromName = fields.matchEpisodeSortFromName.takeIf { it.isNotBlank() }?.let {
                Regex.parseOrNull(it) ?: return null
            }
            return JsonApiEpisodeParser(
                fields,
                selectEpisodes = JsonPath.compileOrNull(fields.selectEpisodes) ?: return null,
                selectName = JsonPath.compileOrNull(fields.selectName) ?: return null,
                selectId = JsonPath.compileOrNull(fields.selectId) ?: return null,
                selectSort = fields.selectSort.takeIf { it.isNotBlank() }?.let { JsonPath.compileOrNull(it) ?: return null },
                selectType = fields.selectType.takeIf { it.isNotBlank() }?.let { JsonPath.compileOrNull(it) ?: return null },
                selectVideoUrl = fields.selectVideoUrl.takeIf { it.isNotBlank() }?.let { JsonPath.compileOrNull(it) ?: return null },
                matchSortFromName = matchSortFromName,
            )
        }
    }

    /**
     * [channel] 为 `null` 表示没有线路. 没有 id 的元素跳过. 同一线路内名称保持唯一: 空名称用 id, 重名的追加 id.
     */
    fun parse(json: JsonElement, subject: JsonApiSubjectInfo, channel: JsonApiChannelInfo?): List<JsonApiEpisodeInfo> {
        val seenNames = HashSet<String>()
        return json.resolveOrNull(selectEpisodes).items().mapNotNull { element ->
            val id = element.resolveOrNull(selectId)?.stringValueOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val rawName = element.resolveOrNull(selectName)?.stringValueOrNull()?.trim().orEmpty()
            val sort = parseSort(element, rawName)
            val name = renderName(rawName, sort, id).let { rendered ->
                val base = rendered.ifBlank { id }
                if (seenNames.add(base)) base else "$base ($id)".also { seenNames.add(it) }
            }
            JsonApiEpisodeInfo(
                channel = channel?.name,
                channelMatch = channel?.match,
                name = name,
                episodeSortOrEp = sort,
                id = id,
                url = JsonApiTemplate.substitute(
                    fields.episodeUrlTemplate,
                    mapOf("id" to id, "subjectId" to subject.id),
                    Escaping.NONE,
                ),
                videoUrl = selectVideoUrl?.let { path ->
                    element.resolveOrNull(path)?.stringValueOrNull()?.trim()
                        ?.takeIf { JsonApiMediaSourceEngine.isAbsoluteUrl(it) }
                },
            )
        }
    }

    private fun parseSort(element: JsonElement, rawName: String): EpisodeSort? {
        val type = selectType?.let { path ->
            element.resolveOrNull(path)?.stringValueOrNull()?.trim()?.lowercase()?.let { fields.episodeTypes[it] }
        }
        val number = selectSort?.let { path -> element.resolveOrNull(path)?.stringValueOrNull()?.trim() }
            ?.takeIf { it.isNotEmpty() }
        if (number != null) {
            return when (type) {
                null, EpisodeType.MainStory -> EpisodeSort(number)
                else -> EpisodeSort(type.value + number)
            }
        }
        if (rawName.isEmpty()) return null
        val regex = matchSortFromName ?: return EpisodeSort(rawName)
        val match = regex.find(rawName) ?: return null
        val raw = try {
            match.groups["ep"]?.value
        } catch (_: IllegalArgumentException) {
            null
        } ?: match.value
        return when (type) {
            null, EpisodeType.MainStory -> EpisodeSort(raw)
            else -> if (raw.startsWith(type.value, ignoreCase = true)) EpisodeSort(raw) else EpisodeSort(type.value + raw)
        }
    }

    private fun renderName(rawName: String, sort: EpisodeSort?, id: String): String {
        if (fields.nameTemplate.isBlank()) return rawName
        return JsonApiTemplate.substitute(
            fields.nameTemplate,
            mapOf("name" to rawName, "sort" to (sort?.toString() ?: ""), "id" to id),
            Escaping.NONE,
        ).trim()
    }
}

/**
 * JSONPath 结果展开为列表: 数组逐个元素, 对象视为单个元素, `null` / 缺失为空.
 */
internal fun JsonElement?.items(): List<JsonElement> = when (this) {
    null, is JsonNull -> emptyList()
    is JsonArray -> this
    is JsonObject -> listOf(this)
    is JsonPrimitive -> listOf(this)
}

/**
 * 取单个标量值的文本. 数组或对象取第一个值 (兼容 `$[*]['a','b']` 这类返回对象的写法).
 */
internal fun JsonElement.stringValueOrNull(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> content
    is JsonArray -> firstOrNull()?.stringValueOrNull()
    is JsonObject -> values.firstOrNull()?.stringValueOrNull()
}

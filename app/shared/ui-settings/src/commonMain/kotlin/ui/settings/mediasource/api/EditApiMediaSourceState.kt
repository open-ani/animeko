/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiRequest
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.MediaSourceTier

/**
 * JSON 编辑器与各 JSON 字段使用的格式. 输出所有默认值, 让用户在编辑器里看到完整的配置.
 */
internal val apiEditorJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * 解析 JSON 编辑器里的文本. 接受裸的 [ApiMediaSourceArguments] 对象, 或订阅及导出格式中的单个源.
 */
internal fun decodeApiArguments(text: String, codecs: MediaSourceCodecManager): ApiMediaSourceArguments {
    val root = apiEditorJson.parseToJsonElement(text)
    if (root is JsonObject && ("mediaSources" in root || "exportedMediaSourceDataList" in root)) {
        val sources = codecs.decodeSubscriptionFromString(text).mediaSources
        require(sources.size == 1) { "Expected exactly one media source, got ${sources.size}" }
        val data = sources.single()
        require(data.factoryId == ApiMediaSource.FactoryId) { "Expected a json-api source" }
        return codecs.decode(data) as ApiMediaSourceArguments
    }
    return apiEditorJson.decodeFromJsonElement(ApiMediaSourceArguments.serializer(), root)
}

/**
 * 一个以文本编辑、但模型里是结构化值的字段 (JSON 对象, 整数, 整份配置).
 *
 * 能解析的输入立即写入 [storage], 与其他字段一样自动保存.
 * 解析不了的输入 (正在输入的 JSON, 清空的数字) 没有对应的模型值, 只留在草稿里, 等用户改到合法为止.
 * 其他途径 (导入, JSON 编辑器) 改动了模型值时, 草稿作废, 显示新的模型值.
 */
@Stable
class DraftField<T>(
    private val storage: SaveableStorage<ApiMediaSourceArguments>,
    private val read: (ApiMediaSourceArguments) -> T,
    private val write: ApiMediaSourceArguments.(T) -> ApiMediaSourceArguments,
    private val encode: (T) -> String,
    private val parse: (String) -> T,
) {
    private class Draft<T>(
        val text: String,
        /**
         * 草稿所基于的模型值. 模型值变成别的时, 草稿作废.
         */
        val base: T,
        /**
         * 输入时解析失败的原因; 解析成功为 `null`. 在输入时算好, 读取时不再解析.
         */
        val error: String?,
    )

    private var draft: Draft<T>? by mutableStateOf(null)

    /**
     * 编辑框里显示的文本.
     */
    val text: String
        get() {
            val container = storage.container ?: return ""
            val current = read(container)
            val draft = draft
            return if (draft != null && draft.base == current) draft.text else encode(current)
        }

    /**
     * 当前草稿无法解析时的错误信息, 否则为 `null`.
     */
    val error: String?
        get() {
            val container = storage.container ?: return null
            val draft = draft ?: return null
            return if (draft.base == read(container)) draft.error else null
        }

    val isError: Boolean get() = error != null

    fun edit(text: String) {
        val container = storage.container ?: return
        runCatching { parse(text) }.fold(
            onSuccess = { value ->
                draft = Draft(text, value, error = null)
                storage.set(container.write(value))
            },
            onFailure = { e ->
                draft = Draft(text, read(container), error = e.message ?: e.toString())
            },
        )
    }
}

/**
 * 整个编辑 JSON API 数据源页面的状态. 测试部分见 [ApiTestState].
 *
 * 普通字段直接读写 [SaveableStorage]; 只有无法用模型表示的中间输入留在 [DraftField] 的草稿里.
 *
 * @see ApiMediaSource
 */
@Stable
class EditApiMediaSourceState(
    private val argumentsStorage: SaveableStorage<ApiMediaSourceArguments>,
    private val allowEditState: State<Boolean>,
    val instanceId: String,
    codecManager: MediaSourceCodecManager,
) {
    private val arguments by argumentsStorage.containerState
    val isLoading by derivedStateOf { arguments == null }

    val enableEdit by derivedStateOf {
        !isLoading && allowEditState.value
    }

    private fun <P : Any> prop(
        get: (ApiMediaSourceArguments) -> P,
        copy: ApiMediaSourceArguments.(P) -> ApiMediaSourceArguments,
    ) = argumentsStorage.prop(get, copy, get(ApiMediaSourceArguments.Default))

    private fun jsonMapField(
        read: (ApiMediaSourceArguments) -> Map<String, String>,
        write: ApiMediaSourceArguments.(Map<String, String>) -> ApiMediaSourceArguments,
    ) = DraftField(
        argumentsStorage, read, write,
        encode = { apiEditorJson.encodeToString(it) },
        parse = { apiEditorJson.decodeFromString(it.ifBlank { "{}" }) },
    )

    private fun intField(
        read: (ApiMediaSourceArguments) -> Int,
        write: ApiMediaSourceArguments.(Int) -> ApiMediaSourceArguments,
    ) = DraftField(
        argumentsStorage, read, write,
        encode = { it.toString() },
        parse = { it.trim().toInt() },
    )

    var displayName by prop({ it.name }, { copy(name = it) })
    val displayNameIsError by derivedStateOf { displayName.isBlank() }

    var websiteUrl by prop({ it.websiteUrl }, { copy(websiteUrl = it) })
    var iconUrl by prop({ it.iconUrl }, { copy(iconUrl = it) })

    /**
     * 发给三个 API 请求的公共请求头, 不发给视频主机.
     */
    val headers = jsonMapField({ it.headers }, { copy(headers = it) })

    /**
     * 一个 API 请求的地址、方法、请求头与请求体.
     */
    @Stable
    inner class RequestFields(
        private val read: (ApiMediaSourceArguments) -> ApiRequest,
        private val write: ApiMediaSourceArguments.(ApiRequest) -> ApiMediaSourceArguments,
    ) {
        var url by prop({ read(it).url }, { write(read(this).copy(url = it)) })

        /**
         * `GET` 或 `POST`. 切到 `GET` 时丢弃请求体, 因为 `GET` 请求不能带请求体.
         */
        var method by prop(
            { read(it).method },
            { method ->
                val request = read(this)
                write(request.copy(method = method, body = if (method == "GET") null else request.body))
            },
        )
        val isPost: Boolean get() = method == "POST"

        val headers = jsonMapField({ read(it).headers }, { write(read(this).copy(headers = it)) })

        val body = DraftField(
            argumentsStorage,
            read = { read(it).body },
            write = { write(read(this).copy(body = it)) },
            encode = { body -> body?.let { apiEditorJson.encodeToString(JsonElement.serializer(), it) }.orEmpty() },
            parse = { text ->
                if (text.isBlank()) null else apiEditorJson.parseToJsonElement(text).takeUnless { it == JsonNull }
            },
        )
    }

    @Stable
    inner class SearchSection {
        val request = RequestFields({ it.search.request }, { copy(search = search.copy(request = it)) })
        var itemsPath by prop({ it.search.itemsPath }, { copy(search = search.copy(itemsPath = it)) })
        var idPath by prop({ it.search.idPath }, { copy(search = search.copy(idPath = it)) })
        var namePath by prop({ it.search.namePath }, { copy(search = search.copy(namePath = it)) })
        var aliasesPath by prop({ it.search.aliasesPath }, { copy(search = search.copy(aliasesPath = it)) })
        var subjectUrl by prop({ it.search.subjectUrl }, { copy(search = search.copy(subjectUrl = it)) })
        var paginate by prop({ it.search.paginate }, { copy(search = search.copy(paginate = it)) })
        val firstPage = intField({ it.search.firstPage }, { copy(search = search.copy(firstPage = it)) })
        val pageSize = intField({ it.search.pageSize }, { copy(search = search.copy(pageSize = it)) })
        val maxPages = intField({ it.search.maxPages }, { copy(search = search.copy(maxPages = it)) })
    }

    @Stable
    inner class DetailSection {
        val request = RequestFields({ it.detail.request }, { copy(detail = detail.copy(request = it)) })
        var channelsPath by prop({ it.detail.channelsPath }, { copy(detail = detail.copy(channelsPath = it)) })
        var channelIdPath by prop({ it.detail.channelIdPath }, { copy(detail = detail.copy(channelIdPath = it)) })
        var channelNamePath by prop(
            { it.detail.channelNamePath },
            { copy(detail = detail.copy(channelNamePath = it)) },
        )
        var episodesPath by prop({ it.detail.episodesPath }, { copy(detail = detail.copy(episodesPath = it)) })
        var episodeIdPath by prop({ it.detail.episodeIdPath }, { copy(detail = detail.copy(episodeIdPath = it)) })
        var episodeNamePath by prop(
            { it.detail.episodeNamePath },
            { copy(detail = detail.copy(episodeNamePath = it)) },
        )
        var episodeNumberPath by prop(
            { it.detail.episodeNumberPath },
            { copy(detail = detail.copy(episodeNumberPath = it)) },
        )
        var episodeKindPath by prop(
            { it.detail.episodeKindPath },
            { copy(detail = detail.copy(episodeKindPath = it)) },
        )
        val episodeKindPrefixes = jsonMapField(
            { it.detail.episodeKindPrefixes },
            { copy(detail = detail.copy(episodeKindPrefixes = it)) },
        )
        var episodeUrl by prop({ it.detail.episodeUrl }, { copy(detail = detail.copy(episodeUrl = it)) })
    }

    @Stable
    inner class PlaybackSection {
        val request = RequestFields({ it.playback.request }, { copy(playback = playback.copy(request = it)) })
        var urlPath by prop({ it.playback.urlPath }, { copy(playback = playback.copy(urlPath = it)) })
        var successPath by prop({ it.playback.successPath }, { copy(playback = playback.copy(successPath = it)) })
        var candidatesPath by prop(
            { it.playback.candidatesPath },
            { copy(playback = playback.copy(candidatesPath = it)) },
        )
        var candidateIdPath by prop(
            { it.playback.candidateIdPath },
            { copy(playback = playback.copy(candidateIdPath = it)) },
        )
        var candidateUrlPath by prop(
            { it.playback.candidateUrlPath },
            { copy(playback = playback.copy(candidateUrlPath = it)) },
        )

        /**
         * 只发给视频主机的请求头. API 密钥不在这里, 所以不会泄露给 CDN.
         */
        val videoHeaders = jsonMapField(
            { it.playback.videoHeaders },
            { copy(playback = playback.copy(videoHeaders = it)) },
        )
    }

    @Stable
    inner class MatchingSection {
        var autoMatch by prop({ it.autoMatch }, { copy(autoMatch = it) })
        var filterBySubjectName by prop({ it.filterBySubjectName }, { copy(filterBySubjectName = it) })
        val searchNamesCount = intField({ it.searchNamesCount }, { copy(searchNamesCount = it) })
        var resolution by prop({ it.resolution }, { copy(resolution = it) })
        var subtitleLanguage by prop({ it.subtitleLanguage }, { copy(subtitleLanguage = it) })
        val tier = DraftField(
            argumentsStorage,
            read = { it.tier },
            write = { copy(tier = it) },
            encode = { it.value.toString() },
            parse = { MediaSourceTier(it.trim().toUInt()) },
        )
        val channelTiers = DraftField<Map<String, MediaSourceTier>>(
            argumentsStorage,
            read = { it.channelTiers },
            write = { copy(channelTiers = it) },
            encode = { apiEditorJson.encodeToString(it) },
            parse = { apiEditorJson.decodeFromString(it.ifBlank { "{}" }) },
        )
    }

    val search = SearchSection()
    val detail = DetailSection()
    val playback = PlaybackSection()
    val matching = MatchingSection()

    /**
     * 整份配置的 JSON 编辑器. 与表单读写同一份 [argumentsStorage].
     */
    val jsonEditor = DraftField(
        argumentsStorage,
        read = { it },
        write = { it },
        encode = { apiEditorJson.encodeToString(ApiMediaSourceArguments.serializer(), it) },
        parse = { decodeApiArguments(it, codecManager) },
    )
    var showJsonEditor by mutableStateOf(false)

    /**
     * 已保存配置的校验错误; `null` 表示配置可用. 表单字段各自只做格式检查, 跨字段的约束在这里显示.
     */
    val validationError: String? by derivedStateOf {
        val args = arguments ?: return@derivedStateOf null
        runCatching { args.validate() }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }

    val importState = ImportMediaSourceState<ApiMediaSourceArguments>(
        codecManager,
        onImport = { argumentsStorage.set(it) },
    )
    val exportState = ExportMediaSourceState(
        codecManager,
        onExport = { argumentsStorage.container },
    )
}

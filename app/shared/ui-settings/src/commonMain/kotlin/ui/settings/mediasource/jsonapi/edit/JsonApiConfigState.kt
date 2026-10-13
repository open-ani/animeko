/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormat
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatDeclared
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatGrouped
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiEpisodeFields
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiHttpMethod
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceEngine
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiPlayStage
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiRequestConfig
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchConfig
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchStage
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSubjectStage
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiTemplate
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVariables
import me.him188.ani.app.ui.settings.danmaku.isValidRegex
import me.him188.ani.app.ui.settings.mediasource.editor.AutoMatchConfigState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceConfigState
import me.him188.ani.app.ui.settings.mediasource.rss.ConfigSection
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * JSON API 数据源的编辑状态. 三个阶段 (搜索、剧集列表、播放) 各有一个请求配置与若干 JSONPath 字段.
 */
@Stable
class JsonApiConfigState(
    argumentsStorage: SaveableStorage<JsonApiMediaSourceArguments>,
    allowEditState: State<Boolean>,
) : MediaSourceConfigState<JsonApiMediaSourceArguments>(
    argumentsStorage, allowEditState, JsonApiMediaSourceArguments.Default,
) {
    override var displayName: String by arguments.prop({ it.name }, { copy(name = it) })
    override var iconUrl: String by arguments.prop({ it.iconUrl }, { copy(iconUrl = it) })

    private val searchConfig: ConfigSection<JsonApiSearchConfig> =
        arguments.sub({ it.searchConfig }, { copy(searchConfig = it) })

    // region 接口

    var baseUrl: String by searchConfig.prop({ it.baseUrl }, { copy(baseUrl = it) })
    var headers: String by searchConfig.prop({ it.headers }, { copy(headers = it) })
    var userAgent: String by searchConfig.prop({ it.userAgent }, { copy(userAgent = it) })
    var requestInterval by searchConfig.prop(
        { it.requestInterval },
        { copy(requestInterval = it.coerceAtLeast(0.milliseconds)) },
    )

    // endregion

    val autoMatch = AutoMatchConfigState(
        searchConfig.sub({ it.autoMatch }, { copy(autoMatch = it) }),
        tier = arguments.prop({ it.tier }, { copy(tier = it) }),
        searchCacheTtl = searchConfig.prop(
            { it.searchCacheTtl },
            { copy(searchCacheTtl = it.coerceAtLeast(0.milliseconds)) },
        ),
    )

    // region 搜索

    private val searchStage: ConfigSection<JsonApiSearchStage> = searchConfig.sub({ it.search }, { copy(search = it) })

    val searchRequest = RequestConfigState(searchStage.sub({ it.request }, { copy(request = it) }), JsonApiVariables.SEARCH)

    var pageSize: Int by searchStage.prop({ it.pageSize }, { copy(pageSize = it) })

    var selectItems: String by searchStage.prop({ it.selectItems }, { copy(selectItems = it) })
    val selectItemsIsError by derivedStateOf { !isValidJsonPath(selectItems) }
    var selectName: String by searchStage.prop({ it.selectName }, { copy(selectName = it) })
    val selectNameIsError by derivedStateOf { !isValidJsonPath(selectName) }
    var selectId: String by searchStage.prop({ it.selectId }, { copy(selectId = it) })
    val selectIdIsError by derivedStateOf { !isValidJsonPath(selectId) }
    var selectAliases: String by searchStage.prop({ it.selectAliases }, { copy(selectAliases = it) })
    val selectAliasesIsError by derivedStateOf { !isValidOptionalJsonPath(selectAliases) }
    var subjectUrlTemplate: String by searchStage.prop({ it.subjectUrlTemplate }, { copy(subjectUrlTemplate = it) })
    val subjectUrlTemplateUnknownVariables: List<String> by derivedStateOf {
        JsonApiTemplate.unknownVariables(subjectUrlTemplate, JsonApiVariables.SUBJECT_URL)
    }
    val subjectUrlTemplateIsError by derivedStateOf {
        !subjectUrlTemplate.contains("{id}") || subjectUrlTemplateUnknownVariables.isNotEmpty()
    }

    // endregion

    // region 剧集列表

    private val subjectStage: ConfigSection<JsonApiSubjectStage> =
        searchConfig.sub({ it.subject }, { copy(subject = it) })

    val subjectRequest = RequestConfigState(subjectStage.sub({ it.request }, { copy(request = it) }), JsonApiVariables.SUBJECT)

    val allChannelFormats: List<JsonApiChannelFormat<*>> get() = JsonApiChannelFormat.entries
    var channelFormatId by subjectStage.prop({ it.channelFormatId }, { copy(channelFormatId = it) })

    val channelFormatDeclared = ChannelFormatDeclaredConfig(
        subjectStage.sub({ it.channelFormatDeclared }, { copy(channelFormatDeclared = it) }),
    )

    @Stable
    class ChannelFormatDeclaredConfig(section: ConfigSection<JsonApiChannelFormatDeclared.Config>) {
        var channels by section.prop({ it.channels }, { copy(channels = it) })
        val channelsIsError by derivedStateOf { channels.isEmpty() }
    }

    val channelFormatGrouped = ChannelFormatGroupedConfig(
        subjectStage.sub({ it.channelFormatGrouped }, { copy(channelFormatGrouped = it) }),
    )

    @Stable
    class ChannelFormatGroupedConfig(section: ConfigSection<JsonApiChannelFormatGrouped.Config>) {
        var selectChannels: String by section.prop({ it.selectChannels }, { copy(selectChannels = it) })
        val selectChannelsIsError by derivedStateOf { !isValidJsonPath(selectChannels) }
        var selectChannelName: String by section.prop({ it.selectChannelName }, { copy(selectChannelName = it) })
        val selectChannelNameIsError by derivedStateOf { !isValidJsonPath(selectChannelName) }
    }

    val episodes = EpisodeFieldsConfig(subjectStage.sub({ it.episodes }, { copy(episodes = it) }))

    @Stable
    class EpisodeFieldsConfig(section: ConfigSection<JsonApiEpisodeFields>) {
        var selectEpisodes: String by section.prop({ it.selectEpisodes }, { copy(selectEpisodes = it) })
        val selectEpisodesIsError by derivedStateOf { !isValidJsonPath(selectEpisodes) }
        var selectName: String by section.prop({ it.selectName }, { copy(selectName = it) })
        val selectNameIsError by derivedStateOf { !isValidJsonPath(selectName) }
        var selectId: String by section.prop({ it.selectId }, { copy(selectId = it) })
        val selectIdIsError by derivedStateOf { !isValidJsonPath(selectId) }
        var selectSort: String by section.prop({ it.selectSort }, { copy(selectSort = it) })
        val selectSortIsError by derivedStateOf { !isValidOptionalJsonPath(selectSort) }
        var selectType: String by section.prop({ it.selectType }, { copy(selectType = it) })
        val selectTypeIsError by derivedStateOf { !isValidOptionalJsonPath(selectType) }
        var typeMapping: String by section.prop({ it.typeMapping }, { copy(typeMapping = it) })
        var matchEpisodeSortFromName: String by section.prop(
            { it.matchEpisodeSortFromName },
            { copy(matchEpisodeSortFromName = it) },
        )
        val matchEpisodeSortFromNameIsError by derivedStateOf {
            matchEpisodeSortFromName.isBlank() || !isValidRegex(matchEpisodeSortFromName)
        }
        var nameTemplate: String by section.prop({ it.nameTemplate }, { copy(nameTemplate = it) })
        val nameTemplateUnknownVariables: List<String> by derivedStateOf {
            JsonApiTemplate.unknownVariables(nameTemplate, JsonApiVariables.EPISODE_NAME)
        }
        val nameTemplateIsError by derivedStateOf { nameTemplateUnknownVariables.isNotEmpty() }
        var episodeUrlTemplate: String by section.prop({ it.episodeUrlTemplate }, { copy(episodeUrlTemplate = it) })
        val episodeUrlTemplateUnknownVariables: List<String> by derivedStateOf {
            JsonApiTemplate.unknownVariables(episodeUrlTemplate, JsonApiVariables.EPISODE_URL)
        }
        val episodeUrlTemplateIsError by derivedStateOf {
            !episodeUrlTemplate.contains("{id}") || episodeUrlTemplateUnknownVariables.isNotEmpty()
        }
        var selectVideoUrl: String by section.prop({ it.selectVideoUrl }, { copy(selectVideoUrl = it) })
        val selectVideoUrlIsError by derivedStateOf { !isValidOptionalJsonPath(selectVideoUrl) }

        /**
         * 剧集列表里就有视频地址, 播放阶段的请求可以留空.
         */
        val hasVideoUrl: Boolean by derivedStateOf { selectVideoUrl.isNotBlank() }
    }

    // endregion

    // region 播放

    private val playStage: ConfigSection<JsonApiPlayStage> = searchConfig.sub({ it.play }, { copy(play = it) })

    val playRequest = RequestConfigState(
        playStage.sub({ it.request }, { copy(request = it) }),
        JsonApiVariables.PLAY,
        isOptional = { episodes.hasVideoUrl },
    )

    var selectCandidates: String by playStage.prop({ it.selectCandidates }, { copy(selectCandidates = it) })
    val selectCandidatesIsError by derivedStateOf { !isValidOptionalJsonPath(selectCandidates) }
    var selectCandidateChannel: String by playStage.prop({ it.selectCandidateChannel }, { copy(selectCandidateChannel = it) })
    val selectCandidateChannelIsError by derivedStateOf { !isValidOptionalJsonPath(selectCandidateChannel) }
    var selectVideoUrl: String by playStage.prop({ it.selectVideoUrl }, { copy(selectVideoUrl = it) })
    val selectVideoUrlIsError by derivedStateOf { !isValidJsonPath(selectVideoUrl) }
    var fallbackToFirstCandidate: Boolean by playStage.prop(
        { it.fallbackToFirstCandidate },
        { copy(fallbackToFirstCandidate = it) },
    )
    var referer: String by playStage.prop({ it.referer }, { copy(referer = it) })
    var videoHeaders: String by playStage.prop({ it.videoHeaders }, { copy(videoHeaders = it) })
    val videoHeadersUnknownVariables: List<String> by derivedStateOf {
        JsonApiTemplate.unknownVariables(videoHeaders, JsonApiVariables.PLAY)
    }
    val videoHeadersIsError by derivedStateOf { videoHeadersUnknownVariables.isNotEmpty() }

    // endregion

    var defaultResolution by searchConfig.prop({ it.defaultResolution }, { copy(defaultResolution = it) })
    var defaultSubtitleLanguage by searchConfig.prop(
        { it.defaultSubtitleLanguage },
        { copy(defaultSubtitleLanguage = it) },
    )

    val searchConfigState = derivedStateOf {
        argumentsStorage.container?.searchConfig
    }
}

/**
 * 一个阶段的请求配置. 地址、请求头与请求体里引用了 [variables] 之外的变量时标为错误: 代入时它们会原样发出去.
 *
 * @param variables 本阶段模板可用的变量名.
 * @param isOptional 本阶段的请求可以留空 (播放阶段在剧集列表自带视频地址时), 留空不算错误.
 */
@Stable
class RequestConfigState(
    section: ConfigSection<JsonApiRequestConfig>,
    private val variables: Set<String>,
    private val isOptional: () -> Boolean = { false },
) {
    var url: String by section.prop({ it.url }, { copy(url = it) })
    val urlUnknownVariables: List<String> by derivedStateOf { JsonApiTemplate.unknownVariables(url, variables) }
    val urlIsError by derivedStateOf { (url.isBlank() && !isOptional()) || urlUnknownVariables.isNotEmpty() }
    var method by section.prop({ it.method }, { copy(method = it) })
    var headers: String by section.prop({ it.headers }, { copy(headers = it) })
    val headersUnknownVariables: List<String> by derivedStateOf { JsonApiTemplate.unknownVariables(headers, variables) }
    val headersIsError by derivedStateOf { headersUnknownVariables.isNotEmpty() }
    var body: String by section.prop({ it.body }, { copy(body = it) })
    val bodyUnknownVariables: List<String> by derivedStateOf { JsonApiTemplate.unknownVariables(body, variables) }

    /**
     * 请求体引用了未知变量, 或代入占位值后不是合法 JSON. 变量位置既可能是字符串也可能是数字, 用 `0` 代入都合法.
     * GET 不发送请求体, 不检查.
     */
    val bodyIsError by derivedStateOf {
        method == JsonApiHttpMethod.POST && (
                bodyUnknownVariables.isNotEmpty() ||
                        body.isNotBlank() && !JsonApiMediaSourceEngine.isStrictJson(
                            JsonApiTemplate.substitute(
                                body,
                                JsonApiTemplate.variableNames(body).associateWith { "0" },
                                JsonApiTemplate.Escaping.NONE,
                            ),
                        )
                )
    }
}

private fun isValidJsonPath(expression: String): Boolean = JsonPath.compileOrNull(expression) != null

private fun isValidOptionalJsonPath(expression: String): Boolean = expression.isBlank() || isValidJsonPath(expression)

/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.jsonapi.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormat
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatDeclared
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatGrouped
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiHttpMethod
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVariables
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.animation.StandardEasing
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_base_url
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_base_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_channel_format_declared
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_channel_format_grouped
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_channel_format_no_channel
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_declared_channels
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_declared_channels_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_name_template
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_name_template_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_id
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_id_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_name
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_sort
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_sort_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_type
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_type_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_video_url
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_select_video_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_type_mapping
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_type_mapping_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_url_template
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_episode_url_template_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_fallback_to_first_candidate
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_fallback_to_first_candidate_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_grouped_select_channel_name
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_grouped_select_channel_name_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_grouped_select_channels
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_grouped_select_channels_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_headers
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_headers_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_page_size
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_page_size_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_request_optional_hint
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_body
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_body_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_headers
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_headers_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_interval
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_interval_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_method
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_url
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_request_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_aliases
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_aliases_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_section_api
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_candidate_channel
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_candidate_channel_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_candidates
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_candidates_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_episodes
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_episodes_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_id
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_id_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_items
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_items_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_name
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_name_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_video_url
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_select_video_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_step3
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_subject_url_template
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_subject_url_template_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_unknown_variables
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_video_headers
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_video_headers_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_referer_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_source_user_agent
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_source_user_agent_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_episode_sort_from_name_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_episode_sort_from_name_supporting
import me.him188.ani.app.ui.settings.mediasource.editor.CollapsibleAutoMatchSection
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigLongTextField
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.FormatSelectionButtonRow
import me.him188.ani.app.ui.settings.mediasource.editor.MediaDefaultsSection
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceConfigurationColumn
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.SectionTitle
import me.him188.ani.app.ui.settings.mediasource.editor.SwitchListItem
import me.him188.ani.app.ui.settings.mediasource.editor.UnsupportedFormatIdHint
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@TestOnly
object JsonApiConfigPaneTestTags {
    const val BASE_URL = "JsonApiConfigPane.baseUrl"
    const val SEARCH_REQUEST_URL = "JsonApiConfigPane.searchRequestUrl"
    const val SEARCH_REQUEST_BODY = "JsonApiConfigPane.searchRequestBody"
    const val DECLARED_CHANNELS = "JsonApiConfigPane.declaredChannels"
    const val CHANNEL_FORMAT_PREFIX = "JsonApiConfigPane.channelFormat."
    const val EPISODE_SELECT_VIDEO_URL = "JsonApiConfigPane.episodeSelectVideoUrl"
}

@Composable
internal fun JsonApiConfigurationPane(
    state: JsonApiConfigState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    MediaSourceConfigurationColumn(
        state, modifier, contentPadding,
        verticalSpacing = verticalSpacing, textFieldShape = textFieldShape,
    ) {
        val listItemColors = MediaSourceEditorDefaults.listItemColors
        val itemPadding = Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))

        SectionTitle(stringResource(Lang.settings_mediasource_jsonapi_section_api), Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        Column {
            ConfigTextField(
                state.baseUrl, { state.baseUrl = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_base_url),
                Modifier.testTag(JsonApiConfigPaneTestTags.BASE_URL),
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_base_url_supporting),
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.headers, { state.headers = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_headers),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_headers_supporting),
                enabled = state.enableEdit,
                multiline = true,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.userAgent, { state.userAgent = it },
                label = stringResource(Lang.settings_mediasource_selector_config_source_user_agent),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_selector_config_source_user_agent_description),
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigLongTextField(
                state.requestInterval.inWholeMilliseconds,
                { state.requestInterval = it.milliseconds },
                label = stringResource(Lang.settings_mediasource_jsonapi_request_interval),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_request_interval_supporting),
                isValid = { it >= 0 },
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        // 步骤 1: 搜索条目

        SectionTitle(MediaSourceEditorDefaults.STEP_NAME_1, Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        RequestConfigSection(
            state.searchRequest,
            variables = JsonApiVariables.SEARCH.asTemplateList(),
            enabled = state.enableEdit,
            urlTestTag = JsonApiConfigPaneTestTags.SEARCH_REQUEST_URL,
            bodyTestTag = JsonApiConfigPaneTestTags.SEARCH_REQUEST_BODY,
            verticalSpacing = verticalSpacing,
            textFieldShape = textFieldShape,
        )

        Column(Modifier.padding(top = verticalSpacing)) {
            ConfigLongTextField(
                state.pageSize.toLong(),
                { state.pageSize = it.toInt() },
                label = stringResource(Lang.settings_mediasource_jsonapi_page_size),
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_page_size_supporting),
                isValid = { it in 1..Int.MAX_VALUE.toLong() },
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.selectItems, { state.selectItems = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_select_items),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_items_supporting),
                isError = state.selectItemsIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.selectName, { state.selectName = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_select_name),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_name_supporting),
                isError = state.selectNameIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.selectId, { state.selectId = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_select_id),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_id_supporting),
                isError = state.selectIdIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.selectAliases, { state.selectAliases = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_select_aliases),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_aliases_supporting),
                isError = state.selectAliasesIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.subjectUrlTemplate, { state.subjectUrlTemplate = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_subject_url_template),
                itemPadding,
                supportingText = unknownVariablesText(state.subjectUrlTemplateUnknownVariables)
                    ?: stringResource(Lang.settings_mediasource_jsonapi_subject_url_template_supporting),
                isError = state.subjectUrlTemplateIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        // 步骤 2: 剧集列表

        SectionTitle(MediaSourceEditorDefaults.STEP_NAME_2, Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        RequestConfigSection(
            state.subjectRequest,
            variables = JsonApiVariables.SUBJECT.asTemplateList(),
            enabled = state.enableEdit,
            verticalSpacing = verticalSpacing,
            textFieldShape = textFieldShape,
        )

        FormatSelectionButtonRow(
            state.allChannelFormats,
            isSelected = { state.channelFormatId == it.id },
            onSelect = { state.channelFormatId = it.id },
            Modifier.fillMaxWidth().padding(top = verticalSpacing, bottom = 4.dp),
            enabled = state.enableEdit,
            buttonModifier = { Modifier.testTag(JsonApiConfigPaneTestTags.CHANNEL_FORMAT_PREFIX + it.id.value) },
        ) { format ->
            Text(
                when (format) { // type-safe to handle all formats
                    JsonApiChannelFormatDeclared -> stringResource(Lang.settings_mediasource_jsonapi_channel_format_declared)
                    JsonApiChannelFormatGrouped -> stringResource(Lang.settings_mediasource_jsonapi_channel_format_grouped)
                    JsonApiChannelFormatNoChannel -> stringResource(Lang.settings_mediasource_jsonapi_channel_format_no_channel)
                },
                softWrap = false,
            )
        }

        AnimatedContent(
            state.channelFormatId,
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
        ) { formatId ->
            JsonApiChannelFormatColumn(
                JsonApiChannelFormat.findById(formatId), state,
                Modifier.fillMaxWidth(),
                verticalSpacing = verticalSpacing, textFieldShape = textFieldShape,
            )
        }

        JsonApiEpisodeFieldsColumn(
            state.episodes,
            enabled = state.enableEdit,
            Modifier.fillMaxWidth(),
            verticalSpacing = verticalSpacing, textFieldShape = textFieldShape,
        )

        // 步骤 3: 播放地址

        SectionTitle(stringResource(Lang.settings_mediasource_jsonapi_step3), Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        JsonApiPlayStageColumn(
            state,
            Modifier.fillMaxWidth(),
            verticalSpacing = verticalSpacing, textFieldShape = textFieldShape,
        )

        MediaDefaultsSection(
            state.defaultResolution, { state.defaultResolution = it },
            state.defaultSubtitleLanguage, { state.defaultSubtitleLanguage = it },
            enabled = state.enableEdit,
            Modifier.padding(top = verticalSpacing),
            listItemColors = listItemColors,
        )

        CollapsibleAutoMatchSection(
            state.autoMatch,
            enabled = state.enableEdit,
            Modifier.padding(top = verticalSpacing),
            textFieldShape = textFieldShape,
            verticalSpacing = verticalSpacing,
            listItemColors = listItemColors,
        )
    }
}

/**
 * 一个阶段的请求: 地址、方法、请求头与请求体.
 *
 * @param variables 本阶段模板里可用的变量, 展示在说明文字里.
 */
@Composable
internal fun RequestConfigSection(
    state: RequestConfigState,
    variables: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    urlTestTag: String? = null,
    bodyTestTag: String? = null,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    Column(modifier) {
        val itemPadding = Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
        ConfigTextField(
            state.url, { state.url = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_request_url),
            if (urlTestTag != null) Modifier.testTag(urlTestTag) else Modifier,
            supportingText = unknownVariablesText(state.urlUnknownVariables)
                ?: stringResource(Lang.settings_mediasource_jsonapi_request_url_supporting, variables),
            isError = state.urlIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        Text(
            stringResource(Lang.settings_mediasource_jsonapi_request_method),
            itemPadding,
            style = MaterialTheme.typography.labelLarge,
        )
        FormatSelectionButtonRow(
            JsonApiHttpMethod.entries,
            isSelected = { state.method == it },
            onSelect = { state.method = it },
            Modifier.fillMaxWidth().padding(top = 4.dp),
            enabled = enabled,
        ) { Text(it.name, softWrap = false) }
        ConfigTextField(
            state.headers, { state.headers = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_request_headers),
            itemPadding,
            supportingText = unknownVariablesText(state.headersUnknownVariables)
                ?: stringResource(Lang.settings_mediasource_jsonapi_request_headers_supporting),
            isError = state.headersIsError,
            enabled = enabled,
            multiline = true,
            shape = textFieldShape,
        )
        // GET 不发送请求体, 不显示; 已填的内容保留, 切回 POST 时还在
        if (state.method == JsonApiHttpMethod.POST) {
            ConfigTextField(
                state.body, { state.body = it },
                label = stringResource(Lang.settings_mediasource_jsonapi_request_body),
                itemPadding.then(if (bodyTestTag != null) Modifier.testTag(bodyTestTag) else Modifier),
                supportingText = unknownVariablesText(state.bodyUnknownVariables)
                    ?: stringResource(Lang.settings_mediasource_jsonapi_request_body_supporting, variables),
                isError = state.bodyIsError,
                enabled = enabled,
                multiline = true,
                shape = textFieldShape,
            )
        }
    }
}

/**
 * 模板里有 [unknown] 这些未知变量时的说明文字, 没有时为 `null` (用字段自己的说明).
 */
@Composable
private fun unknownVariablesText(unknown: List<String>): String? {
    if (unknown.isEmpty()) return null
    return stringResource(Lang.settings_mediasource_jsonapi_unknown_variables, unknown.asTemplateList())
}

private fun Collection<String>.asTemplateList(): String = joinToString(", ") { "{$it}" }

@Composable
private fun JsonApiChannelFormatColumn(
    format: JsonApiChannelFormat<*>?,
    state: JsonApiConfigState,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    Column(modifier) {
        when (format) {
            JsonApiChannelFormatDeclared -> {
                val conf = state.channelFormatDeclared
                ConfigLinesTextField(
                    conf.channels,
                    parse = { JsonApiChannelFormatDeclared.Config.parseChannelLines(it) },
                    format = { JsonApiChannelFormatDeclared.Config.formatChannelLines(it) },
                    onValueChange = { conf.channels = it },
                    label = stringResource(Lang.settings_mediasource_jsonapi_declared_channels),
                    Modifier.testTag(JsonApiConfigPaneTestTags.DECLARED_CHANNELS),
                    supportingText = stringResource(Lang.settings_mediasource_jsonapi_declared_channels_supporting),
                    isError = conf.channelsIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
            }

            JsonApiChannelFormatGrouped -> Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                val conf = state.channelFormatGrouped
                ConfigTextField(
                    conf.selectChannels, { conf.selectChannels = it },
                    label = stringResource(Lang.settings_mediasource_jsonapi_grouped_select_channels),
                    supportingText = stringResource(Lang.settings_mediasource_jsonapi_grouped_select_channels_supporting),
                    isError = conf.selectChannelsIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.selectChannelName, { conf.selectChannelName = it },
                    label = stringResource(Lang.settings_mediasource_jsonapi_grouped_select_channel_name),
                    supportingText = stringResource(Lang.settings_mediasource_jsonapi_grouped_select_channel_name_supporting),
                    isError = conf.selectChannelNameIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
            }

            JsonApiChannelFormatNoChannel -> {}

            null -> UnsupportedFormatIdHint(state.channelFormatId.value, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun JsonApiEpisodeFieldsColumn(
    conf: JsonApiConfigState.EpisodeFieldsConfig,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        ConfigTextField(
            conf.selectEpisodes, { conf.selectEpisodes = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_select_episodes),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_episodes_supporting),
            isError = conf.selectEpisodesIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.selectName, { conf.selectName = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_select_name),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_name_supporting),
            isError = conf.selectNameIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.selectId, { conf.selectId = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_select_id),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_episode_select_id_supporting),
            isError = conf.selectIdIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.selectSort, { conf.selectSort = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_select_sort),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_episode_select_sort_supporting),
            isError = conf.selectSortIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.matchEpisodeSortFromName, { conf.matchEpisodeSortFromName = it },
            label = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_label),
            supportingText = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_supporting),
            isError = conf.matchEpisodeSortFromNameIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.selectType, { conf.selectType = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_select_type),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_episode_select_type_supporting),
            isError = conf.selectTypeIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.typeMapping, { conf.typeMapping = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_type_mapping),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_episode_type_mapping_supporting),
            enabled = enabled,
            multiline = true,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.nameTemplate, { conf.nameTemplate = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_name_template),
            supportingText = unknownVariablesText(conf.nameTemplateUnknownVariables)
                ?: stringResource(Lang.settings_mediasource_jsonapi_episode_name_template_supporting),
            isError = conf.nameTemplateIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.episodeUrlTemplate, { conf.episodeUrlTemplate = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_url_template),
            supportingText = unknownVariablesText(conf.episodeUrlTemplateUnknownVariables)
                ?: stringResource(Lang.settings_mediasource_jsonapi_episode_url_template_supporting),
            isError = conf.episodeUrlTemplateIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigTextField(
            conf.selectVideoUrl, { conf.selectVideoUrl = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_episode_select_video_url),
            Modifier.testTag(JsonApiConfigPaneTestTags.EPISODE_SELECT_VIDEO_URL),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_episode_select_video_url_supporting),
            isError = conf.selectVideoUrlIsError,
            enabled = enabled,
            shape = textFieldShape,
        )
    }
}

/**
 * 步骤 3 的全部配置. 剧集详情页底部的配置面板也用它.
 */
@Composable
internal fun JsonApiPlayStageColumn(
    state: JsonApiConfigState,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    Column(modifier) {
        val itemPadding = Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
        if (state.episodes.hasVideoUrl) {
            Text(
                stringResource(Lang.settings_mediasource_jsonapi_play_request_optional_hint),
                Modifier.padding(bottom = verticalSpacing),
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        RequestConfigSection(
            state.playRequest,
            variables = JsonApiVariables.PLAY.asTemplateList(),
            enabled = state.enableEdit,
            verticalSpacing = verticalSpacing,
            textFieldShape = textFieldShape,
        )
        ConfigTextField(
            state.selectCandidates, { state.selectCandidates = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_select_candidates),
            Modifier.padding(top = verticalSpacing),
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_candidates_supporting),
            isError = state.selectCandidatesIsError,
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
        ConfigTextField(
            state.selectCandidateChannel, { state.selectCandidateChannel = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_select_candidate_channel),
            itemPadding,
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_candidate_channel_supporting),
            isError = state.selectCandidateChannelIsError,
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
        ConfigTextField(
            state.selectVideoUrl, { state.selectVideoUrl = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_select_video_url),
            itemPadding,
            supportingText = stringResource(Lang.settings_mediasource_jsonapi_select_video_url_supporting),
            isError = state.selectVideoUrlIsError,
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
        SwitchListItem(
            stringResource(Lang.settings_mediasource_jsonapi_fallback_to_first_candidate),
            state.fallbackToFirstCandidate, { state.fallbackToFirstCandidate = it },
            supporting = stringResource(Lang.settings_mediasource_jsonapi_fallback_to_first_candidate_supporting),
            enabled = state.enableEdit,
        )
        ConfigTextField(
            state.referer, { state.referer = it },
            label = "Referer",
            supportingText = stringResource(Lang.settings_mediasource_selector_config_referer_description),
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
        ConfigTextField(
            state.videoHeaders, { state.videoHeaders = it },
            label = stringResource(Lang.settings_mediasource_jsonapi_video_headers),
            itemPadding,
            supportingText = unknownVariablesText(state.videoHeadersUnknownVariables)
                ?: stringResource(Lang.settings_mediasource_jsonapi_video_headers_supporting, JsonApiVariables.PLAY.asTemplateList()),
            isError = state.videoHeadersIsError,
            enabled = state.enableEdit,
            multiline = true,
            shape = textFieldShape,
        )
    }
}

/**
 * 以多行文本编辑一个列表. 文本保留用户的原始输入, 只在解析结果变化时写回; 外部值变化 (如导入配置) 时用它刷新文本.
 */
@Composable
private fun <T> ConfigLinesTextField(
    value: List<T>,
    parse: (String) -> List<T>,
    format: (List<T>) -> String,
    onValueChange: (List<T>) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if (parse(text) != value) text = format(value)
    }
    ConfigTextField(
        text,
        {
            text = it
            val parsed = parse(it)
            if (parsed != value) onValueChange(parsed)
        },
        label, modifier, supportingText,
        isError = isError,
        enabled = enabled,
        multiline = true,
        shape = shape,
    )
}

@Composable
internal fun rememberTestJsonApiConfigState(
    arguments: JsonApiMediaSourceArguments = JsonApiMediaSourceArguments.Default,
): JsonApiConfigState = remember {
    JsonApiConfigState(createTestSaveableStorage(arguments), allowEditState = stateOf(true))
}

@Composable
@Preview
private fun PreviewJsonApiConfigurationPane() = ProvideCompositionLocalsForPreview {
    Surface {
        JsonApiConfigurationPane(rememberTestJsonApiConfigState())
    }
}

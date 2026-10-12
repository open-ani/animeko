/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_api_advanced
import me.him188.ani.app.ui.lang.settings_api_aliases_path
import me.him188.ani.app.ui.lang.settings_api_auto_match
import me.him188.ani.app.ui.lang.settings_api_basic
import me.him188.ani.app.ui.lang.settings_api_body
import me.him188.ani.app.ui.lang.settings_api_candidates_id
import me.him188.ani.app.ui.lang.settings_api_candidates_path
import me.him188.ani.app.ui.lang.settings_api_candidates_url
import me.him188.ani.app.ui.lang.settings_api_channel_id
import me.him188.ani.app.ui.lang.settings_api_channel_name
import me.him188.ani.app.ui.lang.settings_api_channel_tiers
import me.him188.ani.app.ui.lang.settings_api_channels_path
import me.him188.ani.app.ui.lang.settings_api_configuration
import me.him188.ani.app.ui.lang.settings_api_detail
import me.him188.ani.app.ui.lang.settings_api_episode_id_path
import me.him188.ani.app.ui.lang.settings_api_episode_kind
import me.him188.ani.app.ui.lang.settings_api_episode_name_path
import me.him188.ani.app.ui.lang.settings_api_episode_number
import me.him188.ani.app.ui.lang.settings_api_episode_url
import me.him188.ani.app.ui.lang.settings_api_episodes_path
import me.him188.ani.app.ui.lang.settings_api_filter_by_subject_name
import me.him188.ani.app.ui.lang.settings_api_first_page
import me.him188.ani.app.ui.lang.settings_api_headers
import me.him188.ani.app.ui.lang.settings_api_id_path
import me.him188.ani.app.ui.lang.settings_api_items_path
import me.him188.ani.app.ui.lang.settings_api_json
import me.him188.ani.app.ui.lang.settings_api_kind_prefixes
import me.him188.ani.app.ui.lang.settings_api_matching
import me.him188.ani.app.ui.lang.settings_api_max_pages
import me.him188.ani.app.ui.lang.settings_api_name_path
import me.him188.ani.app.ui.lang.settings_api_page_size
import me.him188.ani.app.ui.lang.settings_api_paginate
import me.him188.ani.app.ui.lang.settings_api_playback
import me.him188.ani.app.ui.lang.settings_api_request_url
import me.him188.ani.app.ui.lang.settings_api_search_rules
import me.him188.ani.app.ui.lang.settings_api_subject_url
import me.him188.ani.app.ui.lang.settings_api_success_path
import me.him188.ani.app.ui.lang.settings_api_url_path
import me.him188.ani.app.ui.lang.settings_api_validation_error
import me.him188.ani.app.ui.lang.settings_api_video_headers
import me.him188.ani.app.ui.lang.settings_api_website
import me.him188.ani.app.ui.lang.settings_mediasource_rss_auto_save_hint
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_icon_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count
import me.him188.ani.app.ui.lang.settings_mediasource_selector_tier
import me.him188.ani.app.ui.lang.settings_mediasource_selector_tier_description
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.rss.edit.MediaSourceHeadline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object ApiConfigurationPaneTestTags {
    const val NAME = "api-name"
    const val HEADERS = "api-headers"
    const val JSON = "api-json"
    const val JSON_ERROR = "api-json-error"
}

/**
 * 编辑配置. 表单与 JSON 编辑器读写同一份配置, 修改即保存.
 */
@Composable
internal fun ApiConfigurationPane(
    state: EditApiMediaSourceState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding,
    textFieldShape: Shape = MediaSourceConfigurationDefaults.outlinedTextFieldShape,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        // 大图标和标题
        MediaSourceHeadline(state.iconUrl, state.displayName)

        Column(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    !state.showJsonEditor, { state.showJsonEditor = false },
                    SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(Lang.settings_api_configuration))
                }
                SegmentedButton(
                    state.showJsonEditor, { state.showJsonEditor = true },
                    SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(stringResource(Lang.settings_api_advanced))
                }
            }

            if (state.showJsonEditor) {
                ApiJsonEditor(
                    state.jsonEditor,
                    enabled = state.enableEdit,
                    Modifier.padding(top = verticalSpacing),
                    shape = textFieldShape,
                )
            } else {
                ApiConfigurationForm(
                    state,
                    Modifier.padding(top = verticalSpacing),
                    verticalSpacing = verticalSpacing,
                    textFieldShape = textFieldShape,
                )
            }

            state.validationError?.let { error ->
                Text(
                    stringResource(Lang.settings_api_validation_error, error),
                    Modifier.padding(top = verticalSpacing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(Modifier.align(Alignment.End).padding(top = 20.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelMedium,
                    MaterialTheme.colorScheme.outline,
                ) {
                    Text(stringResource(Lang.settings_mediasource_rss_auto_save_hint))
                }
            }
        }
    }
}

@Composable
private fun ApiJsonEditor(
    field: DraftField<ApiMediaSourceArguments>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = MediaSourceConfigurationDefaults.outlinedTextFieldShape,
) {
    OutlinedTextField(
        field.text, { field.edit(it) },
        modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .testTag(ApiConfigurationPaneTestTags.JSON),
        label = { Text(stringResource(Lang.settings_api_json)) },
        isError = field.isError,
        supportingText = field.error?.let { error ->
            { Text(error.take(200), Modifier.testTag(ApiConfigurationPaneTestTags.JSON_ERROR)) }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        shape = shape,
        enabled = enabled,
    )
}

@Composable
private fun ApiConfigurationForm(
    state: EditApiMediaSourceState,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding,
    textFieldShape: Shape = MediaSourceConfigurationDefaults.outlinedTextFieldShape,
) {
    val enabled = state.enableEdit
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(
        modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        SectionTitle(Lang.settings_api_basic)
        ApiTextField(
            state.displayName, { state.displayName = it },
            Lang.settings_mediasource_selector_config_name, enabled, textFieldShape,
            Modifier.testTag(ApiConfigurationPaneTestTags.NAME),
            isError = state.displayNameIsError,
            placeholder = Lang.settings_mediasource_selector_config_name_placeholder,
        )
        ApiTextField(
            state.websiteUrl, { state.websiteUrl = it.trim() },
            Lang.settings_api_website, enabled, textFieldShape,
        )
        ApiTextField(
            state.iconUrl, { state.iconUrl = it.trim() },
            Lang.settings_mediasource_selector_config_icon_url, enabled, textFieldShape,
        )
        ApiDraftTextField(
            state.headers, Lang.settings_api_headers, enabled, textFieldShape, DraftKind.JSON,
            Modifier.testTag(ApiConfigurationPaneTestTags.HEADERS),
        )

        SectionTitle(Lang.settings_api_search_rules)
        val search = state.search
        ApiRequestFields(search.request, enabled, textFieldShape, tagPrefix = "api-search")
        ApiTextField(search.itemsPath, { search.itemsPath = it }, Lang.settings_api_items_path, enabled, textFieldShape)
        ApiTextField(search.idPath, { search.idPath = it }, Lang.settings_api_id_path, enabled, textFieldShape)
        ApiTextField(search.namePath, { search.namePath = it }, Lang.settings_api_name_path, enabled, textFieldShape)
        ApiTextField(
            search.aliasesPath, { search.aliasesPath = it },
            Lang.settings_api_aliases_path, enabled, textFieldShape,
        )
        ApiTextField(
            search.subjectUrl, { search.subjectUrl = it.trim() },
            Lang.settings_api_subject_url, enabled, textFieldShape,
        )
        SwitchItem(Lang.settings_api_paginate, search.paginate, { search.paginate = it }, enabled, listItemColors)
        AniAnimatedVisibility(search.paginate) {
            Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                ApiDraftTextField(
                    search.firstPage, Lang.settings_api_first_page, enabled, textFieldShape, DraftKind.NUMBER,
                )
                ApiDraftTextField(
                    search.pageSize, Lang.settings_api_page_size, enabled, textFieldShape, DraftKind.NUMBER,
                )
                ApiDraftTextField(
                    search.maxPages, Lang.settings_api_max_pages, enabled, textFieldShape, DraftKind.NUMBER,
                )
            }
        }

        SectionTitle(Lang.settings_api_detail)
        val detail = state.detail
        ApiRequestFields(detail.request, enabled, textFieldShape, tagPrefix = "api-detail")
        ApiTextField(
            detail.channelsPath, { detail.channelsPath = it },
            Lang.settings_api_channels_path, enabled, textFieldShape,
        )
        ApiTextField(
            detail.channelIdPath, { detail.channelIdPath = it },
            Lang.settings_api_channel_id, enabled, textFieldShape,
        )
        ApiTextField(
            detail.channelNamePath, { detail.channelNamePath = it },
            Lang.settings_api_channel_name, enabled, textFieldShape,
        )
        ApiTextField(
            detail.episodesPath, { detail.episodesPath = it },
            Lang.settings_api_episodes_path, enabled, textFieldShape,
        )
        ApiTextField(
            detail.episodeIdPath, { detail.episodeIdPath = it },
            Lang.settings_api_episode_id_path, enabled, textFieldShape,
        )
        ApiTextField(
            detail.episodeNamePath, { detail.episodeNamePath = it },
            Lang.settings_api_episode_name_path, enabled, textFieldShape,
        )
        ApiTextField(
            detail.episodeNumberPath, { detail.episodeNumberPath = it },
            Lang.settings_api_episode_number, enabled, textFieldShape,
        )
        ApiTextField(
            detail.episodeKindPath, { detail.episodeKindPath = it },
            Lang.settings_api_episode_kind, enabled, textFieldShape,
        )
        ApiDraftTextField(
            detail.episodeKindPrefixes, Lang.settings_api_kind_prefixes, enabled, textFieldShape, DraftKind.JSON,
        )
        ApiTextField(
            detail.episodeUrl, { detail.episodeUrl = it.trim() },
            Lang.settings_api_episode_url, enabled, textFieldShape,
        )

        SectionTitle(Lang.settings_api_playback)
        val playback = state.playback
        ApiRequestFields(playback.request, enabled, textFieldShape, tagPrefix = "api-playback")
        ApiTextField(playback.urlPath, { playback.urlPath = it }, Lang.settings_api_url_path, enabled, textFieldShape)
        ApiTextField(
            playback.successPath, { playback.successPath = it },
            Lang.settings_api_success_path, enabled, textFieldShape,
        )
        ApiTextField(
            playback.candidatesPath, { playback.candidatesPath = it },
            Lang.settings_api_candidates_path, enabled, textFieldShape,
        )
        ApiTextField(
            playback.candidateIdPath, { playback.candidateIdPath = it },
            Lang.settings_api_candidates_id, enabled, textFieldShape,
        )
        ApiTextField(
            playback.candidateUrlPath, { playback.candidateUrlPath = it },
            Lang.settings_api_candidates_url, enabled, textFieldShape,
        )
        ApiDraftTextField(
            playback.videoHeaders, Lang.settings_api_video_headers, enabled, textFieldShape, DraftKind.JSON,
        )

        SectionTitle(Lang.settings_api_matching)
        val matching = state.matching
        SwitchItem(
            Lang.settings_api_auto_match, matching.autoMatch, { matching.autoMatch = it }, enabled, listItemColors,
        )
        SwitchItem(
            Lang.settings_api_filter_by_subject_name, matching.filterBySubjectName,
            { matching.filterBySubjectName = it }, enabled, listItemColors,
        )
        ApiDraftTextField(
            matching.searchNamesCount, Lang.settings_mediasource_selector_config_search_subject_names_count,
            enabled, textFieldShape, DraftKind.NUMBER,
        )
        ApiTextField(
            matching.resolution, { matching.resolution = it },
            Lang.settings_mediasource_selector_config_default_resolution, enabled, textFieldShape,
        )
        ApiTextField(
            matching.subtitleLanguage, { matching.subtitleLanguage = it },
            Lang.settings_mediasource_selector_config_default_subtitle_language, enabled, textFieldShape,
        )
        ApiDraftTextField(
            matching.tier, Lang.settings_mediasource_selector_tier, enabled, textFieldShape, DraftKind.NUMBER,
            supportingText = Lang.settings_mediasource_selector_tier_description,
        )
        ApiDraftTextField(
            matching.channelTiers, Lang.settings_api_channel_tiers, enabled, textFieldShape, DraftKind.JSON,
        )
    }
}

@Composable
private fun ApiRequestFields(
    fields: EditApiMediaSourceState.RequestFields,
    enabled: Boolean,
    textFieldShape: Shape,
    tagPrefix: String,
) {
    ApiTextField(
        fields.url, { fields.url = it.trim() },
        Lang.settings_api_request_url, enabled, textFieldShape,
        Modifier.testTag("$tagPrefix-url"),
    )
    SingleChoiceSegmentedButtonRow {
        REQUEST_METHODS.forEachIndexed { index, method ->
            SegmentedButton(
                fields.method == method, { fields.method = method },
                SegmentedButtonDefaults.itemShape(index, REQUEST_METHODS.size),
                enabled = enabled,
            ) {
                Text(method)
            }
        }
    }
    ApiDraftTextField(
        fields.headers, Lang.settings_api_headers, enabled, textFieldShape, DraftKind.JSON,
        Modifier.testTag("$tagPrefix-headers"),
    )
    AniAnimatedVisibility(fields.isPost) {
        ApiDraftTextField(
            fields.body, Lang.settings_api_body, enabled, textFieldShape, DraftKind.JSON,
            Modifier.testTag("$tagPrefix-body"),
        )
    }
}

private val REQUEST_METHODS = listOf("GET", "POST")

private enum class DraftKind {
    NUMBER,
    JSON,
}

@Composable
private fun ApiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    enabled: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: StringResource? = null,
) {
    OutlinedTextField(
        value, onValueChange,
        modifier
            .fillMaxWidth()
            .moveFocusOnEnter(),
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.outline) } },
        isError = isError,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true,
        shape = shape,
        enabled = enabled,
    )
}

@Composable
private fun ApiDraftTextField(
    field: DraftField<*>,
    label: StringResource,
    enabled: Boolean,
    shape: Shape,
    kind: DraftKind,
    modifier: Modifier = Modifier,
    supportingText: StringResource? = null,
) {
    val error = field.error
    // 数字框只标红 (异常信息是平台相关的英文); JSON 框显示解析器给出的位置信息
    val supporting = when (kind) {
        DraftKind.NUMBER -> supportingText?.let { stringResource(it) }
        DraftKind.JSON -> error?.take(200) ?: supportingText?.let { stringResource(it) }
    }
    OutlinedTextField(
        field.text, { field.edit(it) },
        modifier
            .fillMaxWidth()
            .then(if (kind == DraftKind.NUMBER) Modifier.moveFocusOnEnter() else Modifier),
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        textStyle = when (kind) {
            DraftKind.NUMBER -> LocalTextStyle.current
            DraftKind.JSON -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        },
        keyboardOptions = when (kind) {
            DraftKind.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            DraftKind.JSON -> KeyboardOptions.Default
        },
        singleLine = kind == DraftKind.NUMBER,
        minLines = if (kind == DraftKind.JSON) 2 else 1,
        shape = shape,
        enabled = enabled,
    )
}

@Composable
private fun SwitchItem(
    headline: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    colors: ListItemColors,
) {
    ListItem(
        headlineContent = { Text(stringResource(headline)) },
        Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        trailingContent = {
            Switch(checked, onCheckedChange, enabled = enabled)
        },
        colors = colors,
    )
}

@Composable
private fun SectionTitle(title: StringResource) {
    Row(Modifier.padding(top = 4.dp)) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.titleMedium,
            MaterialTheme.colorScheme.primary,
        ) {
            Text(stringResource(title))
        }
    }
}

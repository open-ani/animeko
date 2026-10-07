/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiRequest
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.ui.lang.Lang
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
import me.him188.ani.app.ui.lang.settings_api_detail
import me.him188.ani.app.ui.lang.settings_api_episode_kind
import me.him188.ani.app.ui.lang.settings_api_episode_number
import me.him188.ani.app.ui.lang.settings_api_episode_url
import me.him188.ani.app.ui.lang.settings_api_episodes_path
import me.him188.ani.app.ui.lang.settings_api_first_page
import me.him188.ani.app.ui.lang.settings_api_filter_by_subject_name
import me.him188.ani.app.ui.lang.settings_api_headers
import me.him188.ani.app.ui.lang.settings_api_id_path
import me.him188.ani.app.ui.lang.settings_api_items_path
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
import me.him188.ani.app.ui.lang.settings_api_tier
import me.him188.ani.app.ui.lang.settings_api_url_path
import me.him188.ani.app.ui.lang.settings_api_video_headers
import me.him188.ani.app.ui.lang.settings_api_website
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_icon_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count
import me.him188.ani.datasources.api.source.MediaSourceTier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Keeps incomplete JSON and numeric field edits until they can be validated. */
internal class ApiConfigurationFormState(private val codecs: MediaSourceCodecManager) {
    var text by mutableStateOf("")
        private set
    var value by mutableStateOf<ApiMediaSourceArguments?>(null)
        private set
    val fields = mutableStateMapOf<String, String>()
    private var parseError: Throwable? = null

    fun load(text: String) {
        this.text = text
        val parsed = runCatching { decodeApiArguments(text, codecs) }
        parseError = parsed.exceptionOrNull()
        value = parsed.getOrNull()
        value?.let { args ->
            fields.clear()
            fields["headers"] = apiEditorJson.encodeToString(args.headers)
            listOf("search" to args.search.request, "detail" to args.detail.request, "playback" to args.playback.request).forEach { (key, request) ->
                fields["$key.headers"] = apiEditorJson.encodeToString(request.headers)
                fields["$key.body"] = request.body?.let { apiEditorJson.encodeToString(JsonElement.serializer(), it) }.orEmpty()
            }
            fields["kindPrefixes"] = apiEditorJson.encodeToString(args.detail.episodeKindPrefixes)
            fields["videoHeaders"] = apiEditorJson.encodeToString(args.playback.videoHeaders)
            fields["channelTiers"] = apiEditorJson.encodeToString(args.channelTiers)
            fields["firstPage"] = args.search.firstPage.toString()
            fields["pageSize"] = args.search.pageSize.toString()
            fields["maxPages"] = args.search.maxPages.toString()
            fields["searchNamesCount"] = args.searchNamesCount.toString()
            fields["tier"] = args.tier.value.toString()
        }
    }

    fun edit(transform: (ApiMediaSourceArguments) -> ApiMediaSourceArguments): String {
        value = transform(requireNotNull(value))
        return sync()
    }

    fun editField(key: String, value: String): String {
        fields[key] = value
        return sync()
    }

    private fun sync(): String {
        runCatching { build() }.getOrNull()?.let {
            text = apiEditorJson.encodeToString(ApiMediaSourceArguments.serializer(), it)
        }
        return text
    }

    private fun strings(key: String): Map<String, String> =
        apiEditorJson.decodeFromString(fields.getValue(key).ifBlank { "{}" })

    private fun number(key: String): Int = fields.getValue(key).toIntOrNull()
        ?: error("$key: expected an integer")

    private fun request(key: String, value: ApiRequest): ApiRequest = value.copy(
        headers = strings("$key.headers"),
        body = if (value.method == "GET" || fields.getValue("$key.body").isBlank()) null
        else apiEditorJson.parseToJsonElement(fields.getValue("$key.body")).takeUnless { it == JsonNull },
    )

    private fun build(): ApiMediaSourceArguments {
        parseError?.let { throw it }
        val args = requireNotNull(value)
        return args.copy(
            headers = strings("headers"),
            search = args.search.copy(request = request("search", args.search.request), firstPage = number("firstPage"),
                pageSize = number("pageSize"), maxPages = number("maxPages")),
            detail = args.detail.copy(request = request("detail", args.detail.request), episodeKindPrefixes = strings("kindPrefixes")),
            playback = args.playback.copy(request = request("playback", args.playback.request), videoHeaders = strings("videoHeaders")),
            searchNamesCount = number("searchNamesCount"),
            tier = MediaSourceTier(fields.getValue("tier").toUIntOrNull() ?: error("tier: expected an unsigned integer")),
            channelTiers = apiEditorJson.decodeFromString(fields.getValue("channelTiers").ifBlank { "{}" }),
        )
    }

    fun configuration(): ApiMediaSourceArguments = build().also { it.validate() }
}

@Composable
internal fun ApiConfigurationForm(state: ApiConfigurationFormState, enabled: Boolean, onChange: (String) -> Unit) {
    val args = state.value ?: return
    fun edit(transform: (ApiMediaSourceArguments) -> ApiMediaSourceArguments) = onChange(state.edit(transform))
    @Composable fun field(key: String, label: StringResource, json: Boolean = false) {
        ApiField(state.fields.getValue(key), { onChange(state.editField(key, it)) }, label, enabled, key, json)
    }
    ApiSection(stringResource(Lang.settings_api_basic), initiallyExpanded = true) {
        ApiField(args.name, { edit { args -> args.copy(name = it) } }, Lang.settings_mediasource_selector_config_name, enabled, "name")
        ApiField(args.websiteUrl, { edit { args -> args.copy(websiteUrl = it) } }, Lang.settings_api_website, enabled, "website")
        ApiField(args.iconUrl, { edit { args -> args.copy(iconUrl = it) } }, Lang.settings_mediasource_selector_config_icon_url, enabled, "icon")
        field("headers", Lang.settings_api_headers, true)
    }
    ApiSection(stringResource(Lang.settings_api_search_rules), initiallyExpanded = true) {
        ApiRequestFields("search", args.search.request, state, enabled, onChange) { request -> edit { it.copy(search = it.search.copy(request = request)) } }
        ApiField(args.search.itemsPath, { edit { args -> args.copy(search = args.search.copy(itemsPath = it)) } }, Lang.settings_api_items_path, enabled, "items-path")
        ApiField(args.search.idPath, { edit { args -> args.copy(search = args.search.copy(idPath = it)) } }, Lang.settings_api_id_path, enabled)
        ApiField(args.search.namePath, { edit { args -> args.copy(search = args.search.copy(namePath = it)) } }, Lang.settings_api_name_path, enabled)
        ApiField(args.search.aliasesPath, { edit { args -> args.copy(search = args.search.copy(aliasesPath = it)) } }, Lang.settings_api_aliases_path, enabled)
        ApiField(args.search.subjectUrl, { edit { args -> args.copy(search = args.search.copy(subjectUrl = it)) } }, Lang.settings_api_subject_url, enabled)
        ApiSwitch(args.search.paginate, { edit { args -> args.copy(search = args.search.copy(paginate = it)) } }, Lang.settings_api_paginate, enabled)
        if (args.search.paginate) {
            field("firstPage", Lang.settings_api_first_page)
            field("pageSize", Lang.settings_api_page_size)
            field("maxPages", Lang.settings_api_max_pages)
        }
    }
    ApiSection(stringResource(Lang.settings_api_detail)) {
        ApiRequestFields("detail", args.detail.request, state, enabled, onChange) { request -> edit { it.copy(detail = it.detail.copy(request = request)) } }
        ApiField(args.detail.channelsPath, { edit { args -> args.copy(detail = args.detail.copy(channelsPath = it)) } }, Lang.settings_api_channels_path, enabled)
        ApiField(args.detail.channelIdPath, { edit { args -> args.copy(detail = args.detail.copy(channelIdPath = it)) } }, Lang.settings_api_channel_id, enabled)
        ApiField(args.detail.channelNamePath, { edit { args -> args.copy(detail = args.detail.copy(channelNamePath = it)) } }, Lang.settings_api_channel_name, enabled)
        ApiField(args.detail.episodesPath, { edit { args -> args.copy(detail = args.detail.copy(episodesPath = it)) } }, Lang.settings_api_episodes_path, enabled)
        ApiField(args.detail.episodeIdPath, { edit { args -> args.copy(detail = args.detail.copy(episodeIdPath = it)) } }, Lang.settings_api_id_path, enabled)
        ApiField(args.detail.episodeNamePath, { edit { args -> args.copy(detail = args.detail.copy(episodeNamePath = it)) } }, Lang.settings_api_name_path, enabled)
        ApiField(args.detail.episodeNumberPath, { edit { args -> args.copy(detail = args.detail.copy(episodeNumberPath = it)) } }, Lang.settings_api_episode_number, enabled)
        ApiField(args.detail.episodeKindPath, { edit { args -> args.copy(detail = args.detail.copy(episodeKindPath = it)) } }, Lang.settings_api_episode_kind, enabled)
        field("kindPrefixes", Lang.settings_api_kind_prefixes, true)
        ApiField(args.detail.episodeUrl, { edit { args -> args.copy(detail = args.detail.copy(episodeUrl = it)) } }, Lang.settings_api_episode_url, enabled)
    }
    ApiSection(stringResource(Lang.settings_api_playback)) {
        ApiRequestFields("playback", args.playback.request, state, enabled, onChange) { request -> edit { it.copy(playback = it.playback.copy(request = request)) } }
        ApiField(args.playback.urlPath, { edit { args -> args.copy(playback = args.playback.copy(urlPath = it)) } }, Lang.settings_api_url_path, enabled)
        ApiField(args.playback.successPath, { edit { args -> args.copy(playback = args.playback.copy(successPath = it)) } }, Lang.settings_api_success_path, enabled)
        ApiField(args.playback.candidatesPath, { edit { args -> args.copy(playback = args.playback.copy(candidatesPath = it)) } }, Lang.settings_api_candidates_path, enabled)
        ApiField(args.playback.candidateIdPath, { edit { args -> args.copy(playback = args.playback.copy(candidateIdPath = it)) } }, Lang.settings_api_candidates_id, enabled)
        ApiField(args.playback.candidateUrlPath, { edit { args -> args.copy(playback = args.playback.copy(candidateUrlPath = it)) } }, Lang.settings_api_candidates_url, enabled)
        field("videoHeaders", Lang.settings_api_video_headers, true)
    }
    ApiSection(stringResource(Lang.settings_api_matching)) {
        ApiSwitch(args.autoMatch, { edit { args -> args.copy(autoMatch = it) } }, Lang.settings_api_auto_match, enabled)
        ApiSwitch(args.filterBySubjectName, { edit { args -> args.copy(filterBySubjectName = it) } }, Lang.settings_api_filter_by_subject_name, enabled)
        field("searchNamesCount", Lang.settings_mediasource_selector_config_search_subject_names_count)
        ApiField(args.resolution, { edit { args -> args.copy(resolution = it) } }, Lang.settings_mediasource_selector_config_default_resolution, enabled)
        ApiField(args.subtitleLanguage, { edit { args -> args.copy(subtitleLanguage = it) } }, Lang.settings_mediasource_selector_config_default_subtitle_language, enabled)
        field("tier", Lang.settings_api_tier)
        field("channelTiers", Lang.settings_api_channel_tiers, true)
    }
}

@Composable
private fun ApiRequestFields(
    key: String, request: ApiRequest, state: ApiConfigurationFormState, enabled: Boolean,
    onChange: (String) -> Unit, onRequestChange: (ApiRequest) -> Unit,
) {
    ApiField(request.url, { onRequestChange(request.copy(url = it)) }, Lang.settings_api_request_url, enabled, "$key-url")
    SingleChoiceSegmentedButtonRow {
        listOf("GET", "POST").forEachIndexed { index, method ->
            SegmentedButton(request.method == method, { onRequestChange(request.copy(method = method)) },
                SegmentedButtonDefaults.itemShape(index, 2), enabled = enabled) { Text(method) }
        }
    }
    ApiField(state.fields.getValue("$key.headers"), { onChange(state.editField("$key.headers", it)) }, Lang.settings_api_headers, enabled, "$key.headers", true)
    if (request.method == "POST") {
        ApiField(state.fields.getValue("$key.body"), { onChange(state.editField("$key.body", it)) }, Lang.settings_api_body, enabled, "$key.body", true)
    }
}

@Composable
private fun ApiField(value: String, onChange: (String) -> Unit, label: StringResource, enabled: Boolean, tag: String = "", json: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().heightIn(max = if (json) 240.dp else 80.dp).testTag("api-$tag"),
        label = { Text(stringResource(label)) }, enabled = enabled, singleLine = !json,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (json) FontFamily.Monospace else FontFamily.Default),
        minLines = if (json) 2 else 1, shape = MaterialTheme.shapes.medium)
}

@Composable
private fun ApiSwitch(value: Boolean, onChange: (Boolean) -> Unit, label: StringResource, enabled: Boolean) {
    ListItem(headlineContent = { Text(stringResource(label)) }, trailingContent = { Switch(value, onChange, enabled = enabled) })
}

@Composable
private fun ApiSection(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.animation.StandardEasing
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_auto_save_hint
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_base_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_base_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_channel_format_grouped
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_channel_format_no_channel
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_channel_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_channel_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_subject_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_subject_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_episode_sort
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_episode_sort_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_subject_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_subject_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_settings
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_icon_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_player_select_resource
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_referer_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_request_interval
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_request_interval_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_first_word
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_first_word_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_remove_special
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_remove_special_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_subject_format_multi_tag
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_subject_format_single_tag
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_user_agent_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_video_playback
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.app.ui.settings.mediasource.rss.edit.MediaSourceHeadline
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SelectorConfigurationPane(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = SelectorConfigurationDefaults.verticalSpacing,
    textFieldShape: Shape = SelectorConfigurationDefaults.textFieldShape,
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
            val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

            Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                OutlinedTextField(
                    state.displayName, { state.displayName = it },
                    Modifier
                        .fillMaxWidth()
                        .moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_name)) },
                    placeholder = { Text(stringResource(Lang.settings_mediasource_selector_config_name_placeholder)) },
                    isError = state.displayNameIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    state.iconUrl, { state.iconUrl = it },
                    Modifier
                        .fillMaxWidth()
                        .moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_icon_url)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_1)
                }
            }

            Column {
                OutlinedTextField(
                    state.searchUrl, { state.searchUrl = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_search_url)) },
                    placeholder = {
                        Text(
                            stringResource(Lang.settings_mediasource_selector_config_search_url_placeholder),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    supportingText = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_search_url_supporting))
                    },
                    isError = state.searchUrlIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    state.rawBaseUrl, { state.rawBaseUrl = it },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_base_url)) },
                    placeholder = state.baseUrlPlaceholder?.let {
                        {
                            Text(it, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    supportingText = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_base_url_supporting))
                    },
                    isError = state.searchUrlIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_search_first_word)) },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .clickable(enabled = state.enableEdit) {
                            state.searchUseOnlyFirstWord = !state.searchUseOnlyFirstWord
                        },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_search_first_word_description))
                    },
                    trailingContent = {
                        Switch(
                            state.searchUseOnlyFirstWord, { state.searchUseOnlyFirstWord = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_search_remove_special)) },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .clickable(enabled = state.enableEdit) {
                            state.searchRemoveSpecial = !state.searchRemoveSpecial
                        },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_search_remove_special_description))
                    },
                    trailingContent = {
                        Switch(
                            state.searchRemoveSpecial, { state.searchRemoveSpecial = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )

                var searchUseSubjectNamesCount by remember(state.searchUseSubjectNamesCount) {
                    mutableStateOf(state.searchUseSubjectNamesCount.toString())
                }
                OutlinedTextField(
                    searchUseSubjectNamesCount,
                    {
                        searchUseSubjectNamesCount = it
                        state.searchUseSubjectNamesCount = it.toIntOrNull() ?: state.searchUseSubjectNamesCount
                    },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_search_subject_names_count)) },
                    supportingText = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_search_subject_names_count_description))
                    },
                    isError = searchUseSubjectNamesCount.toIntOrNull().let {
                        it == null || it < 1
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                var requestIntervalString by remember(state.requestInterval) {
                    mutableStateOf(state.requestInterval.inWholeMilliseconds.toString())
                }
                OutlinedTextField(
                    requestIntervalString,
                    {
                        requestIntervalString = it
                        state.requestInterval = it.toLongOrNull()?.milliseconds ?: state.requestInterval
                    },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_config_request_interval)) },
                    supportingText = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_request_interval_description))
                    },
                    isError = requestIntervalString.toLongOrNull() == null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            SelectorSubjectFormatSelectionButtonRow(
                state,
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                enabled = state.enableEdit,
            )

            AnimatedContent(
                SelectorSubjectFormat.findById(state.subjectFormatId),
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
            ) { format ->
                SelectorSubjectConfigurationColumn(
                    format, state,
                    textFieldShape, verticalSpacing, listItemColors,
                    Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_2)
                }
            }

            SelectorChannelSelectionButtonRow(
                state,
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                enabled = state.enableEdit,
            )

            AnimatedContent(
                state.channelFormatId,
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
            ) { formatId ->
                SelectorChannelFormatColumn(formatId, state, Modifier.fillMaxWidth())
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(stringResource(Lang.settings_mediasource_selector_config_filter_settings))
                }
            }

            Column(
                Modifier,
                verticalArrangement = Arrangement.spacedBy((verticalSpacing - 16.dp).coerceAtLeast(0.dp)),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_filter_by_subject_name)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { state.filterBySubjectName = !state.filterBySubjectName },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_filter_by_subject_name_description))
                    },
                    trailingContent = {
                        Switch(
                            state.filterBySubjectName, { state.filterBySubjectName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_filter_by_episode_sort)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { state.filterByEpisodeSort = !state.filterByEpisodeSort },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_filter_by_episode_sort_description))
                    },
                    trailingContent = {
                        Switch(
                            state.filterByEpisodeSort, { state.filterByEpisodeSort = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_3)
                }
            }

            SelectorConfigurationDefaults.MatchVideoSection(
                state,
                textFieldShape = textFieldShape,
                verticalSpacing = verticalSpacing,
            )

            kotlin.run {
                var showMenu by rememberSaveable { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_default_resolution)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { showMenu = !showMenu },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_default_resolution_description))
                    },
                    trailingContent = {
                        TextButton(onClick = { showMenu = true }) {
                            Text(state.defaultResolution.displayName)
                        }
                        if (showMenu) {
                            DropdownMenu(showMenu, { showMenu = false }) {
                                for (resolution in Resolution.entries.asReversed()) {
                                    DropdownMenuItem(
                                        text = { Text(resolution.displayName) },
                                        onClick = {
                                            state.defaultResolution = resolution
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                )
            }

            kotlin.run {
                var showMenu by rememberSaveable { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_default_subtitle_language)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { showMenu = !showMenu },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_default_subtitle_language_description))
                    },
                    trailingContent = {
                        TextButton(onClick = { showMenu = true }) {
                            Text(state.defaultSubtitleLanguage.displayName)
                        }
                        if (showMenu) {
                            DropdownMenu(showMenu, { showMenu = false }) {
                                for (language in SubtitleLanguage.matchableEntries.asReversed()) {
                                    DropdownMenuItem(
                                        text = { Text(language.displayName) },
                                        onClick = {
                                            state.defaultSubtitleLanguage = language
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(stringResource(Lang.settings_mediasource_selector_config_player_select_resource))
                }
            }

            Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                val conf = state.selectMediaConfig
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_distinguish_subject_name)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { conf.distinguishSubjectName = !conf.distinguishSubjectName },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_distinguish_subject_name_description))
                    },
                    trailingContent = {
                        Switch(
                            conf.distinguishSubjectName, { conf.distinguishSubjectName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_selector_config_distinguish_channel_name)) },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { conf.distinguishChannelName = !conf.distinguishChannelName },
                    supportingContent = {
                        Text(stringResource(Lang.settings_mediasource_selector_config_distinguish_channel_name_description))
                    },
                    trailingContent = {
                        Switch(
                            conf.distinguishChannelName, { conf.distinguishChannelName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(stringResource(Lang.settings_mediasource_selector_config_video_playback))
                }
            }

            Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                val conf = state.matchVideoConfig.videoHeaders
                OutlinedTextField(
                    conf.referer, { conf.referer = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("Referer") },
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_config_referer_description)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.userAgent, { conf.userAgent = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("User-Agent") },
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_config_user_agent_description)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            Row(Modifier.align(Alignment.End).padding(top = verticalSpacing, bottom = 12.dp)) {
                if (state.enableEdit) {
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
}


@Composable
private fun SelectorSubjectFormatSelectionButtonRow(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        @Composable
        fun Btn(
            id: SelectorFormatId, index: Int,
            label: @Composable () -> Unit,
        ) {
            SegmentedButton(
                state.subjectFormatId == id,
                { state.subjectFormatId = id },
                SegmentedButtonDefaults.itemShape(index, state.allSubjectFormats.size),
                icon = { SegmentedButtonDefaults.Icon(state.subjectFormatId == id) },
                label = label,
                enabled = enabled,
            )
        }

        for ((index, format) in state.allSubjectFormats.withIndex()) {
            Btn(format.id, index) {
                Text(
                    when (format) { // type-safe to handle all formats
                        SelectorSubjectFormatA -> stringResource(Lang.settings_mediasource_selector_config_subject_format_single_tag)
                        SelectorSubjectFormatIndexed -> stringResource(Lang.settings_mediasource_selector_config_subject_format_multi_tag)
                        SelectorSubjectFormatJsonPathIndexed -> "JsonPath"
                    },
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun SelectorChannelSelectionButtonRow(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        @Composable
        fun Btn(
            id: SelectorFormatId, index: Int,
            label: @Composable () -> Unit,
        ) {
            SegmentedButton(
                state.channelFormatId == id,
                { state.channelFormatId = id },
                SegmentedButtonDefaults.itemShape(index, state.allChannelFormats.size),
                icon = { SegmentedButtonDefaults.Icon(state.channelFormatId == id) },
                label = label,
                enabled = enabled,
            )
        }

        for ((index, selectorChannelFormat) in state.allChannelFormats.withIndex()) {
            Btn(selectorChannelFormat.id, index) {
                Text(
                    when (selectorChannelFormat) { // type-safe to handle all formats
                        SelectorChannelFormatNoChannel -> stringResource(Lang.settings_mediasource_selector_config_channel_format_no_channel)
                        SelectorChannelFormatIndexGrouped -> stringResource(Lang.settings_mediasource_selector_config_channel_format_grouped)
                    },
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewSelectorConfigurationPane() = ProvideCompositionLocalsForPreview {
    Surface {
        SelectorConfigurationPane(
            remember {
                SelectorConfigState(
                    createTestSaveableStorage(
                        SelectorMediaSourceArguments.Default,
                    ),
                    allowEditState = stateOf(true),
                )
            },
        )
    }
}

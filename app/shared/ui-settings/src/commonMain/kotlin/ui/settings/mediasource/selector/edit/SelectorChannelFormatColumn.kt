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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_channel_format_index_grouped_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_channel_names_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_channel_names_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_links_from_list_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_links_from_list_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_links_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_links_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_lists_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_lists_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episodes_from_list_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episodes_from_list_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episodes_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episodes_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_channel_name_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_channel_name_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_episode_sort_from_name_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_episode_sort_from_name_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_unsupported_format
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SelectorChannelFormatColumn(
    formatId: SelectorFormatId,
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    textFieldShape: Shape = MediaSourceConfigurationDefaults.outlinedTextFieldShape,
) {
    Column(modifier) {
        when (SelectorChannelFormat.findById(formatId)) {
            SelectorChannelFormatIndexGrouped -> Column(
                verticalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding),
            ) {
                Text(
                    stringResource(Lang.settings_mediasource_selector_channel_format_index_grouped_description),
                    Modifier,
                    style = MaterialTheme.typography.labelLarge,
                )

                val conf = state.channelFormatIndexed
                OutlinedTextField(
                    conf.selectChannelNames, { conf.selectChannelNames = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_channel_names_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_channel_names_supporting)) },
                    isError = conf.selectChannelNamesIsError,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.matchChannelName, { conf.matchChannelName = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_match_channel_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_match_channel_name_supporting)) },
                    isError = conf.matchChannelNameIsError,
                    enabled = state.enableEdit,
                )

                OutlinedTextField(
                    conf.selectEpisodeLists, { conf.selectEpisodeLists = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter().padding(top = 8.dp),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_episode_lists_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_episode_lists_supporting)) },
                    isError = conf.selectEpisodeListsIsError,
                    enabled = state.enableEdit,
                )

                OutlinedTextField(
                    conf.selectEpisodesFromList, { conf.selectEpisodesFromList = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter().padding(top = 8.dp),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_episodes_from_list_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_episodes_from_list_supporting)) },
                    isError = conf.selectEpisodesFromListIsError,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.selectEpisodeLinksFromList, { conf.selectEpisodeLinksFromList = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter().padding(top = 8.dp),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_episode_links_from_list_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_episode_links_from_list_supporting)) },
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.matchEpisodeSortFromName, { conf.matchEpisodeSortFromName = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_supporting)) },
                    isError = conf.matchEpisodeSortFromNameIsError,
                    enabled = state.enableEdit,
                )
            }

            SelectorChannelFormatNoChannel -> Column(
                verticalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding),
            ) {
                val conf = state.channelFormatNoChannel
                OutlinedTextField(
                    conf.selectEpisodes, { conf.selectEpisodes = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_episodes_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_episodes_supporting)) },
                    isError = conf.selectEpisodesIsError,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.selectEpisodeLinks, { conf.selectEpisodeLinks = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_episode_links_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_episode_links_supporting)) },
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.matchEpisodeSortFromName, { conf.matchEpisodeSortFromName = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text(stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    supportingText = { Text(stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_supporting)) },
                    isError = conf.matchEpisodeSortFromNameIsError,
                    enabled = state.enableEdit,
                )
            }

            null -> {
                UnsupportedFormatIdHint(formatId, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
internal fun rememberTestSelectorConfigurationState(
    arguments: SelectorMediaSourceArguments = SelectorMediaSourceArguments.Default
): SelectorConfigState {
    return remember {
        SelectorConfigState(
            createTestSaveableStorage(
                arguments,
            ),
            allowEditState = stateOf(true),
        )
    }
}

@Preview
@Composable
private fun PreviewSelectorChannelFormatColumnNoChannel() {
    ProvideCompositionLocalsForPreview {
        SelectorChannelFormatColumn(
            SelectorChannelFormatNoChannel.id,
            rememberTestSelectorConfigurationState(),
        )
    }
}

@Preview
@Composable
private fun PreviewSelectorChannelFormatColumnIndexGrouped() {
    ProvideCompositionLocalsForPreview {
        SelectorChannelFormatColumn(
            SelectorChannelFormatIndexGrouped.id,
            rememberTestSelectorConfigurationState(),
        )
    }
}

@Preview
@Composable
private fun PreviewSelectorChannelFormatColumnUnsupported() {
    ProvideCompositionLocalsForPreview {
        SelectorChannelFormatColumn(
            SelectorFormatId("test"),
            rememberTestSelectorConfigurationState(),
        )
    }
}

@Composable
internal fun UnsupportedFormatIdHint(formatId: SelectorFormatId, modifier: Modifier = Modifier) {
    Column(modifier) {
        ProvideTextStyleContentColor(MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.error) {
            Icon(
                Icons.Rounded.Error, null,
                Modifier.align(Alignment.CenterHorizontally).size(48.dp),
            )
            Text(
                stringResource(Lang.settings_mediasource_selector_unsupported_format, formatId.value),
                Modifier.padding(top = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

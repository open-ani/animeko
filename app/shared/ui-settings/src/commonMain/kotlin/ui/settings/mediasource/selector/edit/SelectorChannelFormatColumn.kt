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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.stateOf
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
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.UnsupportedFormatIdHint
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
                ConfigTextField(
                    conf.selectChannelNames, { conf.selectChannelNames = it },
                    label = stringResource(Lang.settings_mediasource_selector_channel_names_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_channel_names_supporting),
                    isError = conf.selectChannelNamesIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.matchChannelName, { conf.matchChannelName = it },
                    label = stringResource(Lang.settings_mediasource_selector_match_channel_name_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_match_channel_name_supporting),
                    isError = conf.matchChannelNameIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )

                ConfigTextField(
                    conf.selectEpisodeLists, { conf.selectEpisodeLists = it },
                    label = stringResource(Lang.settings_mediasource_selector_episode_lists_label),
                    Modifier.padding(top = 8.dp),
                    supportingText = stringResource(Lang.settings_mediasource_selector_episode_lists_supporting),
                    isError = conf.selectEpisodeListsIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )

                ConfigTextField(
                    conf.selectEpisodesFromList, { conf.selectEpisodesFromList = it },
                    label = stringResource(Lang.settings_mediasource_selector_episodes_from_list_label),
                    Modifier.padding(top = 8.dp),
                    supportingText = stringResource(Lang.settings_mediasource_selector_episodes_from_list_supporting),
                    isError = conf.selectEpisodesFromListIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.selectEpisodeLinksFromList, { conf.selectEpisodeLinksFromList = it },
                    label = stringResource(Lang.settings_mediasource_selector_episode_links_from_list_label),
                    Modifier.padding(top = 8.dp),
                    supportingText = stringResource(Lang.settings_mediasource_selector_episode_links_from_list_supporting),
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.matchEpisodeSortFromName, { conf.matchEpisodeSortFromName = it },
                    label = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_supporting),
                    isError = conf.matchEpisodeSortFromNameIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
            }

            SelectorChannelFormatNoChannel -> Column(
                verticalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding),
            ) {
                val conf = state.channelFormatNoChannel
                ConfigTextField(
                    conf.selectEpisodes, { conf.selectEpisodes = it },
                    label = stringResource(Lang.settings_mediasource_selector_episodes_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_episodes_supporting),
                    isError = conf.selectEpisodesIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.selectEpisodeLinks, { conf.selectEpisodeLinks = it },
                    label = stringResource(Lang.settings_mediasource_selector_episode_links_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_episode_links_supporting),
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    conf.matchEpisodeSortFromName, { conf.matchEpisodeSortFromName = it },
                    label = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_label),
                    supportingText = stringResource(Lang.settings_mediasource_selector_match_episode_sort_from_name_supporting),
                    isError = conf.matchEpisodeSortFromNameIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
            }

            null -> {
                UnsupportedFormatIdHint(formatId.value, Modifier.align(Alignment.CenterHorizontally))
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

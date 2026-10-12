/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_auto_save_hint
import me.him188.ani.app.ui.lang.settings_mediasource_rss_filter_by_episode
import me.him188.ani.app.ui.lang.settings_mediasource_rss_filter_by_episode_description
import me.him188.ani.app.ui.lang.settings_mediasource_rss_icon_link
import me.him188.ani.app.ui.lang.settings_mediasource_rss_name
import me.him188.ani.app.ui.lang.settings_mediasource_rss_name_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_rss_query_settings
import me.him188.ani.app.ui.lang.settings_mediasource_rss_search_link
import me.him188.ani.app.ui.lang.settings_mediasource_rss_search_link_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_rss_search_link_supporting_text
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RssEditPane(
    state: EditRssMediaSourceState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    outlinedTextFieldShape: Shape = MediaSourceConfigurationDefaults.outlinedTextFieldShape
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(contentPadding)) {
            // 大图标和标题
            MediaSourceHeadline(state.displayIconUrl, state.displayName)

            Column(
                Modifier.focusGroup()
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
            ) {
                val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedTextField(
                        state.displayName, { state.displayName = it },
                        Modifier
                            .fillMaxWidth(),
                        label = { Text(stringResource(Lang.settings_mediasource_rss_name)) },
                        placeholder = { Text(stringResource(Lang.settings_mediasource_rss_name_placeholder)) },
                        isError = state.displayNameIsError,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = outlinedTextFieldShape,
                        enabled = state.enableEdit,
                    )
                    OutlinedTextField(
                        state.iconUrl, { state.iconUrl = it.trim() },
                        Modifier
                            .fillMaxWidth(),
                        label = { Text(stringResource(Lang.settings_mediasource_rss_icon_link)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = outlinedTextFieldShape,
                        enabled = state.enableEdit,
                    )
                }

                Row(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.titleMedium,
                        MaterialTheme.colorScheme.primary,
                    ) {
                        Text(stringResource(Lang.settings_mediasource_rss_query_settings))
                    }
                }

                Column(Modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedTextField(
                        state.searchUrl, { state.searchUrl = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Lang.settings_mediasource_rss_search_link)) },
                        placeholder = {
                            Text(
                                stringResource(Lang.settings_mediasource_rss_search_link_placeholder),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        },
                        supportingText = {
                            Text(
                                stringResource(Lang.settings_mediasource_rss_search_link_supporting_text),
                            )
                        },
                        isError = state.searchUrlIsError,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = outlinedTextFieldShape,
                        enabled = state.enableEdit,
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_filter_by_episode)) },
                        Modifier.clickable(
                            enabled = state.enableEdit,
                        ) { state.filterByEpisodeSort = !state.filterByEpisodeSort },
                        supportingContent = { Text(stringResource(Lang.settings_mediasource_rss_filter_by_episode_description)) },
                        trailingContent = {
                            Switch(
                                state.filterByEpisodeSort, { state.filterByEpisodeSort = it },
                                enabled = state.enableEdit,
                            )
                        },
                        colors = listItemColors,
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
}

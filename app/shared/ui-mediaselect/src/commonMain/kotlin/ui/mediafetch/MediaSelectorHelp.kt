/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.RichDialogLayout
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_details_source_online
import me.him188.ani.app.ui.lang.media_selector_help_bt_description
import me.him188.ani.app.ui.lang.media_selector_help_source_types
import me.him188.ani.app.ui.lang.media_selector_help_title
import me.him188.ani.app.ui.lang.media_selector_help_web_description
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import org.jetbrains.compose.resources.stringResource


@Composable
fun MediaSelectorHelp(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = stringResource(Lang.media_selector_help_title)
    val closeText = stringResource(Lang.subject_episode_close)
    val sourceTypesText = stringResource(Lang.media_selector_help_source_types)
    val btDescriptionText = stringResource(Lang.media_selector_help_bt_description)
    val onlineText = stringResource(Lang.cache_details_source_online)
    val webDescriptionText = stringResource(Lang.media_selector_help_web_description)
    RichDialogLayout(
        title = { Text(titleText) },
        buttons = {
            TextButton(onDismissRequest) {
                Text(closeText)
            }
        },
        modifier,
    ) {
        Text(sourceTypesText, style = MaterialTheme.typography.titleMedium)

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplainerCard(
                title = { Text("BT") },
                Modifier.weight(1f),
                icon = {
                    Icon(MediaSourceIcons.KindBT, null)
                },
            ) {
                Text(btDescriptionText)
            }
            ExplainerCard(
                title = { Text(onlineText) },
                Modifier.weight(1f),
                icon = {
                    Icon(MediaSourceIcons.KindWeb, null)
                },
            ) {
                Text(webDescriptionText)
            }
        }
    }
}

@Composable
fun ExplainerCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon?.let {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        it()
                    }
                }
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    title()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                content()
            }
        }
    }
}


@PreviewLightDark
@Composable
private fun PreviewMediaSelectorHelp() {
    ProvideCompositionLocalsForPreview {
        MediaSelectorHelp({})
    }
}

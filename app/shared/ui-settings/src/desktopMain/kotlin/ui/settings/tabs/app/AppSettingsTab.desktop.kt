/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.parseMpvOptions
import me.him188.ani.app.data.models.preference.splitMpvOptionLines
import me.him188.ani.app.ui.foundation.effects.defaultFocus
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_player_mpv_options
import me.him188.ani.app.ui.lang.settings_player_mpv_options_count
import me.him188.ani.app.ui.lang.settings_player_mpv_options_description
import me.him188.ani.app.ui.lang.settings_player_mpv_options_empty
import me.him188.ani.app.ui.lang.settings_player_mpv_options_placeholder
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldDialog
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun SettingsScope.PlayerGroupPlatform(
    @Suppress("UNUSED_PARAMETER") videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    playerKernelConfig: SettingsState<PlayerKernelConfig>,
) {
    HorizontalDividerItem()
    MpvOptionsItem(playerKernelConfig)
}

internal object MpvOptionsItemTestTags {
    const val ITEM = "mpv_options_item"
    const val TEXT_FIELD = "mpv_options_text_field"
}

/**
 * 自定义 mpv 选项. 桌面端播放器内核是 mpv, 但不会读取用户的 `mpv.conf`, 所以在这里提供一个输入框.
 */
@Composable
private fun SettingsScope.MpvOptionsItem(playerKernelConfig: SettingsState<PlayerKernelConfig>) {
    val config by playerKernelConfig
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val optionCount = remember(config.mpvOptions) { parseMpvOptions(config.mpvOptions).size }

    Box {
        Item(
            headlineContent = { Text(stringResource(Lang.settings_player_mpv_options)) },
            Modifier.testTag(MpvOptionsItemTestTags.ITEM).clickable { showDialog = true },
            supportingContent = {
                Text(
                    if (optionCount == 0) {
                        stringResource(Lang.settings_player_mpv_options_empty)
                    } else {
                        stringResource(Lang.settings_player_mpv_options_count, optionCount)
                    },
                )
            },
            trailingContent = {
                IconButton({ showDialog = true }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        if (showDialog) {
            val savedText = remember(config.mpvOptions) { config.mpvOptions.joinToString("\n") }
            var editingValue by rememberSaveable(savedText) { mutableStateOf(savedText) }
            TextFieldDialog(
                onDismissRequest = { showDialog = false },
                onConfirm = {
                    playerKernelConfig.update(config.copy(mpvOptions = splitMpvOptionLines(editingValue)))
                    showDialog = false
                },
                title = { Text(stringResource(Lang.settings_player_mpv_options)) },
                description = { Text(stringResource(Lang.settings_player_mpv_options_description)) },
            ) {
                // 多行输入, 超过 5 行后在输入框内滚动. 回车用于换行, 因此不绑定 Enter 确认.
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier.fillMaxWidth()
                        .testTag(MpvOptionsItemTestTags.TEXT_FIELD)
                        .defaultFocus(),
                    placeholder = { Text(stringResource(Lang.settings_player_mpv_options_placeholder)) },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 3,
                    maxLines = 5,
                )
            }
        }
    }
}

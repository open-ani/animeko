/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_delete_selected
import me.him188.ani.app.ui.lang.settings_media_source_delete_selected_confirmation
import me.him188.ani.app.ui.lang.settings_media_source_delete_selected_title
import me.him188.ani.app.ui.lang.settings_media_source_disable_selected
import me.him188.ani.app.ui.lang.settings_media_source_enable_selected
import org.jetbrains.compose.resources.stringResource

internal object MediaSourceSelectionToolbarTestTags {
    const val TOOLBAR = "media_source_selection_toolbar"
    const val ENABLE = "media_source_selection_toolbar_enable"
    const val DISABLE = "media_source_selection_toolbar_disable"
    const val DELETE = "media_source_selection_toolbar_delete"
    const val DELETE_CONFIRM = "media_source_selection_delete_confirm"
}

/**
 * 多选模式下的浮动批量操作工具栏: 启用 / 禁用 / 删除.
 */
@Composable
internal fun MediaSourceSelectionActions(
    mediaSources: List<MediaSourcePresentation>,
    selectionState: MediaSourceSelectionState,
    editState: EditMediaSourceState,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val selectedMediaSources = remember(mediaSources, selectionState.selectedIds) {
        mediaSources.filter { it.instanceId in selectionState.selectedIds }
    }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_media_source_delete_selected_title)) },
            text = {
                Text(
                    stringResource(
                        Lang.settings_media_source_delete_selected_confirmation,
                        selectedMediaSources.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editState.deleteMediaSources(selectedMediaSources)
                        selectionState.clear()
                        showDeleteConfirmation = false
                    },
                    modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.DELETE_CONFIRM),
                ) {
                    Text(
                        stringResource(Lang.settings_media_source_delete_selected),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(Lang.settings_media_source_cancel))
                }
            },
        )
    }

    Box(
        modifier
            .testTag(MediaSourceSelectionToolbarTestTags.TOOLBAR)
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        editState.setMediaSourcesEnabled(
                            selectedMediaSources.filterNot { it.isEnabled },
                            enabled = true,
                        )
                    },
                    enabled = selectedMediaSources.any { !it.isEnabled },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.ENABLE),
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        stringResource(Lang.settings_media_source_enable_selected),
                    )
                }
                IconButton(
                    onClick = {
                        editState.setMediaSourcesEnabled(
                            selectedMediaSources.filter { it.isEnabled },
                            enabled = false,
                        )
                    },
                    enabled = selectedMediaSources.any { it.isEnabled },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.DISABLE),
                ) {
                    Icon(
                        Icons.Rounded.VisibilityOff,
                        stringResource(Lang.settings_media_source_disable_selected),
                    )
                }
                VerticalDivider(
                    Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = selectedMediaSources.isNotEmpty(),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.DELETE),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        stringResource(Lang.settings_media_source_delete_selected),
                    )
                }
            }
        }
    }
}

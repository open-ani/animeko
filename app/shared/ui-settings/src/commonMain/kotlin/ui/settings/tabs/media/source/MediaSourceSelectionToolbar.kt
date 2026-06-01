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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.widgets.dismissDialogButton
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
 * 批量操作的可用性与执行, 与外观无关 —— 指针设备是底部那条浮动工具栏
 * ([MediaSourceSelectionActions]), 遥控器是长按选中项弹出的下拉菜单
 * (MediaSourceGroup 里的 `BulkOptionsDropdown`), 两处共用本类, 判据不再各写一遍.
 */
@Stable
internal class MediaSourceBulkActions(
    val selected: List<MediaSourcePresentation>,
    private val selectionState: MediaSourceSelectionState,
    private val editState: EditMediaSourceState,
) {
    val canEnable: Boolean get() = selected.any { !it.isEnabled }
    val canDisable: Boolean get() = selected.any { it.isEnabled }
    val canDelete: Boolean get() = selected.isNotEmpty()

    fun enable() = editState.setMediaSourcesEnabled(selected.filterNot { it.isEnabled }, enabled = true)

    fun disable() = editState.setMediaSourcesEnabled(selected.filter { it.isEnabled }, enabled = false)

    /** 删除并退出多选 (选中的东西没了, 留在多选态没有意义). */
    fun delete() {
        editState.deleteMediaSources(selected)
        selectionState.clear()
    }
}

@Composable
internal fun rememberMediaSourceBulkActions(
    mediaSources: List<MediaSourcePresentation>,
    selectionState: MediaSourceSelectionState,
    editState: EditMediaSourceState,
): MediaSourceBulkActions {
    val selected = remember(mediaSources, selectionState.selectedIds) {
        mediaSources.filter { it.instanceId in selectionState.selectedIds }
    }
    return remember(selected, selectionState, editState) {
        MediaSourceBulkActions(selected, selectionState, editState)
    }
}

/** 批量删除的二次确认 (工具栏与下拉菜单共用). */
@Composable
internal fun MediaSourceBulkDeleteConfirmation(
    count: Int,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.settings_media_source_delete_selected_title)) },
        text = { Text(stringResource(Lang.settings_media_source_delete_selected_confirmation, count)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.DELETE_CONFIRM),
            ) {
                Text(
                    stringResource(Lang.settings_media_source_delete_selected),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        // 遥控器上不渲染: 返回键就是取消
        dismissButton = dismissDialogButton(stringResource(Lang.settings_media_source_cancel), onDismissRequest),
    )
}

/**
 * 多选模式下的浮动批量操作工具栏: 启用 / 禁用 / 删除.
 *
 * 只给指针设备用. 遥控器上够到屏幕底部这条浮窗要穿过整页设置项, 改为长按选中项出下拉菜单
 * (见 MediaSourceGroup), 调用方按 `focusDrivenNavigation` 决定渲不渲染.
 */
@Composable
internal fun MediaSourceSelectionActions(
    mediaSources: List<MediaSourcePresentation>,
    selectionState: MediaSourceSelectionState,
    editState: EditMediaSourceState,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val actions = rememberMediaSourceBulkActions(mediaSources, selectionState, editState)
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        MediaSourceBulkDeleteConfirmation(
            count = actions.selected.size,
            onConfirm = {
                actions.delete()
                showDeleteConfirmation = false
            },
            onDismissRequest = { showDeleteConfirmation = false },
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
                    onClick = { actions.enable() },
                    enabled = actions.canEnable,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(MediaSourceSelectionToolbarTestTags.ENABLE),
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        stringResource(Lang.settings_media_source_enable_selected),
                    )
                }
                IconButton(
                    onClick = { actions.disable() },
                    enabled = actions.canDisable,
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
                    enabled = actions.canDelete,
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

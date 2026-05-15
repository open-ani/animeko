/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch.request

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.saveable.mutableStateSaver
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.mediafetch_request_editor_continue_editing
import me.him188.ani.app.ui.lang.mediafetch_request_editor_discard
import me.him188.ani.app.ui.lang.mediafetch_request_editor_discard_confirmation
import me.him188.ani.app.ui.lang.mediafetch_request_editor_invalid_request
import me.him188.ani.app.ui.lang.mediafetch_request_editor_save_and_refresh
import me.him188.ani.app.ui.lang.mediafetch_request_editor_title
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.datasources.api.source.MediaFetchRequest
import org.jetbrains.compose.resources.stringResource

/**
 * @see MediaFetchRequestEditor
 */
@Composable
fun MediaFetchRequestEditorDialog(
    fetchRequest: MediaFetchRequest,
    onDismissRequest: () -> Unit,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
) {
    var editingRequest by rememberSaveable(
        fetchRequest,
        saver = mutableStateSaver(EditingMediaFetchRequest.Saver),
    ) {
        mutableStateOf(fetchRequest.toEditingMediaFetchRequest())
    }
    var showConfirmDiscard by rememberSaveable { mutableStateOf(false) }
    val onDismissRequestWrapped = {
        val hasChange = editingRequest != fetchRequest.toEditingMediaFetchRequest()
        if (hasChange) {
            showConfirmDiscard = true
        } else {
            onDismissRequest()
        }
    }

    val toaster = LocalToaster.current
    val invalidRequestText = stringResource(Lang.mediafetch_request_editor_invalid_request)
    val saveAndRefreshText = stringResource(Lang.mediafetch_request_editor_save_and_refresh)
    val cancelText = stringResource(Lang.settings_danmaku_cancel)
    val editRequestTitle = stringResource(Lang.mediafetch_request_editor_title)
    val discardText = stringResource(Lang.mediafetch_request_editor_discard)
    val continueEditingText = stringResource(Lang.mediafetch_request_editor_continue_editing)
    val discardConfirmationText = stringResource(Lang.mediafetch_request_editor_discard_confirmation)

    AlertDialog(
        onDismissRequestWrapped,
        confirmButton = {
            TextButton(
                {
                    editingRequest.toMediaFetchRequestOrNull()?.let {
                        onDismissRequestWrapped()
                        onFetchRequestChange(it)
                    } ?: toaster.toast(invalidRequestText)
                },
                enabled = editingRequest.toMediaFetchRequestOrNull() != null,
            ) {
                Text(saveAndRefreshText)
            }
        },
        dismissButton = {
            TextButton(onDismissRequestWrapped) {
                Text(cancelText)
            }
        },
        title = {
            Text(editRequestTitle)
        },
        text = {
            MediaFetchRequestEditor(
                editingRequest,
                { editingRequest = it },
                Modifier.fillMaxWidth(),
            )
        },
    )

    if (showConfirmDiscard) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDiscard = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDiscard = false
                        onDismissRequest()
                    },
                ) {
                    Text(discardText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDiscard = false
                    },
                ) {
                    Text(continueEditingText)
                }
            },
            icon = {
                Icon(
                    Icons.Rounded.Delete, null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(discardConfirmationText)
            },
        )
    }
}

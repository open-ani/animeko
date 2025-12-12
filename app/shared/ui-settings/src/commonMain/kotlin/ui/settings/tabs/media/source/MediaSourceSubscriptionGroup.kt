/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_delete_confirm
import me.him188.ani.app.ui.lang.settings_media_source_subscription
import me.him188.ani.app.ui.lang.settings_media_source_subscription_add
import me.him188.ani.app.ui.lang.settings_media_source_subscription_add_confirm
import me.him188.ani.app.ui.lang.settings_media_source_subscription_add_dialog
import me.him188.ani.app.ui.lang.settings_media_source_subscription_cancel
import me.him188.ani.app.ui.lang.settings_media_source_subscription_copied
import me.him188.ani.app.ui.lang.settings_media_source_subscription_copy_link
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete_description
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete_dialog
import me.him188.ani.app.ui.lang.settings_media_source_subscription_description
import me.him188.ani.app.ui.lang.settings_media_source_subscription_export_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_network_error
import me.him188.ani.app.ui.lang.settings_media_source_subscription_not_updated
import me.him188.ani.app.ui.lang.settings_media_source_subscription_paste
import me.him188.ani.app.ui.lang.settings_media_source_subscription_refresh_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_service_unavailable
import me.him188.ani.app.ui.lang.settings_media_source_subscription_unauthorized
import me.him188.ani.app.ui.lang.settings_media_source_subscription_unknown_error
import me.him188.ani.app.ui.lang.settings_media_source_subscription_update_failed
import me.him188.ani.app.ui.lang.settings_media_source_subscription_update_success
import me.him188.ani.app.ui.lang.settings_media_source_subscription_url
import me.him188.ani.app.ui.lang.settings_mediasource_clipboard_empty
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.Uuid
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.jvm.JvmName

@Stable
class MediaSourceSubscriptionGroupState(
    subscriptionsState: State<List<MediaSourceSubscription>>,
    private val onUpdateAll: suspend () -> Unit,
    private val onAdd: suspend (MediaSourceSubscription) -> Unit,
    private val onDelete: (MediaSourceSubscription) -> Unit,
    private val onExportLocalChangesToString: suspend (MediaSourceSubscription) -> String,
    backgroundScope: CoroutineScope,
) {
    val subscriptions by subscriptionsState

    private val updateAllTasker = MonoTasker(backgroundScope)
    val isUpdateAllInProgress get() = updateAllTasker.isRunning
    fun updateAll() {
        updateAllTasker.launch {
            onUpdateAll()
        }
    }


    var editingUrl by mutableStateOf("")
        private set

    @JvmName("setEditingUrl1")
    fun setEditingUrl(url: String) {
        if (isAddInProgress.value) {
            return
        }
        editingUrl = url
    }

    val editingUrlIsError by derivedStateOf { editingUrl.isEmpty() }

    private val addTasker = MonoTasker(backgroundScope)
    val isAddInProgress get() = addTasker.isRunning
    fun addNew(string: String) {
        addTasker.launch {
            onAdd(
                MediaSourceSubscription(
                    subscriptionId = Uuid.randomString(),
                    url = string,
                ),
            )
            updateAll()
        }
    }

    fun delete(subscription: MediaSourceSubscription) {
        onDelete(subscription)
    }

    private val exportTasker = MonoTasker(backgroundScope)
    val isExportInProgress get() = exportTasker.isRunning
    suspend fun exportToString(subscription: MediaSourceSubscription): String {
        return exportTasker.async {
            onExportLocalChangesToString(subscription)
        }.await()
    }
}

@Composable
internal fun SettingsScope.MediaSourceSubscriptionGroup(
    state: MediaSourceSubscriptionGroupState,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    Group(
        title = { Text(stringResource(Lang.settings_media_source_subscription)) },
        description = { Text(stringResource(Lang.settings_media_source_subscription_description)) },
        actions = {
            IconButton({ showAddDialog = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(Lang.settings_media_source_subscription_add),
                )
            }

            AnimatedContent(
                state.isUpdateAllInProgress.collectAsStateWithLifecycle().value,
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (it) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    IconButton({ state.updateAll() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(Lang.settings_media_source_subscription_refresh_all),
                        )
                    }
                }
            }
        },
    ) {
        for ((index, subscription) in state.subscriptions.withIndex()) {
            SubscriptionItem(subscription, state)
            if (index != state.subscriptions.lastIndex) {
                HorizontalDividerItem()
            }
        }

        if (showAddDialog) {
            val textFieldFocus = remember { FocusRequester() }
            val clipboard = LocalClipboard.current
            val uiScope = rememberCoroutineScope()
            val toaster = LocalToaster.current
            val confirmAdd = {
                showAddDialog = false
                state.addNew(state.editingUrl)
            }
            val isAddInProgressState = state.isAddInProgress.collectAsStateWithLifecycle()
            AlertDialog(
                { showAddDialog = false },
                confirmButton = {
                    AnimatedContent(
                        isAddInProgressState.value,
                        transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        if (it) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            TextButton(confirmAdd) {
                                Text(stringResource(Lang.settings_media_source_subscription_add_confirm))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton({ showAddDialog = false }) {
                        Text(stringResource(Lang.settings_media_source_subscription_cancel))
                    }
                },
                title = {
                    Text(stringResource(Lang.settings_media_source_subscription_add_dialog))
                },
                text = {
                    OutlinedTextField(
                        value = state.editingUrl,
                        onValueChange = { state.setEditingUrl(it) },
                        Modifier.focusRequester(textFieldFocus),
                        isError = state.editingUrlIsError,
                        enabled = !isAddInProgressState.value,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirmAdd() }),
                        label = { Text(stringResource(Lang.settings_media_source_subscription_url)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    uiScope.launch {
                                        val clipText = clipboard.getClipEntryText()
                                        if (clipText.isNullOrBlank()) {
                                            toaster.toast(getString(Lang.settings_mediasource_clipboard_empty))
                                            return@launch
                                        }
                                        state.setEditingUrl(clipText)
                                    }
                                },
                                enabled = !isAddInProgressState.value,
                            ) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = stringResource(Lang.settings_media_source_subscription_paste),
                                )
                            }
                        },
                    )
                    SideEffect {
                        textFieldFocus.requestFocus()
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsScope.SubscriptionItem(
    subscription: MediaSourceSubscription,
    state: MediaSourceSubscriptionGroupState
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    Item(
        headlineContent = {
            SelectionContainer {
                Text(subscription.url)
            }
        },
        supportingContent = {
            Text(
                "每 ${subscription.updatePeriod} 自动更新，" + formatLastUpdated(subscription.lastUpdated),
            )
        },
        trailingContent = {
            var showDropdown by remember { mutableStateOf(false) }
            IconButton({ showDropdown = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null)
            }
            DropdownMenu(showDropdown, { showDropdown = false }) {
                val uiScope = rememberCoroutineScope()
                val clipboard = LocalClipboard.current
                val toaster = LocalToaster.current

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    text = { Text(stringResource(Lang.settings_media_source_subscription_copy_link)) },
                    onClick = {
                        showDropdown = false
                        uiScope.launch {
                            clipboard.setClipEntryText(subscription.url)
                            toaster.toast(getString(Lang.settings_media_source_subscription_copied))
                        }
                    },
                )

                val enableActions = !state.isExportInProgress.collectAsStateWithLifecycle().value
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    text = { Text(stringResource(Lang.settings_media_source_subscription_export_all)) },
                    onClick = {
                        uiScope.launch {
                            val string = state.exportToString(subscription)
                            clipboard.setClipEntryText(string)
                            showDropdown = false
                            toaster.toast(getString(Lang.settings_media_source_subscription_copied))
                        }
                    },
                    enabled = enableActions,
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    text = {
                        Text(
                            stringResource(Lang.settings_media_source_subscription_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showDropdown = false
                        showConfirmDelete = true
                    },
                    enabled = enableActions,
                )
            }
        },
    )
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_media_source_subscription_delete_dialog)) },
            text = {
                Text(
                    stringResource(
                        Lang.settings_media_source_subscription_delete_description,
                        subscription.lastUpdated?.mediaSourceCount ?: 0,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    {
                        state.delete(subscription)
                        showConfirmDelete = false
                    },
                ) {
                    Text(
                        stringResource(Lang.settings_media_source_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    {
                        showConfirmDelete = false
                    },
                ) { Text(stringResource(Lang.settings_media_source_cancel)) }
            },
        )

    }
}

@Composable
private fun formatLastUpdated(lastUpdated: MediaSourceSubscription.LastUpdated?): String {
    if (lastUpdated == null) return stringResource(Lang.settings_media_source_subscription_not_updated)
    val mediaSourceCount = lastUpdated.mediaSourceCount
    val error = lastUpdated.error
    return when {
        error != null || mediaSourceCount == null -> {
            "${formatDateTime(lastUpdated.timeMillis)}${stringResource(Lang.settings_media_source_subscription_update_failed)}${
                formatError(
                    error,
                )
            }"
        }

        else -> {
            "${formatDateTime(lastUpdated.timeMillis)}${
                stringResource(
                    Lang.settings_media_source_subscription_update_success,
                    mediaSourceCount,
                )
            }"
        }
    }
}

@Composable
private fun formatError(error: MediaSourceSubscription.UpdateError?): String {
    if (error == null) return stringResource(Lang.settings_media_source_subscription_unknown_error)
    val failre =
        error.failure ?: return error.message ?: stringResource(Lang.settings_media_source_subscription_unknown_error)
    return when (failre) {
        ApiFailure.NetworkError -> stringResource(Lang.settings_media_source_subscription_network_error)
        ApiFailure.ServiceUnavailable -> stringResource(Lang.settings_media_source_subscription_service_unavailable)
        ApiFailure.Unauthorized -> stringResource(Lang.settings_media_source_subscription_unauthorized)
    }
}

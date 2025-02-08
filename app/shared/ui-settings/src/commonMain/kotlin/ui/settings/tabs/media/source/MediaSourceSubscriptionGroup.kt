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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.Uuid
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
        title = { Text("数据源订阅") },
        description = { Text("可通过订阅添加多个数据源，自动定时更新订阅") },
        actions = {
            IconButton({ showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "添加")
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
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新全部")
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
                                Text("添加")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton({ showAddDialog = false }) {
                        Text("取消")
                    }
                },
                title = {
                    Text("添加订阅")
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
                        label = { Text("URL (HTTP)") },
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
                val clipboard = LocalClipboardManager.current
                val toaster = LocalToaster.current

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    text = { Text("复制链接") },
                    onClick = {
                        clipboard.setText(AnnotatedString(subscription.url))
                        showDropdown = false
                        toaster.toast("已复制")
                    },
                )

                val enableActions = !state.isExportInProgress.collectAsStateWithLifecycle().value
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    text = { Text("全部导出到剪贴板") },
                    onClick = {
                        uiScope.launch {
                            val string = state.exportToString(subscription)
                            clipboard.setText(AnnotatedString(string))
                            showDropdown = false
                            toaster.toast("已复制")
                        }
                    },
                    enabled = enableActions,
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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
            title = { Text("删除数据源订阅") },
            text = {
                Text("将会同时删除来自该订阅的所有数据源 (共 ${subscription.lastUpdated?.mediaSourceCount ?: 0} 个)")
            },
            confirmButton = {
                TextButton(
                    {
                        state.delete(subscription)
                        showConfirmDelete = false
                    },
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton({ showConfirmDelete = false }) { Text("取消") } },
        )

    }
}

@Composable
private fun formatLastUpdated(lastUpdated: MediaSourceSubscription.LastUpdated?): String {
    if (lastUpdated == null) return "还未更新"
    val mediaSourceCount = lastUpdated.mediaSourceCount
    val error = lastUpdated.error
    return when {
        error != null || mediaSourceCount == null -> {
            "${formatDateTime(lastUpdated.timeMillis)}更新失败：${formatError(error)}"
        }

        else -> {
            "${formatDateTime(lastUpdated.timeMillis)}更新成功，包含 $mediaSourceCount 个数据源"
        }
    }
//    return when (lastUpdated.error) {
//        MediaSourceSubscription.UpdateError.NETWORK_ERROR -> "${formatDateTime(lastUpdated.timeMillis)}因网络错误更新失败"
//        MediaSourceSubscription.UpdateError.INVALID_CONFIG -> "${formatDateTime(lastUpdated.timeMillis)}因远程配置有误更新失败"
//        null -> "${formatDateTime(lastUpdated.timeMillis)}更新成功"
//    }
}

private fun formatError(error: MediaSourceSubscription.UpdateError?): String {
    if (error == null) return "未知错误"
    val failre = error.failure ?: return error.message ?: "未知错误"
    return when (failre) {
        ApiFailure.NetworkError -> "网络错误，请检查网络连接"
        ApiFailure.ServiceUnavailable -> "服务暂不可用，请稍后重试"
        ApiFailure.Unauthorized -> "服务禁止访问，请联系服务提供商"
    }
}

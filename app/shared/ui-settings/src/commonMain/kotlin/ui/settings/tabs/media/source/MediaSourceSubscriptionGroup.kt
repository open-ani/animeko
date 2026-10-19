/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.mediasource.subscription.displayName
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_subscription_add_confirm
import me.him188.ani.app.ui.lang.settings_media_source_subscription_add_dialog
import me.him188.ani.app.ui.lang.settings_media_source_subscription_cancel
import me.him188.ani.app.ui.lang.settings_media_source_subscription_media_source_count
import me.him188.ani.app.ui.lang.settings_media_source_subscription_network_error
import me.him188.ani.app.ui.lang.settings_media_source_subscription_not_updated
import me.him188.ani.app.ui.lang.settings_media_source_subscription_paste
import me.him188.ani.app.ui.lang.settings_media_source_subscription_refresh_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_requires_newer_app
import me.him188.ani.app.ui.lang.settings_media_source_subscription_service_unavailable
import me.him188.ani.app.ui.lang.settings_media_source_subscription_unauthorized
import me.him188.ani.app.ui.lang.settings_media_source_subscription_unknown_error
import me.him188.ani.app.ui.lang.settings_media_source_subscription_update_failed
import me.him188.ani.app.ui.lang.settings_media_source_subscription_update_success
import me.him188.ani.app.ui.lang.settings_media_source_subscription_updated_at
import me.him188.ani.app.ui.lang.settings_media_source_subscription_url
import me.him188.ani.app.ui.lang.settings_media_source_subscriptions
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
    private val onUpdate: suspend (subscriptionId: String) -> Unit,
    private val onAdd: suspend (MediaSourceSubscription) -> Unit,
    private val onDelete: (MediaSourceSubscription) -> Unit,
    private val onExportToString: suspend (MediaSourceSubscription) -> String,
    backgroundScope: CoroutineScope,
) {
    val subscriptions by subscriptionsState

    fun findSubscription(subscriptionId: String): MediaSourceSubscription? {
        return subscriptions.find { it.subscriptionId == subscriptionId }
    }

    private val updateAllTasker = MonoTasker(backgroundScope)
    val isUpdateAllInProgress get() = updateAllTasker.isRunning
    fun updateAll() {
        updateAllTasker.launch {
            onUpdateAll()
        }
    }

    private val updateTasker = MonoTasker(backgroundScope)
    val isUpdateInProgress get() = updateTasker.isRunning
    fun update(subscriptionId: String) {
        updateTasker.launch {
            onUpdate(subscriptionId)
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
            onExportToString(subscription)
        }.await()
    }
}

internal object MediaSourceSubscriptionGroupTestTags {
    fun item(subscriptionId: String): String = "media_source_subscription_$subscriptionId"
}

internal val MediaSourceSubscription.description: String?
    get() = metadata?.description?.takeIf { it.isNotBlank() }

@Composable
internal fun SettingsScope.MediaSourceSubscriptionGroup(
    state: MediaSourceSubscriptionGroupState,
    mediaSourcesOfSubscription: (subscriptionId: String) -> List<MediaSourcePresentation>,
    onOpenSubscription: (subscriptionId: String) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    Group(
        title = { Text(stringResource(Lang.settings_media_source_subscriptions)) },
        actions = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                AnimatedContent(
                    state.isUpdateAllInProgress.collectAsStateWithLifecycle().value,
                    transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (it) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        IconButton({ state.updateAll() }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(Lang.settings_media_source_subscription_refresh_all),
                            )
                        }
                    }
                }
            }
        },
    ) {
        for (subscription in state.subscriptions) {
            MediaSourceSubscriptionItem(
                subscription,
                mediaSources = mediaSourcesOfSubscription(subscription.subscriptionId),
                onClick = { onOpenSubscription(subscription.subscriptionId) },
                Modifier.testTag(MediaSourceSubscriptionGroupTestTags.item(subscription.subscriptionId)),
            )
        }

        AddItem(
            stringResource(Lang.settings_media_source_subscription_add_dialog),
            onClick = { showAddDialog = true },
        )

        if (showAddDialog) {
            AddSubscriptionDialog(state, onDismissRequest = { showAddDialog = false })
        }
    }
}

/**
 * 订阅列表中的一行: 图标, 名称, 以及数据源数量和更新状态. 点击进入订阅详情.
 * 与数据源行的结构相同.
 */
@Composable
private fun SettingsScope.MediaSourceSubscriptionItem(
    subscription: MediaSourceSubscription,
    mediaSources: List<MediaSourcePresentation>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Item(
        headlineContent = {
            Text(subscription.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier.clickable(onClick = onClick),
        supportingContent = {
            SubscriptionSummary(subscription.lastUpdated, mediaSources.size)
        },
        leadingContent = {
            MediaSourceSubscriptionIcon(subscription, mediaSources)
        },
        trailingContent = {
            // 与其他行 48dp 图标按钮中的 ⋮ 对齐, 左侧不占多余的宽度, 留给名称
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * 例如 "18 个数据源 · 刚刚更新". 更新失败时显示错误.
 */
@Composable
private fun SubscriptionSummary(lastUpdated: MediaSourceSubscription.LastUpdated?, mediaSourceCount: Int) {
    val failed = lastUpdated != null && (lastUpdated.error != null || lastUpdated.mediaSourceCount == null)
    if (failed) {
        val color = MaterialTheme.colorScheme.error
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Error, contentDescription = null, Modifier.size(16.dp), tint = color)
            Text(
                stringResource(Lang.settings_media_source_subscription_update_failed) + formatError(lastUpdated?.error),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val updateStatus = if (lastUpdated == null) {
        stringResource(Lang.settings_media_source_subscription_not_updated)
    } else {
        stringResource(Lang.settings_media_source_subscription_updated_at, formatDateTime(lastUpdated.timeMillis))
    }
    Text(
        if (mediaSourceCount > 0) {
            stringResource(Lang.settings_media_source_subscription_media_source_count, mediaSourceCount) +
                " · " + updateStatus
        } else {
            updateStatus
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 订阅作者提供的图标. 没有提供时用订阅中前几个数据源的图标拼成, 还没有数据源时 (例如还未更新) 显示默认图标.
 */
@Composable
internal fun MediaSourceSubscriptionIcon(
    subscription: MediaSourceSubscription,
    mediaSources: List<MediaSourcePresentation>,
    modifier: Modifier = Modifier,
) {
    val iconUrl = subscription.metadata?.iconUrl?.takeIf { it.isNotBlank() }
    when {
        iconUrl != null -> AsyncImage(
            iconUrl,
            contentDescription = null,
            modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )

        mediaSources.isNotEmpty() -> MediaSourceIconMosaic(mediaSources.map { it.info }, modifier)

        else -> Box(
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Layers,
                contentDescription = null,
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    state: MediaSourceSubscriptionGroupState,
    onDismissRequest: () -> Unit,
) {
    val textFieldFocus = remember { FocusRequester() }
    val clipboard = LocalClipboard.current
    val uiScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val confirmAdd = {
        onDismissRequest()
        state.addNew(state.editingUrl)
    }
    val isAddInProgressState = state.isAddInProgress.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest,
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
            TextButton(onDismissRequest) {
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

@Composable
internal fun formatLastUpdated(lastUpdated: MediaSourceSubscription.LastUpdated?): String {
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
    if (error.requiresNewerApp) return stringResource(Lang.settings_media_source_subscription_requires_newer_app)
    val failre =
        error.failure ?: return error.message ?: stringResource(Lang.settings_media_source_subscription_unknown_error)
    return when (failre) {
        ApiFailure.NetworkError -> stringResource(Lang.settings_media_source_subscription_network_error)
        ApiFailure.ServiceUnavailable -> stringResource(Lang.settings_media_source_subscription_service_unavailable)
        ApiFailure.Unauthorized -> stringResource(Lang.settings_media_source_subscription_unauthorized)
    }
}

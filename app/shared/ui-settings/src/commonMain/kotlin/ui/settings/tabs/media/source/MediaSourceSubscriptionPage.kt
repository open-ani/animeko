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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_delete_confirm
import me.him188.ani.app.ui.lang.settings_media_source_more
import me.him188.ani.app.ui.lang.settings_media_source_subscription_auto_update
import me.him188.ani.app.ui.lang.settings_media_source_subscription_copied
import me.him188.ani.app.ui.lang.settings_media_source_subscription_copy_link
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete_description
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete_dialog
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete_subscription
import me.him188.ani.app.ui.lang.settings_media_source_subscription_disable_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_enable_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_export_all
import me.him188.ani.app.ui.lang.settings_media_source_subscription_link
import me.him188.ani.app.ui.lang.settings_media_source_subscription_unsupported_media_sources
import me.him188.ani.app.ui.lang.settings_media_source_subscription_update_now
import me.him188.ani.app.ui.lang.settings_media_source_subscription_website
import me.him188.ani.app.ui.lang.settings_media_source_view_config
import me.him188.ani.app.ui.settings.SettingsDetailPaneScope
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.ConnectionTesterRunner
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal object MediaSourceSubscriptionPageTestTags {
    const val UPDATE = "media_source_subscription_update"

    fun item(instanceId: String): String = "media_source_subscription_item_$instanceId"
}

/**
 * 订阅详情页的内容. 订阅被删除后返回上一页.
 */
@Composable
internal fun SettingsDetailPaneScope.MediaSourceSubscriptionPageContent(
    subscriptionId: String,
    subscriptionState: MediaSourceSubscriptionGroupState,
    groupState: MediaSourceGroupState,
    editState: EditMediaSourceState,
) {
    val subscription = subscriptionState.findSubscription(subscriptionId)
    // 恢复页面时订阅列表可能还没加载, 只有在订阅出现过之后消失才说明被删除了
    val seen = remember { mutableStateOf(false) }
    if (subscription == null) {
        if (seen.value) {
            LaunchedEffect(Unit) { navigateUp() }
        }
        return
    }
    SideEffect { seen.value = true }

    val mediaSources = groupState.mediaSourcesOfSubscription(subscriptionId)
    val testers = remember(mediaSources) { groupState.createTesterRunner(mediaSources) }
    SettingsTab {
        MediaSourceSubscriptionPage(subscription, mediaSources, testers, subscriptionState, editState)
    }
}

@Composable
internal fun SettingsScope.MediaSourceSubscriptionPage(
    subscription: MediaSourceSubscription,
    mediaSources: List<MediaSourcePresentation>,
    testers: ConnectionTesterRunner<*>,
    subscriptionState: MediaSourceSubscriptionGroupState,
    editState: EditMediaSourceState,
) {
    val navigator = LocalNavigator.current
    Column {
        subscription.description?.let {
            Text(
                it,
                Modifier.padding(horizontal = SettingsScope.itemHorizontalPadding).padding(top = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        subscription.metadata?.websiteUrl?.takeIf { it.isNotBlank() }?.let { websiteUrl ->
            val uriHandler = LocalUriHandler.current
            Item(
                headlineContent = { Text(stringResource(Lang.settings_media_source_subscription_website)) },
                Modifier.clickable { uriHandler.openUri(websiteUrl) },
                supportingContent = {
                    Text(
                        websiteUrl.removePrefix("https://").removePrefix("http://"),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Home, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(20.dp))
                },
            )
        }

        SubscriptionUrlItem(subscription.url)
        SubscriptionUpdateItem(subscription, subscriptionState)

        HorizontalDividerItem()

        mediaSources.forEach { item ->
            val openConfiguration = { navigator.openMediaSourceConfiguration(item, editState) }
            var showMoreDropdown by remember { mutableStateOf(false) }
            MediaSourceItem(
                item,
                Modifier
                    .testTag(MediaSourceSubscriptionPageTestTags.item(item.instanceId))
                    .clickable(
                        onClickLabel = stringResource(Lang.settings_media_source_view_config),
                        onClick = openConfiguration,
                    ),
            ) {
                IconButton({}, enabled = false) { // 放在 button 里保持 padding 一致
                    ConnectionTesterResultIndicator(item.connectionTester, showIdle = false)
                }
                Box {
                    IconButton(onClick = { showMoreDropdown = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Lang.settings_media_source_more))
                    }
                    DropdownMenu(showMoreDropdown, onDismissRequest = { showMoreDropdown = false }) {
                        ToggleEnabledMenuItem(
                            item,
                            onEnabledChange = { editState.toggleMediaSourceEnabled(item, it) },
                            onDismissRequest = { showMoreDropdown = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Lang.settings_media_source_view_config)) },
                            onClick = {
                                showMoreDropdown = false
                                openConfiguration()
                            },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TestConnectionButton(testers)
        }
    }
}

@Composable
private fun SettingsScope.SubscriptionUrlItem(url: String) {
    val uiScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    Item(
        headlineContent = { Text(stringResource(Lang.settings_media_source_subscription_link)) },
        supportingContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
        trailingContent = {
            IconButton(
                {
                    uiScope.launch {
                        clipboard.setClipEntryText(url)
                        toaster.toast(getString(Lang.settings_media_source_subscription_copied))
                    }
                },
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(Lang.settings_media_source_subscription_copy_link),
                    Modifier.size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun SettingsScope.SubscriptionUpdateItem(
    subscription: MediaSourceSubscription,
    subscriptionState: MediaSourceSubscriptionGroupState,
) {
    val lastUpdated = subscription.lastUpdated
    val failed = lastUpdated != null && (lastUpdated.error != null || lastUpdated.mediaSourceCount == null)
    val isUpdating = subscriptionState.isUpdateInProgress.collectAsStateWithLifecycle().value ||
        subscriptionState.isUpdateAllInProgress.collectAsStateWithLifecycle().value
    Item(
        headlineContent = {
            Text(stringResource(Lang.settings_media_source_subscription_auto_update, subscription.updatePeriod.toString()))
        },
        supportingContent = {
            Text(
                formatLastUpdated(lastUpdated),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lastUpdated?.unsupportedMediaSourceCount?.takeIf { it > 0 }?.let { count ->
                Text(stringResource(Lang.settings_media_source_subscription_unsupported_media_sources, count))
            }
        },
        leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        trailingContent = {
            AnimatedContent(
                isUpdating,
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                contentAlignment = Alignment.Center,
            ) {
                if (it) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else {
                    IconButton(
                        { subscriptionState.update(subscription.subscriptionId) },
                        Modifier.testTag(MediaSourceSubscriptionPageTestTags.UPDATE),
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(Lang.settings_media_source_subscription_update_now),
                            Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    )
}

/**
 * 订阅详情页顶栏的 "更多" 菜单.
 */
@Composable
internal fun MediaSourceSubscriptionPageActions(
    subscriptionId: String,
    subscriptionState: MediaSourceSubscriptionGroupState,
    groupState: MediaSourceGroupState,
    editState: EditMediaSourceState,
) {
    val subscription = subscriptionState.findSubscription(subscriptionId) ?: return
    val mediaSources = groupState.mediaSourcesOfSubscription(subscriptionId)
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDelete by rememberSaveable { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current

    Box {
        IconButton({ showMenu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Lang.settings_media_source_more))
        }
        DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.settings_media_source_subscription_enable_all)) },
                onClick = {
                    showMenu = false
                    editState.setMediaSourcesEnabled(mediaSources.filterNot { it.isEnabled }, enabled = true)
                },
                enabled = mediaSources.any { !it.isEnabled },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Lang.settings_media_source_subscription_disable_all)) },
                onClick = {
                    showMenu = false
                    editState.setMediaSourcesEnabled(mediaSources.filter { it.isEnabled }, enabled = false)
                },
                enabled = mediaSources.any { it.isEnabled },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = { Text(stringResource(Lang.settings_media_source_subscription_export_all)) },
                onClick = {
                    uiScope.launch {
                        val string = subscriptionState.exportToString(subscription)
                        clipboard.setClipEntryText(string)
                        showMenu = false
                        toaster.toast(getString(Lang.settings_media_source_subscription_copied))
                    }
                },
                enabled = !subscriptionState.isExportInProgress.collectAsStateWithLifecycle().value,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(Lang.settings_media_source_subscription_delete_subscription),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    showConfirmDelete = true
                },
            )
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_media_source_subscription_delete_dialog)) },
            text = {
                Text(
                    stringResource(
                        Lang.settings_media_source_subscription_delete_description,
                        mediaSources.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    {
                        showConfirmDelete = false
                        subscriptionState.delete(subscription)
                    },
                ) {
                    Text(
                        stringResource(Lang.settings_media_source_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton({ showConfirmDelete = false }) {
                    Text(stringResource(Lang.settings_media_source_cancel))
                }
            },
        )
    }
}

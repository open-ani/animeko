/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_subscription_delete
import me.him188.ani.app.ui.lang.settings_category_data_playback
import me.him188.ani.app.ui.lang.settings_category_network_storage
import me.him188.ani.app.ui.lang.settings_category_others
import me.him188.ani.app.ui.settings.SettingsPageLayout
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroup
import me.him188.ani.app.ui.settings.tabs.app.WatchTogetherGroup
import me.him188.ani.app.ui.settings.tabs.log.LogTab
import me.him188.ani.app.ui.settings.tabs.media.BackupSettings
import me.him188.ani.app.ui.settings.tabs.media.CacheDirectoryGroup
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroup
import me.him188.ani.app.ui.settings.tabs.media.source.EditMediaSourceSubscriptionDialog
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSelectionActions
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroup
import me.him188.ani.app.ui.settings.tabs.media.source.rememberMediaSourceSelectionState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoteSettingsScreen(
    vm: RemoteSettingsViewModel,
    onNavigateBack: () -> Unit,
    scanner: @Composable (onScanned: (String) -> Unit, onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forColumnPageContent(),
    networkPermission: @Composable (onBack: () -> Unit, content: @Composable () -> Unit) -> Unit =
        { _, content ->
            content()
        },
) {
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.collectConnectionRequests()
        }
    }
    Box(modifier.testTag("remote-settings-screen")) {
        RemoteSettingsSessionHost(vm, onNavigateBack, guardExit = true) {
            contentModifier,
            requestExit ->
            val form = vm.form
            val cache = vm.cacheDirectoryGroupState
            if (form != null && cache != null)
                key(vm.remoteSession) {
                    RemoteSettingsContent(vm, form, contentModifier, windowInsets, requestExit)
                }
        }
        if (!vm.isConnected || vm.pendingRemoteLink != null) {
            BackHandler {
                vm.cancelConnection()
                onNavigateBack()
            }
            Surface(Modifier.fillMaxSize()) {
                networkPermission(onNavigateBack) {
                    val link = vm.pendingRemoteLink
                    if (link != null) LaunchedEffect(link) { vm.connectAfterPermission() }
                    // A fresh scanner instance can retry the same QR after a failed connection.
                    if (!vm.isConnecting && link == null)
                        scanner(vm::scanConnection, onNavigateBack)
                }
            }
        }
    }
}

@Composable
private fun RemoteSettingsContent(
    vm: RemoteSettingsViewModel,
    form: RemoteSettingsFormState,
    modifier: Modifier,
    windowInsets: WindowInsets,
    requestExit: () -> Unit,
) {
    val navigator: ThreePaneScaffoldNavigator<Nothing?> =
        rememberListDetailPaneScaffoldNavigator(
            initialDestinationHistory =
                listOf(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List))
        )
    val layoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)
    var lastSelectedTab by rememberSaveable { mutableStateOf<SettingsTab?>(null) }
    val selection = rememberMediaSourceSelectionState()
    val scope = rememberCoroutineScope()
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current
    LaunchedEffect(lastSelectedTab) {
        if (lastSelectedTab != SettingsTab.MEDIA_SOURCE) selection.clear()
    }
    SettingsPageLayout(
        navigator,
        currentTab = { lastSelectedTab },
        onSelectedTab = { tab ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                lastSelectedTab = tab
            }
        },
        onClickBackOnListPage = { scope.launch { navigator.navigateBack() } },
        onClickBackOnDetailPage = {
            if (selection.inSelection) selection.clear()
            else
                scope.launch {
                    navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                }
        },
        navItems = {
            Title(stringResource(Lang.settings_category_data_playback))
            Item(SettingsTab.PLAYER)
            Item(SettingsTab.MEDIA_SOURCE)
            Item(SettingsTab.MEDIA_SELECTOR)
            Title(stringResource(Lang.settings_category_network_storage))
            Item(SettingsTab.STORAGE)
            Title(stringResource(Lang.settings_category_others))
            Item(SettingsTab.LOG)
            Item(SettingsTab.SETTINGS_BACKUP)
        },
        tabContent = { tab ->
            Column {
                if (tab == SettingsTab.LOG)
                    LogTab(
                        onClickFeedback = {
                            browserNavigator.openBrowser(
                                context,
                                AniHelperDestination.ISSUE_TRACKER,
                            )
                        },
                        loggingItems = { RemoteSettingsLogActions(vm, it) },
                    )
                else
                    SettingsTab {
                        when (tab) {
                            SettingsTab.PLAYER -> {
                                PlayerGroup(
                                    form.video,
                                    form.kernel,
                                    form.filter,
                                    form.regexFilters,
                                    false,
                                    remoteTv = true,
                                )
                                WatchTogetherGroup(form.watching)
                            }
                            SettingsTab.MEDIA_SOURCE -> {
                                MediaSourceSubscriptionGroup(
                                    form.sources.subscriptionsGroup,
                                    onEdit = vm::editSubscription,
                                    onRefresh = vm::refreshSubscription,
                                    onToggleEnabled = vm::toggleSubscription,
                                    deleteDescription =
                                        stringResource(Lang.remote_settings_subscription_delete),
                                )
                                MediaSourceGroup(form.sources.group, form.sources.edit, selection)
                            }
                            SettingsTab.MEDIA_SELECTOR ->
                                MediaSelectionGroup(form.selection, remoteTv = true)
                            SettingsTab.STORAGE ->
                                vm.cacheDirectoryGroupState?.let { CacheDirectoryGroup(it) }
                            SettingsTab.SETTINGS_BACKUP ->
                                vm.cacheDirectoryGroupState?.let { BackupSettings(it) }
                            else -> Unit
                        }
                    }
                if (tab == SettingsTab.MEDIA_SOURCE)
                    AniAnimatedVisibility(selection.inSelection) {
                        Spacer(Modifier.height(80.dp))
                    }
                Spacer(
                    Modifier.height(
                        currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding
                    )
                )
            }
        },
        detailPaneBottomBar = { tab, insets ->
            if (tab == SettingsTab.MEDIA_SOURCE)
                AniAnimatedVisibility(
                    selection.inSelection,
                    Modifier.align(Alignment.BottomCenter),
                ) {
                    MediaSourceSelectionActions(
                        form.sources.group.mediaSources,
                        selection,
                        form.sources.edit,
                        windowInsets = insets,
                    )
                }
        },
        modifier = modifier,
        contentWindowInsets = windowInsets,
        navigationIcon = {
            BackNavigationIconButton(requestExit, Modifier.testTag("remote-settings-back"))
        },
        layoutParameters = layoutParameters,
        loadOpenSourceLibrariesJsons = { emptyList() },
        defaultTab = SettingsTab.PLAYER,
    )
    vm.editingSubscription?.let {
        val busy = vm.remoteSession?.busy?.collectAsStateWithLifecycle()?.value ?: true
        EditMediaSourceSubscriptionDialog(
            it,
            busy,
            vm::cancelSubscriptionEdit,
            vm::saveSubscription,
        )
    }
}

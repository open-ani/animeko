/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.framework.components.SettingsScope

@Composable
fun PeerFilterSettingsScreen(
    state: PeerFilterSettingsState,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<Any> = rememberListDetailPaneScaffoldNavigator(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    Surface(color = AniThemeDefaults.pageContentBackgroundColor) {
        AniListDetailPaneScaffold(
            navigator = navigator,
            listPaneTopAppBar = {
                SearchBlockedIpTopAppBar(
                    enableSearch = !listDetailLayoutParameters.preferSinglePane,
                    title = { AniTopAppBarDefaults.Title("Peer 过滤和屏蔽设置") },
                    state = state,
                    windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    navigationIcon = {
                        if (navigator.canNavigateBack()) {
                            BackNavigationIconButton(
                                onNavigateBack = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        navigator.navigateBack()
                                    }
                                },
                            )
                        } else {
                            navigationIcon()
                        }
                    },
                )
            },
            listPaneContent = {
                PeerFilterEditPane(
                    state = state,
                    showIpBlockingItem = listDetailLayoutParameters.preferSinglePane,
                    onClickIpBlockSettings = {
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            navigator.navigateTo(ThreePaneScaffoldRole.Primary)
                        }
                    },
                    modifier = Modifier
                        .paneContentPadding(
                            extraStart = -SettingsScope.itemHorizontalPadding,
                            extraEnd = -SettingsScope.itemHorizontalPadding,
                        )
                        .paneWindowInsetsPadding()
                        .padding(horizontal = SettingsScope.itemExtraHorizontalPadding),
                )
            },
            detailPane = {
                val filteredList by state.searchedIpBlockList.collectAsStateWithLifecycle(emptyList())

                if (listDetailLayoutParameters.preferSinglePane) {
                    SearchBlockedIpTopAppBar(
                        enableSearch = true,
                        title = { AniTopAppBarDefaults.Title("管理 IP 黑名单") },
                        state = state,
                        navigationIcon = {
                            BackNavigationIconButton(
                                onNavigateBack = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        if (navigator.canNavigateBack()) {
                                            navigator.navigateBack()
                                        }
                                    }
                                },
                            )
                        },
                        windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                }

                Box(Modifier.consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top))) {
                    BlackListEditPane(
                        ipBlackList = filteredList,
                        showTitle = !listDetailLayoutParameters.preferSinglePane,
                        modifier = Modifier
                            .paneContentPadding(
                                extraStart = -SettingsScope.itemHorizontalPadding,
                                extraEnd = -SettingsScope.itemHorizontalPadding,
                            )
                            .paneWindowInsetsPadding()
                            .padding(horizontal = SettingsScope.itemExtraHorizontalPadding),
                        onAdd = { state.addBlockedIp(it) },
                        onRemove = { state.removeBlockedIp(it) },
                    )
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchBlockedIpTopAppBar(
    enableSearch: Boolean,
    state: PeerFilterSettingsState,
    title: @Composable () -> Unit,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    BackHandler(enableSearch && state.searchingBlockedIp) {
        state.stopSearchBlockedIp()
    }

    AniTopAppBar(
        title = {
            if (enableSearch && state.searchingBlockedIp) SearchBlockedIp(state) else title()
        },
        navigationIcon = navigationIcon,
        avatar = {
            if (enableSearch && !state.searchingBlockedIp) {
                IconButton({ state.startSearchBlockedIp() }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索黑名单 IP 地址")
                }
            }
        },
        colors = AniThemeDefaults.transparentAppBarColors(),
        windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}

@Composable
private fun SearchBlockedIp(state: PeerFilterSettingsState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val searchQuery by state.searchBlockedIpQuery.collectAsStateWithLifecycle("")

    TextField(
        value = searchQuery,
        onValueChange = { state.setSearchBlockIpQuery(it) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
    )

    SideEffect { focusRequester.requestFocus() }
}
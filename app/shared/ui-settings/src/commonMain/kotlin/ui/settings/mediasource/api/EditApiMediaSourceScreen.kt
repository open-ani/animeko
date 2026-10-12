/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.ListDetailAnimatedPane
import me.him188.ani.app.ui.foundation.layout.materialWindowMarginPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_more
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_title
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuExport
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuImport
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditApiMediaSourceScreen(
    viewModel: EditApiMediaSourceViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle(null)
    state?.let {
        EditApiMediaSourceScreen(
            it, viewModel.testState, modifier,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
        )
    }
}

/**
 * 编辑 JSON API 数据源. 左侧 (或单独一页) 是配置, 右侧是测试, 与 RSS 和 Selector 编辑页一致.
 */
@Composable
fun EditApiMediaSourceScreen(
    state: EditApiMediaSourceState,
    testState: ApiTestState,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    // 在外面启动, 避免在切换页面后重新启动导致刷新
    LaunchedEffect(testState) {
        testState.observeChanges()
    }

    Scaffold(
        modifier.fillMaxSize(),
        topBar = {
            WindowDragArea {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            navigator.currentDestination?.pane,
                            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                        ) {
                            when (it) {
                                ListDetailPaneScaffoldRole.Detail ->
                                    Text(stringResource(Lang.settings_mediasource_selector_test_title))

                                else -> Text(state.displayName)
                            }
                        }
                    },
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
                    actions = {
                        if (navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                    }
                                },
                            ) {
                                Text(stringResource(Lang.settings_mediasource_selector_test))
                            }
                        }
                        Box {
                            var showDropdown by remember { mutableStateOf(false) }
                            IconButton({ showDropdown = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(Lang.settings_mediasource_selector_more))
                            }
                            DropdownMenu(showDropdown, { showDropdown = false }) {
                                MediaSourceConfigurationDefaults.DropdownMenuImport(
                                    state = state.importState,
                                    onImported = { showDropdown = false },
                                    enabled = !state.isLoading,
                                )
                                MediaSourceConfigurationDefaults.DropdownMenuExport(
                                    state = state.exportState,
                                    onDismissRequest = { showDropdown = false },
                                    enabled = !state.isLoading,
                                )
                            }
                        }
                    },
                    colors = AniThemeDefaults.topAppBarColors(),
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        BackHandler(navigator.canNavigateBack()) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack()
            }
        }

        ListDetailPaneScaffold(
            navigator.scaffoldDirective,
            navigator.scaffoldValue,
            listPane = {
                ListDetailAnimatedPane(Modifier.preferredWidth(480.dp)) {
                    ApiConfigurationPane(
                        state,
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(paddingValues),
                        contentPadding = paddingValues,
                    )
                }
            },
            detailPane = {
                ListDetailAnimatedPane {
                    ApiTestPane(
                        testState,
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(paddingValues),
                        contentPadding = paddingValues,
                    )
                }
            },
            Modifier.materialWindowMarginPadding(),
        )
    }
}

@Composable
@PreviewLightDark
private fun PreviewEditApiMediaSourceScreenPhone() = ProvideCompositionLocalsForPreview {
    val (edit, test) = rememberTestEditApiMediaSourceStateAndTestState()
    EditApiMediaSourceScreen(edit, test)
}

@Composable
@PreviewLightDark
private fun PreviewEditApiMediaSourceScreenPhoneTest() = ProvideCompositionLocalsForPreview {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val (edit, test) = rememberTestEditApiMediaSourceStateAndTestState()
    EditApiMediaSourceScreen(edit, test, navigator = navigator)
    LaunchedEffect(Unit) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }
}

@Composable
@Preview(widthDp = 1280, heightDp = 800)
private fun PreviewEditApiMediaSourceScreenLaptop() = ProvideCompositionLocalsForPreview {
    val (edit, test) = rememberTestEditApiMediaSourceStateAndTestState()
    EditApiMediaSourceScreen(edit, test)
}

/**
 * 预览用: 配置来自内存, 测试面板没有可用的站点, 搜索会显示错误.
 */
@TestOnly
@Composable
internal fun rememberTestEditApiMediaSourceStateAndTestState(
    arguments: ApiMediaSourceArguments = ApiMediaSourceArguments.Default,
): Pair<EditApiMediaSourceState, ApiTestState> {
    val scope = rememberCoroutineScope()
    val storage = remember { createTestSaveableStorage(arguments) }
    val edit = remember {
        EditApiMediaSourceState(
            argumentsStorage = storage,
            allowEditState = stateOf(true),
            instanceId = "test-id",
            codecManager = createTestMediaSourceCodecManager(),
        )
    }
    val test = remember {
        ApiTestState(
            argumentsState = storage.containerState,
            createSource = { throw UnsupportedOperationException("No API in previews") },
            backgroundScope = scope,
        )
    }
    return edit to test
}

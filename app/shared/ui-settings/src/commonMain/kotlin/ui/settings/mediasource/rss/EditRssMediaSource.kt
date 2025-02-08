/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.ListDetailAnimatedPane
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides
import me.him188.ani.app.ui.foundation.layout.ThreePaneScaffoldValueConverter.ExtraPaneForNestedDetails
import me.him188.ani.app.ui.foundation.layout.convert
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.only
import me.him188.ani.app.ui.foundation.layout.panePadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuExport
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuImport
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.observeTestDataChanges
import me.him188.ani.app.ui.settings.mediasource.rss.detail.RssDetailPane
import me.him188.ani.app.ui.settings.mediasource.rss.detail.SideSheetPane
import me.him188.ani.app.ui.settings.mediasource.rss.edit.RssEditPane
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPane
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPaneState
import me.him188.ani.datasources.api.Media

/**
 * 整个编辑 RSS 数据源页面的状态. 对于测试部分: [RssTestPaneState]
 *
 * @see RssMediaSource
 */
@Stable
class EditRssMediaSourceState(
    private val argumentsStorage: SaveableStorage<RssMediaSourceArguments>,
    private val allowEditState: State<Boolean>,
    val instanceId: String,
    codecManager: MediaSourceCodecManager,
) {
    private val arguments by argumentsStorage.containerState
    val isLoading by derivedStateOf { arguments == null }

    val enableEdit by derivedStateOf {
        !isLoading && allowEditState.value
    }

    var displayName by argumentsStorage.prop(
        RssMediaSourceArguments::name, { copy(name = it) },
        "",
    )

    val displayNameIsError by derivedStateOf { displayName.isBlank() }

    var iconUrl by argumentsStorage.prop(
        RssMediaSourceArguments::iconUrl, { copy(iconUrl = it) },
        "",
    )
    val displayIconUrl by derivedStateOf {
        iconUrl.ifBlank { RssMediaSourceArguments.DEFAULT_ICON_URL }
    }

    var searchUrl by argumentsStorage.prop(
        { it.searchConfig.searchUrl }, { copy(searchConfig = searchConfig.copy(searchUrl = it)) },
        "",
    )
    val searchUrlIsError by derivedStateOf { searchUrl.isBlank() }

    var filterByEpisodeSort by argumentsStorage.prop(
        { it.searchConfig.filterByEpisodeSort }, { copy(searchConfig = searchConfig.copy(filterByEpisodeSort = it)) },
        true,
    )
    var filterBySubjectName by argumentsStorage.prop(
        { it.searchConfig.filterBySubjectName }, { copy(searchConfig = searchConfig.copy(filterBySubjectName = it)) },
        true,
    )

    val searchConfig by derivedStateOf {
        RssSearchConfig(
            searchUrl = searchUrl,
            filterByEpisodeSort = filterByEpisodeSort,
            filterBySubjectName = filterBySubjectName,
        )
    }

    val importState = ImportMediaSourceState<RssMediaSourceArguments>(
        codecManager,
        onImport = { argumentsStorage.set(it) },
    )
    val exportState = ExportMediaSourceState(
        codecManager,
        onExport = { argumentsStorage.container },
    )
}

@Composable
fun EditRssMediaSourceScreen(
    viewModel: EditRssMediaSourceViewModel,
    mediaDetailsColumn: @Composable (Media) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit,
) {
    viewModel.state.collectAsStateWithLifecycle(null).value?.let {
        EditRssMediaSourceScreen(
            it, viewModel.testState, mediaDetailsColumn, modifier, windowInsets = windowInsets,
            navigationIcon = navigationIcon,
        )
    }
}

@Composable
fun EditRssMediaSourceScreen(
    state: EditRssMediaSourceState,
    testState: RssTestPaneState,
    mediaDetailsColumn: @Composable (Media) -> Unit,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        testState.searcher.observeTestDataChanges(testState.testDataState)
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier
            .fillMaxSize(),
        topBar = {
            WindowDragArea {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            navigator.currentDestination?.pane,
                            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                        ) {
                            when (it) {
                                ListDetailPaneScaffoldRole.List -> Text(state.displayName)
                                ListDetailPaneScaffoldRole.Detail -> Text("测试数据源")
                                ListDetailPaneScaffoldRole.Extra -> Text("详情")
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
                    colors = AniThemeDefaults.topAppBarColors(),
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    actions = {
                        if (navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                    }
                                },
                            ) {
                                Text("测试")
                            }
                        }
                        Box {
                            var showDropdown by remember { mutableStateOf(false) }
                            IconButton({ showDropdown = true }) {
                                Icon(Icons.Rounded.MoreVert, "更多")
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

        val panePadding = currentWindowAdaptiveInfo1().windowSizeClass.panePadding
        val panePaddingVertical = panePadding.only(PaddingValuesSides.Vertical)
        ListDetailPaneScaffold(
            navigator.scaffoldDirective,
            navigator.scaffoldValue.convert(ExtraPaneForNestedDetails),
            listPane = {
                ListDetailAnimatedPane {
                    RssEditPane(
                        state = state,
                        Modifier.fillMaxSize(),
                        contentPadding = panePaddingVertical,
                    )
                }
            },
            detailPane = {
                ListDetailAnimatedPane {
                    RssTestPane(
                        testState,
                        onNavigateToDetails = {
                            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Extra)
                            }
                        },
                        Modifier.fillMaxSize(),
                        contentPadding = panePaddingVertical,
                    )
                }
            },
            Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(panePadding.only(PaddingValuesSides.Horizontal)),
            extraPane = {
                ListDetailAnimatedPane {
                    Crossfade(testState.viewingItem) { item ->
                        item ?: return@Crossfade
                        if (currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium) {
                            SideSheetPane(
                                onClose = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        navigator.navigateBack()
                                    }
                                },
                                Modifier.padding(panePaddingVertical),
                            ) {
                                RssDetailPane(
                                    item,
                                    mediaDetailsColumn = mediaDetailsColumn,
                                    Modifier
                                        .fillMaxSize(),
                                )
                            }
                        } else {
                            RssDetailPane(
                                item,
                                mediaDetailsColumn = mediaDetailsColumn,
                                Modifier
                                    .fillMaxSize(),
                                contentPadding = panePaddingVertical,
                            )
                        }
                    }
                }
            },
        )
    }
}


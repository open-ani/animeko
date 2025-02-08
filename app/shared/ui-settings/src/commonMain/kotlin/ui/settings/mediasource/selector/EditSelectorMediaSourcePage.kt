/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector

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
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.test.web.SelectorMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodePresentation
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.ListDetailAnimatedPane
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.layout.materialWindowMarginPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuExport
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuImport
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import me.him188.ani.app.ui.settings.mediasource.observeTestDataChanges
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigState
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigurationPane
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodePaneDefaults
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodePaneLayout
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodePaneRoutes
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodeState
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorTestAndEpisodePane
import me.him188.ani.app.ui.settings.mediasource.selector.test.SelectorTestState
import kotlin.coroutines.CoroutineContext

class EditSelectorMediaSourcePageState(
    private val argumentsStorage: SaveableStorage<SelectorMediaSourceArguments>,
    allowEditState: State<Boolean>,
    engine: SelectorMediaSourceEngine,
    webViewVideoExtractor: State<WebViewVideoExtractor?>,
    codecManager: MediaSourceCodecManager,
    backgroundScope: CoroutineScope,
    context: Context,
    flowDispatcher: CoroutineContext = Dispatchers.Default,
) {
    internal val configurationState: SelectorConfigState = SelectorConfigState(
        argumentsStorage,
        allowEditState = allowEditState,
    )

    internal val testState: SelectorTestState =
        SelectorTestState(configurationState.searchConfigState, SelectorMediaSourceTester(engine), backgroundScope)

    private val viewingItemState = mutableStateOf<SelectorTestEpisodePresentation?>(null)

    var viewingItem by viewingItemState
        private set

    lateinit var episodeNavController: NavHostController
        internal set // set from ui

    fun viewEpisode(
        episode: SelectorTestEpisodePresentation,
    ) {
        this.viewingItem = episode
        if (episodeNavController.currentDestination?.hasRoute<SelectorEpisodePaneRoutes.EPISODE>() != true) {
            episodeNavController.navigate(SelectorEpisodePaneRoutes.EPISODE)
        }
    }

    fun stopViewing() {
        this.viewingItem = null
        if (episodeNavController.currentDestination?.hasRoute<SelectorEpisodePaneRoutes.TEST>() != true) {
            episodeNavController.navigate(SelectorEpisodePaneRoutes.TEST)
        }
    }


    internal val episodeState: SelectorEpisodeState = SelectorEpisodeState(
        itemState = viewingItemState,
        matchVideoConfigState = derivedStateOf { configurationState.searchConfigState.value?.matchVideo },
        webViewVideoExtractor = webViewVideoExtractor,
        engine = engine,
        backgroundScope = backgroundScope,
        context = context,
        flowDispatcher = flowDispatcher,
    )


    val importState = ImportMediaSourceState<SelectorMediaSourceArguments>(
        codecManager,
        onImport = { argumentsStorage.set(it) },
    )
    val exportState = ExportMediaSourceState(
        codecManager,
        onExport = { argumentsStorage.container },
    )
}

@Composable
fun EditSelectorMediaSourceScreen(
    vm: EditSelectorMediaSourceViewModel,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle(null)
    state?.let {
        EditSelectorMediaSourceScreen(
            it, modifier, navigator, windowInsets,
            navigationIcon,
        )
    }
}

@Composable
fun EditSelectorMediaSourceScreen(
    state: EditSelectorMediaSourcePageState,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val episodePaneLayout = SelectorEpisodePaneLayout.calculate(navigator.scaffoldValue)
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier,
        topBar = {
            WindowDragArea {
                val myNavigationIcon = @Composable {
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
                }

                val viewingItem = state.viewingItem
                if (viewingItem != null && episodePaneLayout.showTopBarInScaffold) {
                    SelectorEpisodePaneDefaults.TopAppBar(
                        state.episodeState,
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        navigationIcon = myNavigationIcon,
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (viewingItem != null) {
                                Text(viewingItem.name)
                            } else {
                                Text(state.configurationState.displayName)
                            }
                        },
                        navigationIcon = myNavigationIcon,
                        actions = {
                            if (currentWindowAdaptiveInfo1().isWidthCompact && navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail) {
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
                                        enabled = !state.configurationState.isLoading,
                                    )
                                    MediaSourceConfigurationDefaults.DropdownMenuExport(
                                        state = state.exportState,
                                        onDismissRequest = { showDropdown = false },
                                        enabled = !state.configurationState.isLoading,
                                    )
                                }
                            }

                        },
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        colors = AniThemeDefaults.topAppBarColors(),
                    )
                }
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

        // 在外面启动, 避免在切换页面后重新启动导致刷新
        LaunchedEffect(state) {
            state.episodeState.searcher.observeTestDataChanges(state.episodeState.searcherTestDataState)
        }
        LaunchedEffect(state) {
            state.testState.observeChanges()
        }

        ListDetailPaneScaffold(
            navigator.scaffoldDirective,
            navigator.scaffoldValue,
            listPane = {
                ListDetailAnimatedPane(Modifier.preferredWidth(480.dp)) {
                    SelectorConfigurationPane(
                        state = state.configurationState,
                        Modifier.fillMaxSize().consumeWindowInsets(paddingValues),
                        contentPadding = paddingValues,
                    )
                }
            },
            detailPane = {
                ListDetailAnimatedPane {
                    SelectorTestAndEpisodePane(
                        state = state,
                        layout = episodePaneLayout,
                        modifier = Modifier.consumeWindowInsets(paddingValues),
                        contentPadding = paddingValues,
                    )
                }
            },
            Modifier.materialWindowMarginPadding(),
        )
    }
}

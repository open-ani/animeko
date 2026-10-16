/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceEngine
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiTestEpisode
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailNavState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailPaneLayout
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailRoutes
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorScaffold
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceTestAndDetailPane
import me.him188.ani.app.ui.settings.mediasource.jsonapi.edit.JsonApiConfigState
import me.him188.ani.app.ui.settings.mediasource.jsonapi.edit.JsonApiConfigurationPane
import me.him188.ani.app.ui.settings.mediasource.jsonapi.play.JsonApiPlayPaneContent
import me.him188.ani.app.ui.settings.mediasource.jsonapi.play.JsonApiPlayPaneDefaults
import me.him188.ani.app.ui.settings.mediasource.jsonapi.play.JsonApiPlayState
import me.him188.ani.app.ui.settings.mediasource.jsonapi.test.JsonApiTestPane
import me.him188.ani.app.ui.settings.mediasource.jsonapi.test.JsonApiTestState
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import kotlin.time.Duration.Companion.minutes

class EditJsonApiMediaSourcePageState(
    private val argumentsStorage: SaveableStorage<JsonApiMediaSourceArguments>,
    allowEditState: State<Boolean>,
    engine: JsonApiMediaSourceEngine,
    codecManager: MediaSourceCodecManager,
    backgroundScope: CoroutineScope,
) {
    internal val configurationState: JsonApiConfigState = JsonApiConfigState(
        argumentsStorage,
        allowEditState = allowEditState,
    )

    /**
     * 测试页切到播放详情页时取消订阅; 已取得的响应保留几分钟, 返回时不重新请求 (站点会限流).
     */
    private val tester = JsonApiMediaSourceTester(
        engine,
        sharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5.minutes.inWholeMilliseconds),
    )

    internal val testState: JsonApiTestState = JsonApiTestState(
        configurationState.searchConfigState,
        tester,
        backgroundScope,
    )

    /**
     * 测试页与播放详情页之间的导航.
     */
    val detailNav = MediaSourceDetailNavState<JsonApiTestEpisode>()

    internal val playState: JsonApiPlayState = JsonApiPlayState(detailNav.viewingItemAsState, tester)

    val importState = ImportMediaSourceState<JsonApiMediaSourceArguments>(
        codecManager,
        onImport = { argumentsStorage.set(it) },
    )
    val exportState = ExportMediaSourceState(
        codecManager,
        onExport = { argumentsStorage.container },
    )
}

@Composable
fun EditJsonApiMediaSourceScreen(
    vm: EditJsonApiMediaSourceViewModel,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle(null)
    state?.let {
        EditJsonApiMediaSourceScreen(it, modifier, navigator, windowInsets, navigationIcon)
    }
}

@Composable
fun EditJsonApiMediaSourceScreen(
    state: EditJsonApiMediaSourcePageState,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val detailLayout = MediaSourceDetailPaneLayout.calculate(navigator.scaffoldValue)

    // 在外面启动, 避免在切换页面后重新启动导致刷新
    LaunchedEffect(state) {
        state.testState.observeChanges()
    }
    LaunchedEffect(state) {
        state.playState.observeChanges()
    }

    val viewingItem = state.detailNav.viewingItem
    MediaSourceEditorScaffold(
        title = {
            if (viewingItem != null) {
                Text(viewingItem.presentation.name)
            } else {
                Text(state.configurationState.displayName)
            }
        },
        importState = state.importState,
        exportState = state.exportState,
        modifier,
        navigator = navigator,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
        enableImportExport = !state.configurationState.isLoading,
        topBarOverride = if (viewingItem != null && detailLayout.showTopBarInScaffold) {
            { myNavigationIcon ->
                JsonApiPlayPaneDefaults.TopAppBar(
                    state.playState,
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    navigationIcon = myNavigationIcon,
                )
            }
        } else null,
        listPane = { paddingValues ->
            JsonApiConfigurationPane(
                state = state.configurationState,
                Modifier.fillMaxSize().consumeWindowInsets(paddingValues),
                contentPadding = paddingValues,
            )
        },
        detailPane = { paddingValues ->
            JsonApiTestAndPlayPane(
                state = state,
                layout = detailLayout,
                modifier = Modifier.consumeWindowInsets(paddingValues),
                contentPadding = paddingValues,
            )
        },
    )
}

@Composable
fun JsonApiTestAndPlayPane(
    state: EditJsonApiMediaSourcePageState,
    layout: MediaSourceDetailPaneLayout,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialRoute: MediaSourceDetailRoutes = MediaSourceDetailRoutes.TEST,
) {
    MediaSourceTestAndDetailPane(
        state.detailNav,
        modifier,
        initialRoute = initialRoute,
        testPane = {
            JsonApiTestPane(
                state.testState,
                onViewEpisode = { state.detailNav.view(it) },
                Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )
        },
        detailPane = {
            val onBack: () -> Unit = { state.detailNav.stopViewing() }
            val cardColors: CardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            val content: @Composable () -> Unit = {
                JsonApiPlayPaneContent(
                    state.playState,
                    Modifier.fillMaxSize(),
                    itemColors = ListItemDefaults.colors(containerColor = cardColors.containerColor),
                )
            }
            val topAppBarDecorated = if (layout.showTopBarInPane) {
                {
                    Card(
                        Modifier.fillMaxSize(),
                        colors = cardColors,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        JsonApiPlayPaneDefaults.TopAppBar(
                            state.playState,
                            navigationIcon = {
                                BackNavigationIconButton({ onBack() })
                            },
                        )
                        content()
                    }
                }
            } else content

            val bottomSheetDecorated = if (layout.showBottomSheet) {
                {
                    BottomSheetScaffold(
                        sheetContent = {
                            JsonApiPlayPaneDefaults.ConfigurationContent(
                                state.configurationState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        },
                        Modifier.fillMaxSize(),
                        sheetPeekHeight = 78.dp,
                    ) { paddingValues ->
                        Box(Modifier.padding(paddingValues)) {
                            topAppBarDecorated()
                        }
                    }
                }
            } else topAppBarDecorated

            Box(Modifier.padding(contentPadding)) {
                bottomSheetDecorated()
            }
        },
    )
}

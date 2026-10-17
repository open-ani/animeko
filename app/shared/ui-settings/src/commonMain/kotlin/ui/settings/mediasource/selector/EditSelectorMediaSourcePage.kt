/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.test.web.SelectorMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.TestEpisodePresentation
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailNavState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailPaneLayout
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorScaffold
import me.him188.ani.app.ui.settings.mediasource.observeTestDataChanges
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigState
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigurationPane
import me.him188.ani.app.ui.settings.mediasource.selector.episode.SelectorEpisodePaneDefaults
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
    private val webSessionManager: WebSessionManager,
    testMediaSourceId: String,
    backgroundScope: CoroutineScope,
    context: Context,
    flowDispatcher: CoroutineContext = Dispatchers.Default,
) {
    internal val configurationState: SelectorConfigState = SelectorConfigState(
        argumentsStorage,
        allowEditState = allowEditState,
    )

    internal val testState: SelectorTestState =
        SelectorTestState(
            configurationState.searchConfigState,
            SelectorMediaSourceTester(
                sessionManager = webSessionManager,
                mediaSourceId = testMediaSourceId,
            ),
            backgroundScope,
        )

    /**
     * 测试页与剧集详情页之间的导航.
     */
    val detailNav = MediaSourceDetailNavState<TestEpisodePresentation>()

    internal val episodeState: SelectorEpisodeState = SelectorEpisodeState(
        itemState = detailNav.viewingItemAsState,
        matchVideoConfigState = derivedStateOf { configurationState.searchConfigState.value?.matchVideo },
        userAgentState = derivedStateOf { configurationState.searchConfigState.value?.userAgentOrNull },
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

    fun cancelAutoCaptchaResolutionRequests() {
        webSessionManager.cancelAutoSolves()
    }
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
    val episodePaneLayout = MediaSourceDetailPaneLayout.calculate(navigator.scaffoldValue)
    DisposableEffect(state) {
        onDispose {
            state.cancelAutoCaptchaResolutionRequests()
        }
    }

    // 在外面启动, 避免在切换页面后重新启动导致刷新
    LaunchedEffect(state) {
        state.episodeState.searcher.observeTestDataChanges(state.episodeState.searcherTestDataState)
    }
    LaunchedEffect(state) {
        state.testState.observeChanges()
    }

    val viewingItem = state.detailNav.viewingItem
    MediaSourceEditorScaffold(
        title = {
            if (viewingItem != null) {
                Text(viewingItem.name)
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
        topBarOverride = if (viewingItem != null && episodePaneLayout.showTopBarInScaffold) {
            { myNavigationIcon ->
                SelectorEpisodePaneDefaults.TopAppBar(
                    state.episodeState,
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    navigationIcon = myNavigationIcon,
                )
            }
        } else null,
        listPane = { paddingValues ->
            SelectorConfigurationPane(
                state = state.configurationState,
                Modifier.fillMaxSize().consumeWindowInsets(paddingValues),
                contentPadding = paddingValues,
            )
        },
        detailPane = { paddingValues ->
            SelectorTestAndEpisodePane(
                state = state,
                layout = episodePaneLayout,
                modifier = Modifier.consumeWindowInsets(paddingValues),
                contentPadding = paddingValues,
            )
        },
    )
}

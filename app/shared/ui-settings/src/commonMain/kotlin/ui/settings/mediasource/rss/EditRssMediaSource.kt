/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ktor.http.Url
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceEngine
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.domain.mediasource.rss.RssSearchQuery
import me.him188.ani.app.domain.rss.RssParser
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
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
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_details
import me.him188.ani.app.ui.lang.settings_mediasource_rss_more
import me.him188.ani.app.ui.lang.settings_mediasource_rss_test
import me.him188.ani.app.ui.lang.settings_mediasource_rss_test_data_source
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
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.xml.Xml
import org.jetbrains.compose.resources.stringResource

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
                                ListDetailPaneScaffoldRole.Detail -> Text(stringResource(Lang.settings_mediasource_rss_test_data_source))
                                ListDetailPaneScaffoldRole.Extra -> Text(stringResource(Lang.settings_mediasource_rss_details))
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
                                Text(stringResource(Lang.settings_mediasource_rss_test))
                            }
                        }
                        Box {
                            var showDropdown by remember { mutableStateOf(false) }
                            IconButton({ showDropdown = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(Lang.settings_mediasource_rss_more))
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

@TestOnly
internal object TestRssMediaSourceEngine : RssMediaSourceEngine() {
    private val parsed by lazy {
        Xml.parse(
            """
                <rss version="2.0">
                <channel>
                <title>樱trick</title>
                <description>Anime Garden 是動漫花園資源網的第三方镜像站, 動漫花園資訊網是一個動漫愛好者交流的平台,提供最及時,最全面的動畫,漫畫,動漫音樂,動漫下載,BT,ED,動漫遊戲,資訊,分享,交流,讨论.</description>
                <link>https://garden.breadio.wiki/resources?page=1&amp;pageSize=100&amp;search=%5B%22%E6%A8%B1trick%22%5D</link>
                <item>
                <title>[愛戀&amp;漫猫字幕社]櫻Trick Sakura Trick 01-12 avc_flac mkv 繁體內嵌合集(急招時軸)</title>
                <link>https://garden.breadio.wiki/detail/moe/6558436a88897300074bfd42</link>
                <guid isPermaLink="true">https://garden.breadio.wiki/detail/moe/6558436a88897300074bfd42</guid>
                <pubDate>Sat, 18 Nov 2023 04:54:02 GMT</pubDate>
                <enclosure url="magnet:?xt=urn:btih:d22868eee2dae4214476ac865e0b6ec533e09e57" length="0" type="application/x-bittorrent"/>
                </item>
            """.trimIndent(),
        )
    }

    @Throws(RepositoryException::class, CancellationException::class)
    override suspend fun searchImpl(
        finalUrl: Url,
        config: RssSearchConfig,
        query: RssSearchQuery,
        page: Int?,
        mediaSourceId: String
    ): Result {
        return try {
            val channel = RssParser.parse(parsed, includeOrigin = true)

            Result(
                finalUrl,
                query,
                parsed,
                channel,
                channel?.items?.mapNotNull { convertItemToMedia(it, mediaSourceId) },
            )
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

@Composable
@PreviewLightDark
fun PreviewEditRssMediaSourcePagePhone() = ProvideCompositionLocalsForPreview {
    val (edit, test) = rememberTestEditRssMediaSourceStateAndRssTestPaneState()
    EditRssMediaSourceScreen(edit, test, {})
}

@Composable
@PreviewLightDark
fun PreviewEditRssMediaSourcePagePhoneTest() = ProvideCompositionLocalsForPreview {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val (edit, test) = rememberTestEditRssMediaSourceStateAndRssTestPaneState()
    EditRssMediaSourceScreen(
        edit, test, {},
        navigator = navigator,
    )
    LaunchedEffect(Unit) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }
}

@Composable
@Preview
fun PreviewEditRssMediaSourcePageLaptop() = ProvideCompositionLocalsForPreview {
    val (edit, test) = rememberTestEditRssMediaSourceStateAndRssTestPaneState()
    EditRssMediaSourceScreen(edit, test, {})
}

@TestOnly
@Composable
internal fun rememberTestEditRssMediaSourceStateAndRssTestPaneState(): Pair<EditRssMediaSourceState, RssTestPaneState> {
    val scope = rememberCoroutineScope()
    val edit = rememberTestEditRssMediaSourceState()
    return edit to remember {
        RssTestPaneState(
            derivedStateOf { edit.searchConfig },
            TestRssMediaSourceEngine,
            scope,
        )
    }
}

@TestOnly
@Composable
internal fun rememberTestEditRssMediaSourceState() = remember {
    EditRssMediaSourceState(
        argumentsStorage = SaveableStorage(
            stateOf(RssMediaSourceArguments.Default),
            {},
            MutableStateFlow(false),
        ),
        allowEditState = stateOf(true),
        instanceId = "test-id",
        codecManager = createTestMediaSourceCodecManager(),
    )
}
/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.selector.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.resolver.TestWebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.mediasource.test.TestEpisodePresentation
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_copied
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_actual_play_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_hide_css
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_hide_data
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_hide_images
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_hide_scripts
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_matched
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_multiple_matched_video
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_nested_link
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_no_matched_video
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_not_matched
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_single_matched_video
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailPaneLayout
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceDetailRoutes
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceTestAndDetailPane
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourcePageState
import me.him188.ani.app.ui.settings.mediasource.selector.test.SelectorTestPane
import me.him188.ani.app.ui.settings.mediasource.selector.test.TestSelectorMediaSourceEngine
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun SelectorTestAndEpisodePane(
    state: EditSelectorMediaSourcePageState,
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
            SelectorTestPane(
                state.testState,
                onViewEpisode = { state.detailNav.view(it) },
                Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )
        },
        detailPane = {
            // stopViewing 会把栈弹回 TEST
            val onBack: () -> Unit = { state.detailNav.stopViewing() }
            val cardColors: CardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            // decorate
            val content: @Composable () -> Unit = {
                SelectorEpisodePaneContent(
                    state.episodeState,
                    Modifier.fillMaxSize(),
                    itemColors = ListItemDefaults.colors(containerColor = cardColors.containerColor),
                )
            }
            val topAppBarDecorated = if (layout.showTopBarInPane) {
                {
                    // list 展开, 能编辑配置
                    Card(
                        Modifier.fillMaxSize(),
                        colors = cardColors,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        SelectorEpisodePaneDefaults.TopAppBar(
                            state.episodeState,
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
                            SelectorEpisodePaneDefaults.ConfigurationContent(
                                state.configurationState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        },
                        Modifier
                            .fillMaxSize(),
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

@Composable
fun SelectorEpisodePaneContent(
    state: SelectorEpisodeState,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    horizontalPadding: Dp = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding,
    itemColors: ListItemColors = ListItemDefaults.colors(),
) {
    Column(modifier) {
        Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
            FastLinearProgressIndicator(
                state.isSearchingInProgress.collectAsStateWithLifecycle().value,
                delayMillis = 0,
                minimumDurationMillis = 300,
            )
        }

        val list by state.rawMatchResults.collectAsStateWithLifecycle(emptyList())

        Row(
            Modifier.padding(
                start = horizontalPadding, end = horizontalPadding,
                top = 20.dp,
                bottom = 20.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val matchedVideoSize by remember {
                derivedStateOf {
                    list.count { it.isMatchedVideo() }
                }
            }
            val noMatchedVideoText = stringResource(
                Lang.settings_mediasource_selector_episode_no_matched_video,
                list.size,
            )
            val singleMatchedVideoText = stringResource(
                Lang.settings_mediasource_selector_episode_single_matched_video,
                list.size,
                matchedVideoSize,
            )
            val multipleMatchedVideoText = stringResource(
                Lang.settings_mediasource_selector_episode_multiple_matched_video,
                list.size,
                matchedVideoSize,
            )
            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                when (matchedVideoSize) {
                    0 -> {
                        Icon(
                            Icons.Rounded.PriorityHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(noMatchedVideoText)
                    }

                    1 -> {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(singleMatchedVideoText)
                    }

                    else -> {
                        Icon(
                            Icons.Rounded.PriorityHigh,
                            contentDescription = null,
                            tint = Color.Yellow.compositeOver(MaterialTheme.colorScheme.error),
                        )
                        Text(multipleMatchedVideoText)
                    }
                }
            }
        }

        val hideImagesText = stringResource(Lang.settings_mediasource_selector_episode_hide_images)
        val hideCssText = stringResource(Lang.settings_mediasource_selector_episode_hide_css)
        val hideScriptsText = stringResource(Lang.settings_mediasource_selector_episode_hide_scripts)
        val hideDataText = stringResource(Lang.settings_mediasource_selector_episode_hide_data)
        FlowRow(
            Modifier.padding(horizontal = horizontalPadding).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilterChip(
                selected = state.hideImages,
                { state.hideImages = !state.hideImages },
                label = { Text(hideImagesText) },
                leadingIcon = { if (state.hideImages) Icon(Icons.Rounded.Check, null) },
            )
            FilterChip(
                selected = state.hideCss,
                { state.hideCss = !state.hideCss },
                label = { Text(hideCssText) },
                leadingIcon = { if (state.hideCss) Icon(Icons.Rounded.Check, null) },
            )
            FilterChip(
                selected = state.hideScripts,
                { state.hideScripts = !state.hideScripts },
                label = { Text(hideScriptsText) },
                leadingIcon = { if (state.hideScripts) Icon(Icons.Rounded.Check, null) },
            )
            FilterChip(
                selected = state.hideData,
                { state.hideData = !state.hideData },
                label = { Text(hideDataText) },
                leadingIcon = { if (state.hideData) Icon(Icons.Rounded.Check, null) },
            )
        }

        val filteredList by state.filteredResults.collectAsStateWithLifecycle(emptyList())
        val copiedText = stringResource(Lang.settings_mediasource_copied)
        val nestedLinkText = stringResource(Lang.settings_mediasource_selector_episode_nested_link)
        val matchedText = stringResource(Lang.settings_mediasource_selector_episode_matched)
        val notMatchedText = stringResource(Lang.settings_mediasource_selector_episode_not_matched)

        LazyColumn(
            contentPadding = PaddingValues(
                bottom = itemSpacing,
                start = horizontalPadding - 8.dp, end = horizontalPadding,
            ),
        ) {
            // 上面总是有个东西可以保证当后面加载到匹配 (置顶) 时, 看到的是那个被匹配到的
            item { Spacer(Modifier.height(1.dp)) }

            for (matchResult in filteredList) {
                item(key = matchResult.key) {
                    val toaster = LocalToaster.current
                    val clipboard = LocalClipboard.current
                    val scope = rememberCoroutineScope()
                    ListItem(
                        headlineContent = {
                            Text(
                                matchResult.originalUrl,
                                color = if (matchResult.highlight)
                                    MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        },
                        Modifier.animateItem()
                            .clickable {
                                scope.launch {
                                    clipboard.setClipEntryText(matchResult.originalUrl)
                                    toaster.toast(copiedText)
                                }
                            },
                        supportingContent = {
                            val m3u8 = matchResult.video?.m3u8Url
                            when {
                                m3u8 != null && m3u8 != matchResult.originalUrl -> {
                                    Text(
                                        stringResource(
                                            Lang.settings_mediasource_selector_episode_actual_play_url,
                                            m3u8,
                                        ),
                                    )
                                }

                                matchResult.webUrl.didLoadNestedPage -> {
                                    Text(nestedLinkText)
                                }
                            }
                        },
                        colors = itemColors,
                        leadingContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                when {
                                    matchResult.highlight -> {
                                        Icon(Icons.Rounded.Check, matchedText, tint = MaterialTheme.colorScheme.primary)
                                    }

                                    else -> {
                                        Icon(Icons.Rounded.Close, notMatchedText)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}


@Composable
@Preview
fun PreviewSelectorEpisodePaneCompact() = ProvideCompositionLocalsForPreview {
    Surface {
        val state = rememberTestEditSelectorMediaSourceState(
            SelectorSearchConfig.MatchVideoConfig(),
        )
        SelectorTestAndEpisodePane(
            state = state,
            layout = MediaSourceDetailPaneLayout.Compact,
        )
        SideEffect {
            state.detailNav.view(SampleTestEpisodePresentations[0])
        }
    }
}

@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSelectorEpisodePaneExpanded() {
    ProvideCompositionLocalsForPreview {
        Surface {
            SelectorTestAndEpisodePane(
                state = rememberTestEditSelectorMediaSourceState(),
                layout = MediaSourceDetailPaneLayout.Expanded,
                initialRoute = MediaSourceDetailRoutes.DETAIL,
            )
        }
    }
}

@TestOnly
@Stable
internal val SampleTestEpisodePresentations
    get() = listOf(
        TestEpisodePresentation(
            channel = null,
            name = "Test Episode 2",
            episodeSort = EpisodeSort(2),
            playUrl = "https://example.com",
            tags = buildMatchTags {
                emit("EP: 02", isMatch = true)
                emit("https://example.com", isMatch = true)
            },
            origin = null,
        ),
        TestEpisodePresentation(
            channel = null,
            name = "Test Episode Unknown",
            episodeSort = null,
            playUrl = "https://example.com",
            tags = buildMatchTags {
                emit("缺失 EP", isMissing = true)
            },
            origin = null,
        ),
    )

@TestOnly
@Composable
internal fun rememberTestSelectorEpisodeState(
    item: TestEpisodePresentation? = SampleTestEpisodePresentations[0],
    config: SelectorSearchConfig.MatchVideoConfig = SelectorSearchConfig.MatchVideoConfig(),
    urls: (pageUrl: String) -> List<String> = {
        listOf("https://example.com/a.mkv")
    },
): SelectorEpisodeState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember {
        SelectorEpisodeState(
            itemState = stateOf(item),
            matchVideoConfigState = stateOf(config),
            userAgentState = stateOf(null),
            webViewVideoExtractor = stateOf(TestWebViewVideoExtractor(urls)),
            engine = TestSelectorMediaSourceEngine(),
            backgroundScope = scope,
            flowDispatcher = EmptyCoroutineContext,
            context = context,
        )
    }
}

@TestOnly
@Composable
internal fun rememberTestEditSelectorMediaSourceState(
    matchVideoConfig: SelectorSearchConfig.MatchVideoConfig = SelectorSearchConfig.MatchVideoConfig(),
    urls: (pageUrl: String) -> List<String> = {
        listOf("https://example.com/a.mkv")
    },
): EditSelectorMediaSourcePageState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        EditSelectorMediaSourcePageState(
            createTestSaveableStorage(
                SelectorMediaSourceArguments.Default.run {
                    copy(
                        searchConfig = searchConfig.copy(matchVideo = matchVideoConfig),
                    )
                },
            ),
            allowEditState = stateOf(true),
            engine = TestSelectorMediaSourceEngine(),
            webViewVideoExtractor = stateOf(TestWebViewVideoExtractor(urls)),
            codecManager = createTestMediaSourceCodecManager(),
            webSessionManager = createTestWebSessionManager(scope),
            testMediaSourceId = "preview-selector-test",
            backgroundScope = scope,
            context = context,
            flowDispatcher = EmptyCoroutineContext,
        )
    }
}

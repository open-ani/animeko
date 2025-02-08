/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.episode

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.domain.media.resolver.TestWebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodePresentation
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourcePageState
import me.him188.ani.app.ui.settings.mediasource.selector.test.TestSelectorMediaSourceEngine
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(TestOnly::class)
@Composable
@Preview
fun PreviewSelectorEpisodePaneCompact() = ProvideCompositionLocalsForPreview {
    Surface {
        val state = rememberTestEditSelectorMediaSourceState(
            SelectorSearchConfig.MatchVideoConfig(),
        )
        SelectorTestAndEpisodePane(
            state = state,
            layout = SelectorEpisodePaneLayout.Compact,
        )
        SideEffect {
            state.viewEpisode(TestSelectorTestEpisodePresentations[0])
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSelectorEpisodePaneExpanded() {
    ProvideCompositionLocalsForPreview {
        Surface {
            SelectorTestAndEpisodePane(
                state = rememberTestEditSelectorMediaSourceState(),
                layout = SelectorEpisodePaneLayout.Expanded,
                initialRoute = SelectorEpisodePaneRoutes.EPISODE,
            )
        }
    }
}

@TestOnly
@Stable
internal val TestSelectorTestEpisodePresentations
    get() = listOf(
        SelectorTestEpisodePresentation(
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
        SelectorTestEpisodePresentation(
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
    item: SelectorTestEpisodePresentation? = TestSelectorTestEpisodePresentations[0],
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
            backgroundScope = scope,
            context,
            flowDispatcher = EmptyCoroutineContext,
        )
    }
}
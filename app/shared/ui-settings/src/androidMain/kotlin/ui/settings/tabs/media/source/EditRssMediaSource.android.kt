/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.ktor.http.Url
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceEngine
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.domain.mediasource.rss.RssSearchQuery
import me.him188.ani.app.domain.rss.RssParser
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceScreen
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPaneState
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.xml.Xml

@TestOnly
internal object TestRssMediaSourceEngine : RssMediaSourceEngine() {
    private val parsed by lazy {
        Xml.parse(
            """
                <rss version="2.0">
                <channel>
                <title>樱trick</title>
                <description>Anime Garden 是動漫花園資源網的第三方镜像站, 動漫花園資訊網是一個動漫愛好者交流的平台,提供最及時,最全面的動畫,漫畫,動漫音樂,動漫下載,BT,ED,動漫遊戲,資訊,分享,交流,讨论.</description>
                <link>https://garden.breadio.wiki/resources?page=1&pageSize=100&search=%5B%22%E6%A8%B1trick%22%5D</link>
                <item>
                <title>[愛戀&漫猫字幕社]櫻Trick Sakura Trick 01-12 avc_flac mkv 繁體內嵌合集(急招時軸)</title>
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
                channel.items.mapNotNull { convertItemToMedia(it, mediaSourceId) },
            )
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewEditRssMediaSourcePagePhone() = ProvideCompositionLocalsForPreview {
    val (edit, test) = rememberTestEditRssMediaSourceStateAndRssTestPaneState()
    EditRssMediaSourceScreen(edit, test, {})
}

@OptIn(TestOnly::class)
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

@OptIn(TestOnly::class)
@Composable
@Preview(device = Devices.PIXEL_TABLET)
@Preview(device = Devices.PIXEL_TABLET, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
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

@TestOnly
internal val TestEditMediaSourceModeAdd get() = EditMediaSourceMode.Add(FactoryId("RSS"))


/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source.rss.test

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.mediasource.test.MatchTag
import me.him188.ani.app.domain.mediasource.test.rss.RssItemInfo
import me.him188.ani.app.domain.rss.RssChannel
import me.him188.ani.app.domain.rss.RssEnclosure
import me.him188.ani.app.domain.rss.RssItem
import me.him188.ani.app.ui.foundation.preview.PreviewTabletLightDark
import me.him188.ani.app.ui.settings.mediasource.rss.test.OverviewTab
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPaneDefaults
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestResult
import me.him188.ani.datasources.api.topic.titles.ParsedTopicTitle
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Clock

@TestOnly
internal val TestRssChannel
    get() = RssChannel(
        title = "Title",
        description = "Description",
        link = "Link",
        ttl = 10,
        items = TestRssItems,
    )

@TestOnly
internal val TestRssItems
    get() = listOf(
        RssItem(
            title = "Title",
            description = "Description",
            pubDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            link = "Link",
            guid = "GUID",
            enclosure = RssEnclosure("https://example.com", type = "application/x-bittorrent"),
        ),
        RssItem(
            title = "Title",
            description = "Description",
            pubDate = LocalDateTime(2024, 7, 1, 1, 1, 1),
            link = "Link",
            guid = "GUID",
            enclosure = RssEnclosure("https://example.com", type = "application/x-bittorrent"),
        ),
    )

@TestOnly
internal val TestRssItemInfos
    get() = listOf(
        RssItemInfo(
            RssItem(
                title = "Title",
                description = "Description",
                pubDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                link = "Link",
                guid = "GUID",
                enclosure = RssEnclosure("https://example.com", type = "application/x-bittorrent"),
            ),
            parsed = ParsedTopicTitle(),
            tags = listOf(
                MatchTag("01..02", isMatch = false),
                MatchTag("1080P"),
            ),
        ),
        RssItemInfo(
            RssItem(
                title = "Title",
                description = "Description",
                pubDate = LocalDateTime(2024, 7, 1, 1, 1, 1),
                link = "Link",
                guid = "GUID",
                enclosure = RssEnclosure("https://example.com", type = "application/x-bittorrent"),
            ),
            parsed = ParsedTopicTitle(),
            tags = listOf(
                MatchTag("01..02", isMatch = false),
                MatchTag("1080P"),
            ),
        ),
    )

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
@PreviewTabletLightDark
private fun PreviewOverviewTab() {
    Surface(color = Color.Gray) {
        RssTestPaneDefaults.OverviewTab(
            remember {
                RssTestResult.Success(
                    "https://example.com",
                    TestRssChannel,
                    TestRssItemInfos,
                    TestMediaList,
                    null,
                )
            },
        )

    }
}

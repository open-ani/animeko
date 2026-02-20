/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.rss.test

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.mediasource.test.MatchTag
import me.him188.ani.app.domain.mediasource.test.rss.RssItemInfo
import me.him188.ani.app.domain.rss.RssChannel
import me.him188.ani.app.domain.rss.RssEnclosure
import me.him188.ani.app.domain.rss.RssItem
import me.him188.ani.app.ui.foundation.interaction.onRightClickIfSupported
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied_to_clipboard
import me.him188.ani.datasources.api.topic.titles.ParsedTopicTitle
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
@Suppress("UnusedReceiverParameter")
fun RssTestPaneDefaults.OverviewTab(
    result: RssTestResult.Success,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    RssOverviewCard(
        result,
        modifier,
        contentPadding = PaddingValues(16.dp),
        state = state,
    )
}


@Composable
fun RssOverviewCard(
    result: RssTestResult.Success,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer),
    ),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyGridState = rememberLazyGridState(),
) {
    val channel = result.channel
    val encodedUrl = result.encodedUrl
    Card(modifier, colors = colors, shape = MaterialTheme.shapes.large) {
        val toaster = LocalToaster.current
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val textCopied = stringResource(Lang.settings_mediasource_rss_copied_to_clipboard)
        val copy = { str: String ->
            scope.launch {
                clipboard.setClipEntryText(str)
                toaster.toast(textCopied)
            }
        }

        fun Modifier.copyable(value: () -> String): Modifier {
            val func: () -> Unit = { copy(value()) }
            return combinedClickable(
                onLongClick = func,
                onLongClickLabel = "复制",
                onClick = func, // no-op
                onClickLabel = "复制",
            ).onRightClickIfSupported(onClick = func)
        }

        val listItemColors = ListItemDefaults.colors(
            containerColor = colors.containerColor,
        )

        LazyVerticalGrid(
            GridCells.Adaptive(minSize = 300.dp),
            state = state,
            contentPadding = contentPadding,
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Encoded Query URL") },
                    Modifier
                        .copyable { encodedUrl },
                    supportingContent = { Text(encodedUrl) },
                    colors = listItemColors,
                )
            }
            item {
                val url = encodedUrl.runCatching { decodeURLQueryComponent() }
                    .getOrElse { encodedUrl }
                ListItem(
                    headlineContent = { Text("Query URL") },
                    Modifier
                        .copyable { url },
                    supportingContent = { Text(url) },
                    colors = listItemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Title") },
                    Modifier.copyable { channel.title },
                    supportingContent = { Text(channel.title) },
                    colors = listItemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Description") },
                    Modifier.copyable { channel.description },
                    supportingContent = { Text(channel.description) },
                    colors = listItemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Link") },
                    Modifier
                        .copyable { channel.link },
                    supportingContent = { Text(channel.link) },
                    colors = listItemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("RSS Item Count") },
                    Modifier,
                    supportingContent = { SelectionContainer { Text(channel.items.size.toString()) } },
                    colors = listItemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Parsed Media Count") },
                    Modifier,
                    supportingContent = { SelectionContainer { Text(result.mediaList.size.toString()) } },
                    colors = listItemColors,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ListItem(
                    headlineContent = { Text("原始 XML") },
                    Modifier.copyable { result.originString },
                    supportingContent = {
                        if (result.origin == null) {
                            Text("不可用")
                        } else {
                            OutlinedTextField(
                                value = remember(result.originString) {
                                    result.originString.lineSequence().take(4).joinToString("\n")
                                },
                                onValueChange = {},
                                Modifier.padding(vertical = 8.dp),
                                readOnly = true,
                                minLines = 1,
                                maxLines = 4,
                            )
                        }
                    },
                    colors = listItemColors,
                )
            }
        }
    }
}

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

@Preview
@Composable
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

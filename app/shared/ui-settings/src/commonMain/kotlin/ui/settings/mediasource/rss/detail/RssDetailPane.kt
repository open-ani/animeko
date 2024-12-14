/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.test.rss.RssItemInfo
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.mediasource.rss.test.subtitleLanguageRendered
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.isSingleEpisode

@Composable
fun SideSheetPane(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Row(Modifier.padding(all = 16.dp)) {
                Text(
                    "详情",
                    Modifier.weight(1f).align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IconButton(onClose, Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            content()
        }
    }
}

@Composable
fun RssDetailPane(
    item: RssViewingItem,
    mediaDetailsColumn: @Composable (Media) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(modifier.background(ListItemDefaults.containerColor).padding(contentPadding)) {
        when (item) {
            is RssViewingItem.ViewingMedia ->
                mediaDetailsColumn(item.value)

            is RssViewingItem.ViewingRssItem -> RssItemDetailColumn(
                item.value,
                Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RssItemDetailColumn(
    item: RssItemInfo,
    modifier: Modifier = Modifier,
) {
    val browser = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 300.dp),
        modifier,
    ) {
        val copyContent = @Composable { value: () -> String ->
            val toaster = LocalToaster.current
            IconButton(
                {
                    clipboard.setText(AnnotatedString(value()))
                    toaster.toast("已复制")
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
            }
        }
        val browseContent = @Composable { url: () -> String ->
            IconButton({ browser.openUri(url()) }) {
                Icon(Icons.Rounded.ArrowOutward, contentDescription = "打开链接")
            }
        }

        item {
            ListItem(
                headlineContent = { SelectionContainer { Text(item.rss.title) } },
                trailingContent = { copyContent { item.rss.title } },
            )
        }
        if (item.rss.description.isNotBlank()) {
            item {
                ListItem(
                    headlineContent = { Text("描述") },
                    supportingContent = { SelectionContainer { Text(item.rss.description, maxLines = 4) } },
                    trailingContent = { copyContent { item.rss.description } },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text("剧集范围") },
                leadingContent = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                supportingContent = {
                    val range = item.parsed.episodeRange
                    SelectionContainer {
                        Text(
                            when {
                                range == null -> "未知"
                                range.isSingleEpisode() -> range.knownSorts.firstOrNull().toString()
                                else -> range.toString()
                            },
                        )
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("分辨率") },
                leadingContent = { Icon(Icons.Rounded.Hd, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(item.parsed.resolution?.displayName ?: "未知") } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("字幕语言") },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(item.subtitleLanguageRendered) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("发布时间") },
                leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(item.rss.pubDate?.let { formatDateTime(it) } ?: "未知")
                    }
                },
                trailingContent = { copyContent { item.rss.title } },
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            ListItem(
                headlineContent = { Text("link") },
                supportingContent = {
                    SelectionContainer { Text(item.rss.link) }
                },
                trailingContent = { browseContent { item.rss.link } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("guid") },
                supportingContent = {
                    SelectionContainer { Text(item.rss.guid) }
                },
                trailingContent = { browseContent { item.rss.guid } },
            )
        }
        item.rss.enclosure?.let { enclosure ->
            item {
                ListItem(
                    headlineContent = { Text("enclosure.url") },
                    supportingContent = { SelectionContainer { Text(enclosure.url) } },
                    trailingContent = { copyContent { enclosure.url } },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("enclosure.type") },
                    supportingContent = { SelectionContainer { Text(enclosure.type) } },
                    trailingContent = { copyContent { enclosure.type } },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text("原始 XML") },
                supportingContent = {
                    if (item.rss.origin == null) {
                        Text("不可用")
                    } else {
                        OutlinedTextField(
                            value = remember(item) {
                                item.rss.origin.toString()
                            },
                            onValueChange = {},
                            Modifier.padding(vertical = 8.dp),
                            readOnly = true,
                            minLines = 2,
                            maxLines = 8,
                        )
                    }
                },
                trailingContent = item.rss.origin?.let {
                    { copyContent { it.toString() } }
                },
            )
        }
    }
}
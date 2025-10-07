/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Event
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.test.rss.RssItemInfo
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_close
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copy_content
import me.him188.ani.app.ui.lang.settings_mediasource_rss_description
import me.him188.ani.app.ui.lang.settings_mediasource_rss_details
import me.him188.ani.app.ui.lang.settings_mediasource_rss_enclosure_type
import me.him188.ani.app.ui.lang.settings_mediasource_rss_enclosure_url
import me.him188.ani.app.ui.lang.settings_mediasource_rss_episode_range
import me.him188.ani.app.ui.lang.settings_mediasource_rss_guid
import me.him188.ani.app.ui.lang.settings_mediasource_rss_link_label
import me.him188.ani.app.ui.lang.settings_mediasource_rss_not_available
import me.him188.ani.app.ui.lang.settings_mediasource_rss_open_link
import me.him188.ani.app.ui.lang.settings_mediasource_rss_original_xml
import me.him188.ani.app.ui.lang.settings_mediasource_rss_publish_time
import me.him188.ani.app.ui.lang.settings_mediasource_rss_resolution
import me.him188.ani.app.ui.lang.settings_mediasource_rss_subtitle_language
import me.him188.ani.app.ui.lang.settings_mediasource_rss_unknown
import me.him188.ani.app.ui.settings.mediasource.rss.test.subtitleLanguageRendered
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.isSingleEpisode
import org.jetbrains.compose.resources.stringResource

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
                    stringResource(Lang.settings_mediasource_rss_details),
                    Modifier.weight(1f).align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IconButton(onClose, Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Lang.settings_mediasource_rss_close))
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    // Load string resources in composable context
    val copiedText = stringResource(Lang.settings_mediasource_rss_copied)
    val unknownText = stringResource(Lang.settings_mediasource_rss_unknown)

    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 300.dp),
        modifier,
    ) {
        val copyContent = @Composable { value: () -> String ->
            IconButton(
                {
                    scope.launch {
                        clipboard.setClipEntryText(value())
                        toaster.toast(copiedText)
                    }
                },
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(Lang.settings_mediasource_rss_copy_content),
                )
            }
        }
        val browseContent = @Composable { url: () -> String ->
            IconButton({ browser.openUri(url()) }) {
                Icon(
                    Icons.Rounded.ArrowOutward,
                    contentDescription = stringResource(Lang.settings_mediasource_rss_open_link),
                )
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
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_description)) },
                    supportingContent = { SelectionContainer { Text(item.rss.description, maxLines = 4) } },
                    trailingContent = { copyContent { item.rss.description } },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_episode_range)) },
                leadingContent = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                supportingContent = {
                    val range = item.parsed.episodeRange
                    SelectionContainer {
                        Text(
                            when {
                                range == null -> unknownText
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
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_resolution)) },
                leadingContent = { Icon(Icons.Outlined.Hd, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(item.parsed.resolution?.displayName ?: unknownText) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_subtitle_language)) },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(item.subtitleLanguageRendered) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_publish_time)) },
                leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(item.rss.pubDate?.let { formatDateTime(it) } ?: unknownText)
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
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_link_label)) },
                supportingContent = {
                    SelectionContainer { Text(item.rss.link) }
                },
                trailingContent = { browseContent { item.rss.link } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_guid)) },
                supportingContent = {
                    SelectionContainer { Text(item.rss.guid) }
                },
                trailingContent = { browseContent { item.rss.guid } },
            )
        }
        item.rss.enclosure?.let { enclosure ->
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_enclosure_url)) },
                    supportingContent = { SelectionContainer { Text(enclosure.url) } },
                    trailingContent = { copyContent { enclosure.url } },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_enclosure_type)) },
                    supportingContent = { SelectionContainer { Text(enclosure.type) } },
                    trailingContent = { copyContent { enclosure.type } },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.settings_mediasource_rss_original_xml)) },
                supportingContent = {
                    if (item.rss.origin == null) {
                        Text(stringResource(Lang.settings_mediasource_rss_not_available))
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

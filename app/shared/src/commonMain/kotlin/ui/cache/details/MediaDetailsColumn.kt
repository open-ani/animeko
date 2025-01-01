/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.details

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.http.Url
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.media.MediaDetailsRenderer
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.isSingleEpisode


@Composable
fun MediaDetailsLazyGrid(
    media: Media,
    sourceInfo: MediaSourceInfo?,
    modifier: Modifier = Modifier,
    showSourceInfo: Boolean = true,
) {
    val browser = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current


    val toaster = LocalToaster.current
    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 300.dp),
        modifier,
    ) {
        val copyContent = @Composable { value: () -> String ->
            IconButton(
                {
                    clipboard.setText(AnnotatedString(value()))
                    toaster.toast("已复制")
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
            }
        }
        val browseContent = @Composable { url: String ->
            IconButton(
                {
                    if (runCatching { Url(url) }.isSuccess) {
                        browser.openUri(url)
                    } else {
                        clipboard.setText(AnnotatedString(url))
                        toaster.toast("已复制")
                    }
                },
            ) {
                Icon(Icons.Rounded.ArrowOutward, contentDescription = "打开链接")
            }
        }

        item {
            ListItem(
                headlineContent = { SelectionContainer { Text(media.originalTitle) } },
                trailingContent = { copyContent { media.originalTitle } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("剧集范围") },
                leadingContent = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                supportingContent = {
                    val range = media.episodeRange
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
        if (showSourceInfo) {
            item {
                ListItem(
                    headlineContent = { Text("数据源") },
                    leadingContent = { MediaSourceIcon(sourceInfo, Modifier.size(24.dp)) },
                    supportingContent = {
                        val kind = when (media.kind) {
                            MediaSourceKind.WEB -> "在线"
                            MediaSourceKind.BitTorrent -> "BT"
                            MediaSourceKind.LocalCache -> "本地"
                        }
                        SelectionContainer { Text("[$kind] ${sourceInfo?.displayName ?: "未知"}") }
                    },
                    trailingContent = kotlin.run {
                        val originalUrl by rememberUpdatedState(media.originalUrl)
                        val isUrlLegal by remember {
                            derivedStateOf {
                                originalUrl.startsWith("http://", ignoreCase = true)
                                        || originalUrl.startsWith("https://", ignoreCase = true)
                            }
                        }
                        if (isUrlLegal) {
                            {
                                browseContent(originalUrl)
                            }
                        } else {
                            {
                                copyContent { originalUrl }
                            }
                        }
                    },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text("字幕组") },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(media.properties.alliance) } },
                trailingContent = { copyContent { media.properties.alliance } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("字幕语言") },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(
                            remember(media) {
                                MediaDetailsRenderer.renderSubtitleLanguages(
                                    media.properties.subtitleKind,
                                    media.properties.subtitleLanguageIds,
                                )
                            },
                        )
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("发布时间") },
                leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(formatDateTime(media.publishedTime)) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("分辨率") },
                leadingContent = { Icon(Icons.Rounded.Hd, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(media.properties.resolution) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("文件大小") },
                leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        if (media.properties.size == FileSize.Unspecified) {
                            Text("未知")
                        } else {
                            Text(media.properties.size.toString())
                        }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("原始下载方式") },
                leadingContent = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(media.download.contentUri, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                trailingContent = { copyContent { media.download.contentUri } },
            )
        }
        media.extraFiles.subtitles.forEachIndexed { index, subtitle ->
            item {
                ListItem(
                    headlineContent = {
                        SelectionContainer {
                            Text(
                                remember(subtitle) {
                                    buildString {
                                        append("外挂字幕 ${index + 1}")
                                        subtitle.language?.let {
                                            append(": ")
                                            append(it)
                                        }
                                    }
                                },
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.FilePresent, contentDescription = null) },
                    supportingContent = { SelectionContainer { Text(subtitle.uri) } },
                    trailingContent = { browseContent(subtitle.uri) },
                )
            }
        }
    }
}

@Stable
private val ResourceLocation.contentUri: String
    get() = when (this) {
        is ResourceLocation.HttpStreamingFile -> this.uri
        is ResourceLocation.HttpTorrentFile -> this.uri
        is ResourceLocation.LocalFile -> this.filePath
        is ResourceLocation.MagnetLink -> this.uri
        is ResourceLocation.WebVideo -> this.uri
    }

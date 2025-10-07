/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.features.getComponentAccessors
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.media.MediaDetailsRenderer
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.isSingleEpisode
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
data class MediaDetails(
    val originalTitle: String,
    val episodeRange: EpisodeRange?,
    val kind: MediaSourceKind,
    val originalUrl: String,
    val properties: MediaProperties,
    val fileSize: FileSize,
    val publishedTimeMillis: Long,
    /**
     * 来源地址, 如 HTML 页面
     */
    val contentOriginalUri: String,
    val fileType: ResourceLocation.LocalFile.FileType?,
    /**
     * 实际下载连接, 如 m3u8
     */
    val contentDownloadUri: String?,
    /**
     * 如果此资源已缓存, 则为本地路径
     */
    val localCacheFilePath: Path?,
    val extraFiles: MediaExtraFiles,
    val totalSegments: Int? = null,
    val downloaderStatus: String? = null,
    val sourceInfo: MediaSourceInfo?,
) {
    val isUrlLegal = originalUrl.startsWith("http://", ignoreCase = true)
            || originalUrl.startsWith("https://", ignoreCase = true)

    companion object {
        fun from(
            originalMedia: Media,
            sourceInfo: MediaSourceInfo?,
            cachedMedia: CachedMedia?,
        ): MediaDetails {
            val originalUri = when (val download = originalMedia.download) {
                is ResourceLocation.HttpStreamingFile -> download.uri
                is ResourceLocation.HttpTorrentFile -> download.uri
                is ResourceLocation.LocalFile -> download.filePath
                is ResourceLocation.MagnetLink -> download.uri
                is ResourceLocation.WebVideo -> download.uri
            }
            val fileType = when (val download = cachedMedia?.download ?: originalMedia.download) {
                is ResourceLocation.HttpStreamingFile -> null
                is ResourceLocation.HttpTorrentFile -> null
                is ResourceLocation.LocalFile -> download.fileType
                is ResourceLocation.MagnetLink -> null
                is ResourceLocation.WebVideo -> null
            }
            val contentDownloadUri = when (val download = cachedMedia?.download) {
                is ResourceLocation.LocalFile -> download.originalUri
                is ResourceLocation.HttpStreamingFile,
                is ResourceLocation.HttpTorrentFile,
                is ResourceLocation.MagnetLink,
                is ResourceLocation.WebVideo,
                null -> null
            }
            val localCacheFilePath = when (val download = cachedMedia?.download) {
                is ResourceLocation.LocalFile -> Path(download.filePath)
                is ResourceLocation.HttpStreamingFile,
                is ResourceLocation.HttpTorrentFile,
                is ResourceLocation.MagnetLink,
                is ResourceLocation.WebVideo,
                null -> null
            }

            return MediaDetails(
                originalTitle = originalMedia.originalTitle,
                episodeRange = originalMedia.episodeRange,
                kind = originalMedia.kind,
                originalUrl = originalMedia.originalUrl,
                properties = originalMedia.properties,
                fileSize = cachedMedia?.properties?.size ?: originalMedia.properties.size,
                publishedTimeMillis = originalMedia.publishedTime,
                contentOriginalUri = originalUri,
                fileType = fileType,
                contentDownloadUri = contentDownloadUri,
                localCacheFilePath = localCacheFilePath,
                extraFiles = originalMedia.extraFiles,
                totalSegments = cachedMedia?.cacheProperties?.totalSegments,
                downloaderStatus = cachedMedia?.cacheProperties?.httpDownloaderStatus,
                sourceInfo = sourceInfo,
            )
        }
    }
}

@Composable
fun MediaDetailsLazyGrid(
    details: MediaDetails,
    modifier: Modifier = Modifier,
    showSourceInfo: Boolean = true,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val fileRevealer = LocalContext.current.getComponentAccessors().fileRevealer
    val scope = rememberCoroutineScope()

    val toaster = LocalToaster.current
    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 500.dp),
        modifier,
    ) {
        val copyContent = @Composable { value: () -> String ->
            IconButton(
                {
                    scope.launch {
                        clipboard.setClipEntryText(value())
                        toaster.toast("已复制")
                    }
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
            }
        }
        val browseContent = @Composable { url: String ->
            IconButton(
                {
                    if (runCatching { Url(url) }.isSuccess) {
                        uriHandler.openUri(url)
                    } else {
                        scope.launch {
                            clipboard.setClipEntryText(url)
                            toaster.toast("已复制")
                        }
                    }
                },
            ) {
                Icon(Icons.Rounded.ArrowOutward, contentDescription = "打开链接")
            }
        }
        val browseFile = @Composable { url: Path ->
            if (fileRevealer == null) {
                // noop
            } else {
                IconButton(
                    {
                        scope.launch {
                            if (!fileRevealer.revealFile(url)) {
                                toaster.toast("打开文件失败 ${url.inSystem.absolutePath}")
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = "浏览文件")
                }
            }
        }
        val placeholderLeadingContent = @Composable { Spacer(Modifier.size(24.dp)) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListItem(
                headlineContent = {
                    SelectionContainer {
                        Text(
                            details.originalTitle,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                trailingContent = { copyContent { details.originalTitle } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("剧集范围") },
                leadingContent = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                supportingContent = {
                    val range = details.episodeRange
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
                    leadingContent = { MediaSourceIcon(details.sourceInfo, Modifier.size(24.dp)) },
                    supportingContent = {
                        val kind = when (details.kind) {
                            MediaSourceKind.WEB -> "在线"
                            MediaSourceKind.BitTorrent -> "BT"
                            MediaSourceKind.LocalCache -> "本地"
                        }
                        SelectionContainer { Text("[$kind] ${details.sourceInfo?.displayName ?: "未知"}") }
                    },
                    trailingContent = kotlin.run {
                        val originalUrl by rememberUpdatedState(details.originalUrl)
                        if (details.isUrlLegal) {
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
                supportingContent = { SelectionContainer { Text(details.properties.alliance) } },
                trailingContent = { copyContent { details.properties.alliance } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("字幕语言") },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(
                            remember(details) {
                                MediaDetailsRenderer.renderSubtitleLanguages(
                                    details.properties.subtitleKind,
                                    details.properties.subtitleLanguageIds,
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
                supportingContent = { SelectionContainer { Text(formatDateTime(details.publishedTimeMillis)) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("分辨率") },
                leadingContent = { Icon(Icons.Outlined.Hd, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(details.properties.resolution) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("文件大小") },
                leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        if (details.fileSize == FileSize.Unspecified) {
                            Text("未知")
                        } else {
                            Text(details.fileSize.toString())
                        }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("原始链接") },
                leadingContent = placeholderLeadingContent,
                supportingContent = {
                    SelectionContainer {
                        Text(details.contentOriginalUri, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                trailingContent = { copyContent { details.contentOriginalUri } },
            )
        }
        if (details.fileType != null) {
            item {
                ListItem(
                    headlineContent = { Text("文件类型") },
                    leadingContent = placeholderLeadingContent,
                    supportingContent = {
                        SelectionContainer {
                            Text(
                                when (details.fileType) {
                                    ResourceLocation.LocalFile.FileType.MPTS -> "MPTS"
                                    ResourceLocation.LocalFile.FileType.CONTAINED -> "Contained"
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                )
            }
        }
        if (details.contentDownloadUri != null) {
            item {
                ListItem(
                    headlineContent = { Text("原始下载链接") },
                    leadingContent = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                    supportingContent = {
                        SelectionContainer {
                            Text(details.contentDownloadUri, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingContent = { copyContent { details.contentDownloadUri } },
                )
            }
        }
        if (details.localCacheFilePath != null) {
            item {
                ListItem(
                    headlineContent = { Text("本地缓存路径") },
                    leadingContent = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                    supportingContent = {
                        SelectionContainer {
                            Text(details.localCacheFilePath.toString(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingContent = {
                        browseFile(details.localCacheFilePath)
                    },
                )
            }
        }
        if (details.totalSegments != null) {
            item {
                ListItem(
                    headlineContent = { Text("总片段数") },
                    leadingContent = placeholderLeadingContent,
                    supportingContent = {
                        Text(details.totalSegments.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        if (details.downloaderStatus != null) {
            item {
                ListItem(
                    headlineContent = { Text("下载器内部状态") },
                    leadingContent = placeholderLeadingContent,
                    supportingContent = {
                        Text(details.downloaderStatus, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        details.extraFiles.subtitles.forEachIndexed { index, subtitle ->
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


@TestOnly
val TestMediaDetails
    get() = MediaDetails.from(
        TestMediaList[0],
        MikanCNMediaSource.INFO,
        null,
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewCacheGroupDetailsColumn() = ProvideCompositionLocalsForPreview {
    MediaDetailsLazyGrid(TestMediaDetails)
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.isAndroid

@Composable
fun ShareEpisodeDropdown(
    data: MediaShareData,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberAsyncHandler()
    val uriHandler = LocalUriHandler.current
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        data.download?.let { download ->
            val downloadText = when (download) {
                is ResourceLocation.HttpStreamingFile -> "视频流链接"
                is ResourceLocation.HttpTorrentFile -> "种子文件下载链接"
                is ResourceLocation.LocalFile -> "本地文件链接"
                is ResourceLocation.MagnetLink -> "磁力链接"
                is ResourceLocation.WebVideo -> "网页链接" // should not happen though
            }
            DropdownMenuItem(
                text = {
                    Text("复制$downloadText")
                },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        clipboard.setClipEntryText(download.uri)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            )
            DropdownMenuItem(
                text = { Text("访问$downloadText") },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(download.uri)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
            if (LocalPlatform.current.isAndroid() && download !is ResourceLocation.WebVideo) {
                DropdownMenuItem(
                    text = { Text("用其他应用打开") },
                    onClick = {
                        onDismissRequest()
                        browserNavigator.intentOpenVideo(context, download.uri)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Outbox, null) },
                )
            }
        }

        data.websiteUrl?.let { websiteUrl ->
            DropdownMenuItem(
                text = { Text("复制数据源页面链接") },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        clipboard.setClipEntryText(websiteUrl)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            )
            DropdownMenuItem(
                text = { Text("访问数据源页面") },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(websiteUrl)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
        }
    }
}

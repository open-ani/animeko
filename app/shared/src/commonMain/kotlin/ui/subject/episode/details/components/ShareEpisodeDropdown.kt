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
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_share_copy_link
import me.him188.ani.app.ui.lang.subject_episode_share_copy_source_page
import me.him188.ani.app.ui.lang.subject_episode_share_local_file_link
import me.him188.ani.app.ui.lang.subject_episode_share_magnet_link
import me.him188.ani.app.ui.lang.subject_episode_share_open_link
import me.him188.ani.app.ui.lang.subject_episode_share_open_source_page
import me.him188.ani.app.ui.lang.subject_episode_share_open_with_other_app
import me.him188.ani.app.ui.lang.subject_episode_share_stream_link
import me.him188.ani.app.ui.lang.subject_episode_share_torrent_download_link
import me.him188.ani.app.ui.lang.subject_episode_share_webpage_link
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.isAndroid
import org.jetbrains.compose.resources.stringResource

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
                is ResourceLocation.HttpStreamingFile -> stringResource(Lang.subject_episode_share_stream_link)
                is ResourceLocation.HttpTorrentFile -> stringResource(Lang.subject_episode_share_torrent_download_link)
                is ResourceLocation.LocalFile -> stringResource(Lang.subject_episode_share_local_file_link)
                is ResourceLocation.MagnetLink -> stringResource(Lang.subject_episode_share_magnet_link)
                is ResourceLocation.WebVideo -> stringResource(Lang.subject_episode_share_webpage_link) // should not happen though
            }
            val copyDownloadText = stringResource(Lang.subject_episode_share_copy_link, downloadText)
            val openDownloadText = stringResource(Lang.subject_episode_share_open_link, downloadText)
            val openWithOtherAppText = stringResource(Lang.subject_episode_share_open_with_other_app)
            DropdownMenuItem(
                text = {
                    Text(copyDownloadText)
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
                text = { Text(openDownloadText) },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(download.uri)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
            if (LocalPlatform.current.isAndroid() && download !is ResourceLocation.WebVideo) {
                DropdownMenuItem(
                    text = { Text(openWithOtherAppText) },
                    onClick = {
                        onDismissRequest()
                        browserNavigator.intentOpenVideo(context, download.uri)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Outbox, null) },
                )
            }
        }

        data.websiteUrl?.let { websiteUrl ->
            val copySourcePageText = stringResource(Lang.subject_episode_share_copy_source_page)
            val openSourcePageText = stringResource(Lang.subject_episode_share_open_source_page)
            DropdownMenuItem(
                text = { Text(copySourcePageText) },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        clipboard.setClipEntryText(websiteUrl)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            )
            DropdownMenuItem(
                text = { Text(openSourcePageText) },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(websiteUrl)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
        }
    }
}

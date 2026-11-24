/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.share

import androidx.compose.runtime.Immutable
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData


@Immutable
data class MediaShareData(
    val websiteUrl: String?,
    val download: ResourceLocation?
) {
    companion object {
        fun from(
            media: Media?,
            mediaData: MediaData?,
        ): MediaShareData {
            val realDownload = when (mediaData) {
                is UriMediaData -> {
                    // 流式资源是 http(s)/file URI, 直接分享该地址.
                    // Android SAF 本地文件是 content:// 等设备私有 URI, 不能构造 HttpStreamingFile,
                    // 回落到 media.download, 让分享菜单按本地文件处理.
                    if (mediaData.uri.startsWithShareableStreamScheme()) {
                        ResourceLocation.HttpStreamingFile(mediaData.uri)
                    } else {
                        media?.download
                    }
                }

                is TorrentMediaData,
                is SeekableInputMediaData,
                null -> {
                    media?.download
                }
            }

            return MediaShareData(
                websiteUrl = media?.originalUrl,
                download = realDownload,
            )
        }

        private fun String.startsWithShareableStreamScheme(): Boolean =
            startsWith("https://") || startsWith("http://") || startsWith("file://")
    }
}

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.files.Path
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaSource
import org.openani.mediamp.source.SystemFileMediaSource

class LocalFileVideoSourceResolver : VideoSourceResolver {
    override suspend fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.LocalFile
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaSource<*> {
        when (val download = media.download) {
            is ResourceLocation.LocalFile -> {
                return SystemFileMediaSource(
                    Path(download.filePath),
                    media.extraFiles.toMediampMediaExtraFiles(),
                )
            }

            else -> throw UnsupportedMediaException(media)
        }
    }
}
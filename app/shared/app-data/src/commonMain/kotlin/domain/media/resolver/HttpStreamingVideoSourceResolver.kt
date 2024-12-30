/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.MediaSource
import org.openani.mediamp.source.UriMediaSource

class HttpStreamingVideoSourceResolver : VideoSourceResolver {
    override suspend fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.HttpStreamingFile
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaSource<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)
        return HttpStreamingMediaSource(
            media.download.uri,
            media.originalTitle,
            emptyMap(),
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }
}

class HttpStreamingMediaSource(
    uri: String,
    val originalTitle: String,
    headers: Map<String, String> = emptyMap(),
    extraFiles: MediaExtraFiles,
) : UriMediaSource(uri, headers, extraFiles)

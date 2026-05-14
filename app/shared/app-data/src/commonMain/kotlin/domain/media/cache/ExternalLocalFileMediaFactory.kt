/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.name

object ExternalLocalFileMediaFactory {
    fun createMedia(
        file: SystemPath,
        subjectInfo: SubjectInfo,
        episodeInfo: EpisodeInfo,
    ): Media {
        val filePath = file.absolutePath
        return DefaultMedia(
            mediaId = "external-local-file:${subjectInfo.subjectId}:${episodeInfo.episodeId}:${filePath.hashCode()}",
            mediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            originalUrl = filePath,
            download = ResourceLocation.LocalFile(
                filePath = filePath,
                fileType = ResourceLocation.LocalFile.FileType.CONTAINED,
            ),
            originalTitle = file.name,
            publishedTime = 0L,
            properties = MediaProperties(
                subjectName = subjectInfo.nameCnOrName,
                episodeName = episodeInfo.displayName,
                subtitleLanguageIds = emptyList(),
                resolution = "",
                alliance = "外部文件",
                size = file.length().bytes,
                subtitleKind = null,
            ),
            episodeRange = EpisodeRange.single(episodeInfo.sort),
            location = MediaSourceLocation.Local,
            kind = MediaSourceKind.LocalCache,
        )
    }

    fun createMetadata(
        subjectInfo: SubjectInfo,
        episodeInfo: EpisodeInfo,
    ): MediaCacheMetadata {
        return MediaCacheMetadata(
            MediaFetchRequest.create(subjectInfo, episodeInfo),
        )
    }
}

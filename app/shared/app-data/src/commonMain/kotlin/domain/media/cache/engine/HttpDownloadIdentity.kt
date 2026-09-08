/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.io.Buffer
import kotlinx.io.writeString
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.readAndDigest

/** Different episodes of a season resource must never share an HTTP task or output file. */
internal fun httpDownloadId(media: Media, metadata: MediaCacheMetadata): DownloadId {
    val identity = listOf(media.mediaId, metadata.subjectId, metadata.episodeId)
        .joinToString("") { "${it.length}:$it" }
    val digest = Buffer().apply { writeString(identity) }.readAndDigest(DigestAlgorithm.SHA256).toHexString()
    return DownloadId("http-v2-$digest")
}

/** Existing persisted records keep their legacy task and file names; new downloads always use v2. */
internal suspend fun restoredHttpDownloadId(
    media: Media,
    metadata: MediaCacheMetadata,
    exists: suspend (DownloadId) -> Boolean,
): DownloadId {
    val current = httpDownloadId(media, metadata)
    if (exists(current)) return current
    return DownloadId(media.mediaId.replace(Regex("[\\\\/:*?\"<>|]"), "-"))
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import me.him188.ani.datasources.api.source.MediaMatch

internal fun MediaMatch.toCandidateResult(): MediaCandidateResult {
    return MediaCandidateResult(
        mediaId = media.mediaId,
        mediaSourceId = media.mediaSourceId,
        originalTitle = media.originalTitle,
        originalUrl = media.originalUrl,
        downloadUri = media.download.uri,
        downloadType = media.download::class.simpleName ?: "unknown",
        kind = media.kind.toString(),
        matchKind = kind.toString(),
        episodeRange = media.episodeRange?.toString(),
    )
}

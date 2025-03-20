/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest

/**
 * @see me.him188.ani.datasources.api.source.MediaFetchRequest
 */
class MediaFetchRequestBuilder(
    var subjectId: String = "1",
    var episodeId: String = "1",
    var subjectNameCN: String? = null,
    var subjectNames: List<String> = listOf(),
    var episodeSort: EpisodeSort = EpisodeSort(1),
    var episodeName: String = "",
    var episodeEp: EpisodeSort? = null,
) {
    fun build(): MediaFetchRequest = MediaFetchRequest(
        subjectId,
        episodeId,
        subjectNameCN,
        subjectNames,
        episodeSort,
        episodeName,
        episodeEp,
    )

    fun takeFrom(other: MediaFetchRequest) {
        subjectId = other.subjectId
        episodeId = other.episodeId
        subjectNameCN = other.subjectNameCN
        subjectNames = other.subjectNames
        episodeSort = other.episodeSort
        episodeName = other.episodeName
        episodeEp = other.episodeEp
    }
}

inline fun buildMediaFetchRequest(
    action: MediaFetchRequestBuilder.() -> Unit,
): MediaFetchRequest {
    return MediaFetchRequestBuilder().apply(action).build()
}

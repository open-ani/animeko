/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.danmaku

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.danmaku.CombinedDanmakuFetchResult
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchDanmakuRequest(
    val subjectInfo: SubjectInfo,
    val episodeInfo: EpisodeInfo,
    val episodeId: Int,
    val filename: String? = null,
    val fileLength: Long? = null,
    val fileHash: String? = "aa".repeat(16),
    val videoDuration: Duration = 0.milliseconds,
)

class DanmakuRepository(
    private val danmakuManager: DanmakuManager,
) : Repository() {
    suspend fun search(request: SearchDanmakuRequest): CombinedDanmakuFetchResult {
        val subject = request.subjectInfo
        val episode = request.episodeInfo

        return danmakuManager.fetch(
            DanmakuSearchRequest(
                subjectId = subject.subjectId,
                subjectPrimaryName = subject.displayName,
                subjectNames = subject.allNames,
                subjectPublishDate = subject.airDate,
                episodeId = episode.episodeId,
                episodeSort = episode.sort,
                episodeEp = episode.ep,
                episodeName = episode.displayName,
                filename = request.filename,
                fileHash = request.fileHash,
                fileSize = request.fileLength,
                videoDuration = request.videoDuration,
            ),
        )
    }

    suspend fun post(episodeId: Int, info: DanmakuInfo): Danmaku = danmakuManager.post(episodeId, info)
}

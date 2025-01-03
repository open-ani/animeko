/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException


fun interface SearchDanmakuUseCase : UseCase {
    @Throws(RepositoryException::class, CancellationException::class)
    suspend operator fun invoke(request: SearchDanmakuRequest): CombinedDanmakuFetchResult
}

class SearchDanmakuUseCaseImpl(
    private val context: CoroutineContext = Dispatchers.Default,
) : SearchDanmakuUseCase, KoinComponent {
    private val danmakuManager: DanmakuManager by inject()

    override suspend fun invoke(request: SearchDanmakuRequest): CombinedDanmakuFetchResult = withContext(context) {
        val subject = request.subjectInfo
        val episode = request.episodeInfo

        danmakuManager.fetch(
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
}

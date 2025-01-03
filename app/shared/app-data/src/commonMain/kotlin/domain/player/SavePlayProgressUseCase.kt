/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.PlaybackState
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

interface SavePlayProgressUseCase : UseCase {
    suspend operator fun invoke(
        playbackState: PlaybackState,
        currentPositionMillis: Long,
        videoDurationMillis: Long?,
        episodeId: Int
    )
}

class SavePlayProgressUseCaseImpl : SavePlayProgressUseCase, KoinComponent {
    private val episodePlayHistoryRepository: EpisodePlayHistoryRepository by inject()

    override suspend fun invoke(
        playbackState: PlaybackState,
        currentPositionMillis: Long,
        videoDurationMillis: Long?,
        episodeId: Int
    ) {
        if (playbackState == PlaybackState.FINISHED) return
        val durationMillis = videoDurationMillis.let {
            if (it == null) return@let 0L
            return@let max(0, it - 1000) // 最后一秒不会保存进度
        }

        if (currentPositionMillis in 0..<durationMillis) {
            logger.info { "Saving position for epId=$episodeId: ${currentPositionMillis.milliseconds}" }
            episodePlayHistoryRepository.saveOrUpdate(episodeId, currentPositionMillis)
        }
    }

    private companion object {
        private val logger = logger<SavePlayProgressUseCaseImpl>()
    }
}
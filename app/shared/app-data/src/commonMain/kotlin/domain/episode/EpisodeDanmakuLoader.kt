/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.danmaku.DanmakuLoaderImpl
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuSession
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.time.Duration.Companion.milliseconds

/**
 * Connects episode data, the player, and the danmaku loader.
 *
 * It reads [bundleFlow] to launch danmaku loading, and provides a [danmakuEventFlow] that is connected to the player.
 */
class EpisodeDanmakuLoader(
    player: MediampPlayer,
    private val bundleFlow: Flow<SubjectEpisodeInfoBundle>,
    backgroundScope: CoroutineScope,
    koin: Koin,
) {
    private val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase by koin.inject()

    private val flowScope = backgroundScope

    private val danmakuLoader = DanmakuLoaderImpl(
        bundleFlow
            .map { bundle ->
                SearchDanmakuRequest(
                    bundle.subjectInfo,
                    bundle.episodeInfo,
                    bundle.episodeId,
                )
            },
        backgroundScope,
        koin,
    )

    private val danmakuSessionFlow: Flow<DanmakuSession> = danmakuLoader.collectionFlow.mapLatest { session ->
        session.at(
            progress = player.currentPositionMillis.map { it.milliseconds },
            danmakuRegexFilterList = getDanmakuRegexFilterListFlowUseCase(),
        )
    }.shareIn(flowScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    val danmakuLoadingStateFlow: Flow<DanmakuLoadingState> = danmakuLoader.state
    val danmakuEventFlow: Flow<DanmakuEvent> = danmakuSessionFlow.flatMapLatest { it.events }

    suspend fun requestRepopulate() {
        danmakuSessionFlow.first().requestRepopulate()
    }
}

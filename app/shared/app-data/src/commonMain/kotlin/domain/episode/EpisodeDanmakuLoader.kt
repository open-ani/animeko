/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.danmaku.DanmakuLoaderImpl
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.media.player.data.filenameOrNull
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.metadata.duration
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Connects episode data, the player, and the danmaku loader.
 *
 * It reads [bundleFlow] to launch danmaku loading, and provides a [danmakuEventFlow] that is connected to the player.
 */
class EpisodeDanmakuLoader(
    player: MediampPlayer,
    private val selectedMedia: Flow<Media?>,
    private val bundleFlow: Flow<SubjectEpisodeInfoBundle>,
    backgroundScope: CoroutineScope,
    koin: Koin,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) {
    private val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase by koin.inject()

    private val flowScope = backgroundScope

//    val playerExtension = object : PlayerExtension("EpisodeDanmakuLoader") {
//        override fun onStart(backgroundTaskScope: ExtensionBackgroundTaskScope) {
//            backgroundTaskScope.launch("DanmakuLoader") {
//                danmakuLoader.collectionFlow.first()
//            }
//        }
//    }

    private val danmakuLoader = DanmakuLoaderImpl(
        combine(
            bundleFlow,
            player.mediaData,
            selectedMedia,
            player.mediaProperties.filter { it != null }.map { it?.duration ?: 0.milliseconds },
        ) { info, mediaData, selectedMedia, duration ->
            if (mediaData == null) {
                null
            } else {
                SearchDanmakuRequest(
                    info.subjectInfo,
                    info.episodeInfo,
                    info.episodeId,
                    filename = mediaData.filenameOrNull ?: selectedMedia?.originalTitle,
                    fileLength = when (mediaData) {
                        null -> null
                        is SeekableInputMediaData -> mediaData.fileLength()
                        is UriMediaData -> null
                    },
                    videoDuration = duration,
                )
            }
        }.distinctUntilChanged()
            .debounce {
                if (it == null) {
                    0.milliseconds // 立即清空
                } else {
                    1.seconds
                }
            }
            .onEach {
                logger.info { "New SearchDanmakuRequest: $it" }
            },
        backgroundScope,
        koin,
        sharingStarted,
    )

    private val danmakuSessionFlow: Flow<DanmakuSession> = danmakuLoader.collectionFlow.mapLatest { session ->
        session.at(
            progress = player.currentPositionMillis.map { it.milliseconds },
            danmakuRegexFilterList = getDanmakuRegexFilterListFlowUseCase(),
        )
    }.shareIn(flowScope, started = sharingStarted, replay = 1)

    val danmakuLoadingStateFlow: Flow<DanmakuLoadingState> = danmakuLoader.state
    val danmakuEventFlow: Flow<DanmakuEvent> = danmakuSessionFlow.flatMapLatest { it.events }

    suspend fun requestRepopulate() {
        danmakuSessionFlow.first().requestRepopulate()
    }

    private companion object {
        private val logger = logger<EpisodeDanmakuLoader>()
    }
}

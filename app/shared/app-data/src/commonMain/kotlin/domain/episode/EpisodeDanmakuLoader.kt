/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.danmaku.DanmakuLoaderImpl
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.media.player.data.filenameOrNull
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.danmaku.api.TimeBasedDanmakuSession
import me.him188.ani.danmaku.api.emptyDanmakuCollection
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.annotations.TestOnly
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

    private val config = MutableStateFlow(persistentMapOf<String, DanmakuOriginConfig>())
    val configFlow = config.asStateFlow()

    private val danmakuCollectionFlow = combine(danmakuLoader.fetchResultFlow, config) { results, configMap ->
        if (results == null) {
            emptyDanmakuCollection()
        } else {
            // apply config

            TimeBasedDanmakuSession.create(
                results.asSequence().flatMap { result ->
                    val config = configMap[result.matchInfo.providerId] ?: DanmakuOriginConfig.Default

                    if (!config.enabled) {
                        return@flatMap emptySequence()
                    }

                    result.list.map {
                        it.copy(playTimeMillis = it.playTimeMillis + config.shiftMillis)
                    }
                },
            )
        }
    }

    private val danmakuSessionFlow: Flow<DanmakuSession> = danmakuCollectionFlow.mapLatest { session ->
        session.at(
            progress = player.currentPositionMillis.map { it.milliseconds },
            danmakuRegexFilterList = getDanmakuRegexFilterListFlowUseCase(),
        )
    }.shareIn(flowScope, started = sharingStarted, replay = 1)

    val danmakuLoadingStateFlow: StateFlow<DanmakuLoadingState> = danmakuLoader.danmakuLoadingStateFlow
    
    // this flow must emit a value quickly when started, otherwise it will block ui
    val fetchResults: Flow<List<DanmakuFetchResultWithConfig>> = combine(
        danmakuLoader.fetchResultFlow.onStart { emit(null) },
        configFlow,
    ) { results, configs ->
        results.orEmpty().map {
            DanmakuFetchResultWithConfig(
                it.matchInfo.providerId,
                it.matchInfo,
                configs[it.matchInfo.providerId] ?: DanmakuOriginConfig.Default,
            )
        }
    }.shareIn(flowScope, started = sharingStarted, replay = 1)

    val danmakuEventFlow: Flow<DanmakuEvent> = danmakuSessionFlow.flatMapLatest { it.events }

    suspend fun requestRepopulate() {
        danmakuSessionFlow.first().requestRepopulate()
    }

    fun setEnabled(providerId: String, enabled: Boolean) {
        config.update { conf ->
            conf.put(
                providerId,
                conf.getConfigOrDefault(providerId).copy(enabled = enabled),
            )
        }
    }

    fun setShiftMillis(providerId: String, shiftMillis: Long) {
        config.update { conf ->
            conf.put(
                providerId,
                conf.getConfigOrDefault(providerId).copy(shiftMillis = shiftMillis),
            )
        }
    }

    private fun Map<String, DanmakuOriginConfig>.getConfigOrDefault(providerId: String) =
        this[providerId] ?: DanmakuOriginConfig.Default

    private companion object {
        private val logger = logger<EpisodeDanmakuLoader>()
    }
}

/**
 * 配置一个弹幕数据源
 */
data class DanmakuOriginConfig(
    val enabled: Boolean,
    val shiftMillis: Long,
) {
    companion object {
        val Default = DanmakuOriginConfig(enabled = true, shiftMillis = 0)
    }
}

/**
 * 一个弹幕数据源的结果, 包含了匹配信息和弹幕列表, 还包含本次会话的配置
 */
data class DanmakuFetchResultWithConfig(
    val providerId: String,
    val matchInfo: DanmakuMatchInfo,
    val config: DanmakuOriginConfig,
)

@TestOnly
fun createTestDanmakuFetchResultWithConfig(
    providerId: String,
    matchInfo: DanmakuMatchInfo = DanmakuMatchInfo(
        providerId,
        100,
        DanmakuMatchMethod.Exact(
            subjectTitle = "条目标题",
            episodeTitle = "剧集标题",
        ),
    ),
    config: DanmakuOriginConfig = DanmakuOriginConfig.Default,
): DanmakuFetchResultWithConfig = DanmakuFetchResultWithConfig(providerId, matchInfo, config)

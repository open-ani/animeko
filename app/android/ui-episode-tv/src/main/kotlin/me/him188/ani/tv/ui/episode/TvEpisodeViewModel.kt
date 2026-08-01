/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeDanmakuLoader
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.infoBundleFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.player.extension.AutoSelectExtension
import me.him188.ani.app.domain.player.extension.MarkAsWatchedExtension
import me.him188.ani.app.domain.player.extension.ObserveWebMediaSourcePreferenceExtension
import me.him188.ani.app.domain.player.extension.PlaybackSpeedExtension
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.app.domain.player.extension.SaveMediaPreferenceExtension
import me.him188.ani.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
import me.him188.ani.app.domain.player.extension.SwitchNextEpisodeExtension
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuTrackProperties
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.togglePause

/**
 * TV 播放页薄 VM (atv-architecture.md §8.1): 与手机共用同一套播放编排 (app-data domain),
 * 扩展取子集 —— 不含 Analytics / WatchTogether / CacheOnBtPlay (裁剪, §1.2).
 */
@Stable
class TvEpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    context: ContextMP,
    private val koin: Koin = GlobalKoin,
) : AbstractViewModel(), KoinComponent {
    private val playerStateFactory: MediampPlayerFactory<*> by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val danmakuRepository: DanmakuRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase by inject()

    val player: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    private val playbackSpeedFlow: Flow<Float> =
        settingsRepository.videoScaffoldConfig.flow.map { it.playbackSpeed }.distinctUntilChanged()

    private val episodeCollectionsFlow = episodeCollectionRepository
        .subjectEpisodeCollectionInfosFlow(subjectId)
        .shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val subjectCollectionFlow = subjectCollectionRepository
        .subjectCollectionFlow(subjectId)
        .shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val fetchPlayState = EpisodeFetchSelectPlayState(
        subjectId, initialEpisodeId, player, backgroundScope,
        extensions = listOf(
            PlaybackSpeedExtension.Factory(playbackSpeedFlow),
            RememberPlayProgressExtension,
            MarkAsWatchedExtension,
            SwitchNextEpisodeExtension.Factory(
                getNextEpisode = { currentEpisodeId ->
                    val list = episodeCollectionsFlow.first()
                    val subject = subjectCollectionFlow.first()
                    val currentIndex = list.indexOfFirst { it.episodeId == currentEpisodeId }
                    if (currentIndex == -1) {
                        null
                    } else {
                        val nextEpisode = list.getOrNull(currentIndex + 1) ?: return@Factory null
                        if (!nextEpisode.episodeInfo.isKnownCompleted(subject.recurrence)) {
                            null
                        } else {
                            nextEpisode.episodeId
                        }
                    }
                },
            ),
            SwitchMediaOnPlayerErrorExtension,
            AutoSelectExtension,
            SaveMediaPreferenceExtension,
            ObserveWebMediaSourcePreferenceExtension,
        ),
        koin,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    // region 页面状态

    @OptIn(UnsafeEpisodeSessionApi::class)
    val titleFlow: StateFlow<String> = fetchPlayState.infoBundleFlow
        .filterNotNull()
        .map { bundle ->
            val subjectName = bundle.subjectCollectionInfo.subjectInfo.displayName
            val episode = bundle.episodeCollectionInfo.episodeInfo
            "$subjectName  第 ${episode.sort} 话"
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), "")

    val videoLoadingState: StateFlow<VideoLoadingState> =
        fetchPlayState.playerSession.videoLoadingState

    // endregion

    // region 弹幕 (接线拷自手机 EpisodeViewModel, atv-architecture.md §8.3)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val episodeDanmakuLoader = EpisodeDanmakuLoader(
        player = player,
        selectedMedia = fetchPlayState.mediaSelectorFlow.transformLatest {
            if (it == null) {
                emit(null)
            } else {
                emitAll(it.selected)
            }
        },
        bundleFlow = fetchPlayState.infoBundleFlow.filterNotNull().distinctUntilChanged(),
        danmakuRepository = danmakuRepository,
        getDanmakuRegexFilterListFlowUseCase = getDanmakuRegexFilterListFlowUseCase,
        backgroundScope,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    val danmakuEventFlow: Flow<TvUIDanmakuEvent> = danmakuRepository.selfId.flatMapLatest { selfId ->
        fun createDanmakuPresentation(data: DanmakuInfo, selfId: String?) =
            DanmakuPresentation(data, isSelf = selfId == data.senderId)

        episodeDanmakuLoader.danmakuEventFlow.mapNotNull { event ->
            when (event) {
                is DanmakuEvent.Add -> {
                    val data = event.danmaku
                    if (data.text.isBlank()) {
                        null
                    } else {
                        TvUIDanmakuEvent.Add(createDanmakuPresentation(data, selfId))
                    }
                }

                is DanmakuEvent.Repopulate -> {
                    TvUIDanmakuEvent.Repopulate(
                        event.list
                            .filter { it.text.any { c -> !c.isWhitespace() } }
                            .map { createDanmakuPresentation(it, selfId) },
                        withContext(Dispatchers.Main) {
                            player.getCurrentPositionMillis()
                        },
                    )
                }
            }
        }
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val danmakuConfigState = mutableStateOf(DanmakuConfig.Default)
    val danmakuHostState = DanmakuHostState(danmakuConfigState, DanmakuTrackProperties.Default)

    // endregion

    /** Web 源解析器 (WebView), 页面需调用其 ComposeContent() 完成挂载 (同手机 EpisodePage). */
    val mediaResolver: MediaResolver get() = fetchPlayState.playerSession.mediaResolver

    /** 启动扩展系统 (AutoSelect/自动连播/进度记忆等), 由页面首帧调用 (同手机 EpisodePage). */
    fun onUIReady() {
        fetchPlayState.onUIReady()
    }

    fun togglePause() {
        player.togglePause()
    }

    fun seekBy(deltaMillis: Long) {
        val target = (player.getCurrentPositionMillis() + deltaMillis).coerceAtLeast(0)
        player.seekTo(target)
    }

    init {
        backgroundScope.launch {
            settingsRepository.danmakuConfig.flow.collect { danmakuConfigState.value = it }
        }
        backgroundScope.launch {
            // 保证数据源会一直查询 (拷自手机 EpisodePageState): MediaFetchSession 是冷流,
            // 必须有订阅者 cumulativeResults 才会真正向各数据源发起请求, AutoSelect 才有候选可选.
            fetchPlayState.episodeSessionFlow.collectLatest { session ->
                session.fetchSelectFlow.flatMapLatest { bundle ->
                    bundle?.mediaFetchSession?.cumulativeResults ?: flowOf(emptyList())
                }.collect()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.launch(NonCancellable + CoroutineName("TvEpisodeViewModel#onCleared")) {
            fetchPlayState.onClose()
        }
    }

    override fun getKoin(): Koin = koin
}

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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.domain.episode.episodeIdFlow
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.togglePause
import kotlin.math.abs

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

    /** 播放页顶部两行标题: 条目名 / 「第 NN 集 集标题」(对齐参考版). */
    data class TitleInfo(val subjectName: String, val episodeLine: String)

    @OptIn(UnsafeEpisodeSessionApi::class)
    val titleFlow: StateFlow<TitleInfo> = fetchPlayState.infoBundleFlow
        .filterNotNull()
        .map { bundle ->
            val episode = bundle.episodeCollectionInfo.episodeInfo
            TitleInfo(
                subjectName = bundle.subjectCollectionInfo.subjectInfo.displayName,
                episodeLine = buildString {
                    append("第 ${episode.sort} 集")
                    val name = episode.nameCn.ifBlank { episode.name }
                    if (name.isNotBlank()) append("  $name")
                },
            )
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TitleInfo("", ""))

    /** 当前选中数据源名 (播放器底栏展示). */
    @OptIn(UnsafeEpisodeSessionApi::class)
    val currentMediaLabel: StateFlow<String?> = fetchPlayState.mediaSelectorFlow
        .transformLatest { selector ->
            if (selector == null) {
                emit(null)
            } else {
                emitAll(selector.selected.map { it?.properties?.alliance })
            }
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    val videoLoadingState: StateFlow<VideoLoadingState> =
        fetchPlayState.playerSession.videoLoadingState

    /** 选集条条目 (§8.3): 集序号 + 标题 + 已看标记. */
    data class StripEpisode(
        val episodeId: Int,
        val sortLabel: String,
        val title: String,
        val watched: Boolean,
    )

    val episodeStripFlow: StateFlow<List<StripEpisode>> = episodeCollectionsFlow
        .map { list ->
            list.map { collection ->
                val info = collection.episodeInfo
                StripEpisode(
                    episodeId = collection.episodeId,
                    sortLabel = "第 ${info.sort} 集",
                    title = info.nameCn.ifBlank { info.name },
                    watched = collection.collectionType == UnifiedCollectionType.DONE,
                )
            }
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前播放的分集 (切集后随会话切换). */
    @OptIn(UnsafeEpisodeSessionApi::class)
    val currentEpisodeIdFlow: StateFlow<Int> = fetchPlayState.episodeIdFlow
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), initialEpisodeId)

    // endregion

    // region 数据源选择 (§8.1: 仅 WEB 源; TV 未装配缓存/torrent, 双保险过滤)

    @OptIn(UnsafeEpisodeSessionApi::class)
    val mediaCandidates: StateFlow<List<Media>> = fetchPlayState.mediaSelectorFlow
        .flatMapLatest { selector ->
            selector?.filteredCandidatesMedia ?: flowOf(emptyList())
        }
        .map { list -> list.filter { it.kind == MediaSourceKind.WEB } }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(UnsafeEpisodeSessionApi::class)
    val selectedMedia: StateFlow<Media?> = fetchPlayState.mediaSelectorFlow
        .flatMapLatest { selector -> selector?.selected ?: flowOf(null) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(UnsafeEpisodeSessionApi::class)
    fun selectMedia(media: Media) {
        backgroundScope.launch {
            fetchPlayState.mediaSelectorFlow.filterNotNull().first().select(media)
        }
    }

    // endregion

    // region 浮出面板数据 (§8.3 面板 ×5: 推荐/Staff/角色为条目级, 评论随当前集, 弹幕为已加载列表)

    private val subjectRelationsRepository: SubjectRelationsRepository by inject()
    private val episodeCommentRepository: EpisodeCommentRepository by inject()
    private val bangumiRelatedPeopleService: BangumiRelatedPeopleService by inject()

    val relatedSubjectsFlow: StateFlow<List<RelatedSubjectInfo>> = bangumiRelatedPeopleService
        .relatedSubjectsFlow(subjectId)
        .map { RelatedSubjectInfo.sortList(it) }
        .catch { emit(emptyList()) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val staffFlow: StateFlow<List<RelatedPersonInfo>> = subjectRelationsRepository
        .subjectRelatedPersonsFlow(subjectId)
        .map { it.sortedWith(RelatedPersonInfo.ImportanceOrder) }
        .catch { emit(emptyList()) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val charactersFlow: StateFlow<List<RelatedCharacterInfo>> = subjectRelationsRepository
        .subjectRelatedCharactersFlow(subjectId)
        .map { it.sortedWith(RelatedCharacterInfo.ImportanceOrder) }
        .catch { emit(emptyList()) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前集的评论 (只读, §1.2); 切集自动换源. */
    val episodeCommentsPager: Flow<PagingData<EpisodeComment>> = currentEpisodeIdFlow
        .flatMapLatest { episodeCommentRepository.subjectEpisodeCommentsPager(it.toLong()) }
        .cachedIn(backgroundScope)

    /** 已加载弹幕 (新→旧; Repopulate 重置 + Add 头插, 面板 reverseLayout 吸底展示). */
    private val danmakuList = MutableStateFlow<List<DanmakuPresentation>>(emptyList())
    val danmakuListFlow: StateFlow<List<DanmakuPresentation>> = danmakuList

    // endregion

    // region 播放器能力 (mediamp features)

    private val playbackSpeedFeature get() = player.features[PlaybackSpeed]
    private val aspectRatioFeature get() = player.features[VideoAspectRatio]

    val playbackSpeedStateFlow: StateFlow<Float> =
        (playbackSpeedFeature?.valueFlow ?: flowOf(1f))
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), playbackSpeedFeature?.value ?: 1f)

    val aspectRatioModeFlow: StateFlow<AspectRatioMode> =
        aspectRatioFeature?.mode
            ?: kotlinx.coroutines.flow.MutableStateFlow(AspectRatioMode.FIT)

    @OptIn(ExperimentalMediampApi::class)
    val bufferedFractionFlow: StateFlow<Float> =
        (player.features[Buffering]?.bufferedPercentage ?: flowOf(0))
            .map { it / 100f }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** 确认键按住 2.5x 快进 (附录 A: 长按 500ms, 松开还原原倍速). */
    private var speedBeforeHold: Float? = null

    fun setSpeedHold(engaged: Boolean) {
        val feature = playbackSpeedFeature ?: return
        if (engaged) {
            if (speedBeforeHold == null) speedBeforeHold = feature.value
            feature.set(SPEED_HOLD_FACTOR)
        } else {
            speedBeforeHold?.let { feature.set(it) }
            speedBeforeHold = null
        }
    }

    /** 图标行倍速按钮: 在档位间循环 (会话内生效, 不写回设置). */
    fun cycleSpeed() {
        val feature = playbackSpeedFeature ?: return
        val current = feature.value
        val index = SPEED_STEPS.indexOfFirst { abs(it - current) < 0.01f }
        feature.set(SPEED_STEPS[(index + 1).mod(SPEED_STEPS.size)])
    }

    fun cycleAspectRatio() {
        val feature = aspectRatioFeature ?: return
        val modes = AspectRatioMode.entries
        feature.setMode(modes[(modes.indexOf(feature.mode.value) + 1) % modes.size])
    }

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

    fun seekTo(positionMillis: Long) {
        player.seekTo(positionMillis.coerceAtLeast(0))
    }

    fun switchEpisode(episodeId: Int) {
        backgroundScope.launch { fetchPlayState.switchEpisode(episodeId) }
    }

    /** 上一集 (-1) / 下一集 (+1); 到列表边界则不动 (媒体键 RW/FF, §8.2 全局键). */
    fun switchToNeighborEpisode(offset: Int) {
        backgroundScope.launch {
            val list = episodeCollectionsFlow.first()
            val index = list.indexOfFirst { it.episodeId == currentEpisodeIdFlow.value }
            if (index == -1) return@launch
            val target = list.getOrNull(index + offset) ?: return@launch
            fetchPlayState.switchEpisode(target.episodeId)
        }
    }

    init {
        backgroundScope.launch {
            settingsRepository.danmakuConfig.flow.collect { danmakuConfigState.value = it }
        }
        backgroundScope.launch {
            // 弹幕列表面板数据: 与渲染层共享同一 danmakuEventFlow (shareIn), 不重复拉取
            danmakuEventFlow.collect { event ->
                when (event) {
                    is TvUIDanmakuEvent.Repopulate -> danmakuList.value = event.list.asReversed()
                    is TvUIDanmakuEvent.Add ->
                        danmakuList.value =
                            (listOf(event.presentation) + danmakuList.value).take(DANMAKU_LIST_CAP)
                }
            }
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

    companion object {
        /** 确认键按住快进倍率 (附录 A). */
        const val SPEED_HOLD_FACTOR = 2.5f

        /** 图标行倍速循环档位. */
        val SPEED_STEPS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        /** 弹幕列表面板保留上限. */
        const val DANMAKU_LIST_CAP = 500
    }
}

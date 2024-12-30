/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.fetch.FilteredMediaSourceResults
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.fetch.createFetchFetchSessionFlow
import me.him188.ani.app.domain.media.resolver.VideoSourceResolver
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.autoSelect
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.app.ui.comment.CommentContext
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.launchInMain
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.danmaku.PlayerDanmakuState
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetailsState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsPresentation
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingState
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.ui.subject.episode.video.DanmakuLoaderImpl
import me.him188.ani.app.ui.subject.episode.video.DanmakuStatistics
import me.him188.ani.app.ui.subject.episode.video.DelegateDanmakuStatistics
import me.him188.ani.app.ui.subject.episode.video.LoadDanmakuRequest
import me.him188.ani.app.ui.subject.episode.video.PlayerLauncher
import me.him188.ani.app.ui.subject.episode.video.PlayerSkipOpEdState
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.PlaybackState
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Stable
class EpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
) : KoinComponent, AbstractViewModel(), HasBackgroundScope {
    private val episodeId: MutableStateFlow<Int> = MutableStateFlow(initialEpisodeId)
    private val playerStateFactory: MediampPlayerFactory<*> by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val animeScheduleRepository: AnimeScheduleRepository by inject()
    private val mediaCacheManager: MediaCacheManager by inject()
    private val danmakuManager: DanmakuManager by inject()
    val videoSourceResolver: VideoSourceResolver by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val danmakuRegexFilterRepository: DanmakuRegexFilterRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val bangumiCommentService: BangumiCommentService by inject()
    private val bangumiCommentRepository: BangumiCommentRepository by inject()
    private val episodePlayHistoryRepository: EpisodePlayHistoryRepository by inject()
    private val selectorMediaSourceEpisodeCacheRepository: SelectorMediaSourceEpisodeCacheRepository by inject()

    private val subjectCollection = subjectCollectionRepository.subjectCollectionFlow(subjectId)
    private val subjectInfo = subjectCollection.map { it.subjectInfo }
    private val episodeCollection = episodeId.transformLatest { episodeId ->
        emit(null) // 清空前端
        emitAll(episodeCollectionRepository.episodeCollectionInfoFlow(subjectId, episodeId))
    }.stateInBackground(null)

    private val episodeInfo = episodeCollection.map { it?.episodeInfo }.distinctUntilChanged()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()

    // Media Selection

    // 会在更换 ep 时更换
    private val mediaFetchSession = episodeInfo.flatMapLatest { episodeInfo ->
        mediaSourceManager.createFetchFetchSessionFlow(
            if (episodeInfo == null) {
                emptyFlow()
            } else {
                subjectInfo.map { subjectInfo ->
                    MediaFetchRequest.create(subjectInfo, episodeInfo)
                }.distinctUntilChanged()
            },
        )
    }.shareInBackground(started = SharingStarted.Lazily)

    val playerControllerState = PlayerControllerState(ControllerVisibility.Invisible)

    /**
     * 更换 EP 是否已经完成了.
     *
     * 之所以需要这个状态, 是因为当切换 EP 时, [mediaFetchSession] 会变更,
     * 随后 [MediaFetchSession.cumulativeResults] 会传递给 [mediaSelector],
     * 但是 [MediaSelector.filteredCandidatesMedia] 是 share 在后台的, 也就是说它可能会在任意时间之后才会发现 [mediaFetchSession] 有更新.
     *
     * 这就导致当切换 EP 后, [MediaSelector.filteredCandidatesMedia] 会有一段时间仍然是旧的值.
     *
     * 问题在于, [mediaFetchSession] 的变更会触发 [MediaSelectorAutoSelect.selectCached].
     * 自动选择可能比 [MediaSelector.filteredCandidatesMedia] 更新更早, 所以自动选择就会用久的 `mediaList` 选择缓存, 导致将会播放旧的视频.
     *
     *
     * 因此我们增加 [switchEpisodeCompleted], 在操作 EP 时, 先将其设置为 `false`, 然后再修改 [episodeId],
     * 并在 [mediaSelector] 更新时设置为 true.
     *
     * 这样也并不是一定安全的, 有可能在我们修改 [episodeId] 后, 正好旧的查询触发了 [MediaSelector.filteredCandidatesMedia] 更新,
     * 就导致 [switchEpisodeCompleted] 被设置为 `true`, [MediaSelectorAutoSelect.selectCached] 仍然会参考旧的 `mediaList` 选择缓存.
     *
     * 但是这种情况发生的概率比较小, 仅限于后台还有一个查询正在进行的时候用户切换了 EP, 并且旧的 EP 要至少有一个缓存, 而且恰好在一个比较短的时间内旧的查询完成了.
     *
     *
     * 一个更恰当的解决方法可能是把 [mediaSelector] 变成 flow. 当切换 EP 时直接把 [mediaSelector] 重新创建, 就不可能访问到旧的状态了.
     * 但是这会导致所有依赖 [mediaSelector] 的客户都更换为 flow 方式, 这很可能会导致更多问题. 因为我们先使用这个临时解决方案.
     */
    private val switchEpisodeCompleted = MutableStateFlow(false)

    private suspend inline fun awaitSwitchEpisodeCompleted() {
        switchEpisodeCompleted.first { it }
    }

    private val mediaSelector = MediaSelectorFactory.withKoin(getKoin())
        .create(
            subjectId,
            mediaFetchSession.flatMapLatest { it.cumulativeResults },
        )
        .apply {
            val mediaSelectorSettingsFlow = settingsRepository.mediaSelectorSettings.flow
            val preferKindFlow = mediaSelectorSettingsFlow.map { it.preferKind }

            autoSelect.run {

                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        awaitCompletedAndSelectDefault(
                            it,
                            preferKindFlow,
                        )
                    }
                }
                launchInBackground {
                    // 快速自动选择数据源. 当按数据源顺序排序, 当最高排序的数据源查询完成后立即自动选择. #1322
                    mediaFetchSession.collectLatest { session ->
                        awaitSwitchEpisodeCompleted()
                        val mediaSelectorSettings = mediaSelectorSettingsFlow.first()
                        if (!mediaSelectorSettings.fastSelectWebKind) {
                            return@collectLatest
                        }

                        suspend fun doSelect(allowNonPreferred: Flow<Boolean>) = fastSelectSources(
                            session,
                            mediaSourceManager.allInstances.first() // no need to subscribe to changes
                                .filter { it.source.kind == MediaSourceKind.WEB }
                                .map { it.mediaSourceId },
                            preferKind = preferKindFlow,
                            overrideUserSelection = false,
                            blacklistMediaIds = emptySet(),
                            allowNonPreferredFlow = allowNonPreferred,
                        )

                        var result = doSelect(
                            allowNonPreferred = flow {
                                when (val delay = mediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay) {
                                    Duration.ZERO -> {
                                        emit(true)
                                    }

                                    Duration.INFINITE -> {
                                        emit(false)
                                    }

                                    else -> {
                                        emit(false)
                                        delay(delay)
                                        emit(true)
                                    }
                                }
                            },
                        )
                        if (result == null && mediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay != Duration.INFINITE) {
                            // 所有数据源查询完成, 仍然没有选择到, 有可能是 `allowNonPreferred` 一直为 `false`. 所以我们要最后再尝试 select 一次
                            result = doSelect(
                                allowNonPreferred = flowOf(true),
                            )
                        }
                        logger.info { "fastSelectSources result: $result" }
                    }
                }
                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        selectCached(it)
                    }
                }

                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        if (settingsRepository.mediaSelectorSettings.flow.first().autoEnableLastSelected) {
                            autoEnableLastSelected(it)
                        }
                    }
                }
            }
            eventHandling.run {
                launchInBackground {
                    savePreferenceOnSelect {
                        episodePreferencesRepository.setMediaPreference(subjectId, it)
                    }
                }
            }
            launchInBackground {
                filteredCandidatesMedia.collect {
                    switchEpisodeCompleted.value = true
                }
            }
        }

    val mediaSourceInfoProvider: MediaSourceInfoProvider = MediaSourceInfoProvider(
        getSourceInfoFlow = { mediaSourceManager.infoFlowByMediaSourceId(it) },
    )

    /**
     * "数据源" bottom sheet 内容
     */
    val mediaSelectorState: MediaSelectorState =
        MediaSelectorPresentation(mediaSelector, mediaSourceInfoProvider, backgroundScope)


    /**
     * "数据源" bottom sheet 中的每个数据源的结果
     */
    val mediaSourceResultsPresentation: MediaSourceResultsPresentation =
        MediaSourceResultsPresentation(
            FilteredMediaSourceResults(
                results = mediaFetchSession.mapLatest { it.mediaSourceResults },
                settings = settingsRepository.mediaSelectorSettings.flow,
            ),
            backgroundScope.coroutineContext,
        )

    /**
     * Play controller for video view. This can be saved even when window configuration changes (i.e. everything recomposes).
     */
    val playerState: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    /**
     * 保存播放进度的入口有4个：退出播放页，切换剧集，同集切换数据源，暂停播放
     * 其中 切换剧集 和 同集切换数据源 虽然都是切换数据源，但它们并不能合并成一个入口，
     * 因为 切换数据源 是依赖 PlayerLauncher collect mediaSelector.selected 实现的，
     * 它会在 mediaSelector.unselect() 任意时间后发现 selected 已经改变，导致 episodeId 可能已经改变，从而将当前集的播放进度保存到新的剧集中
     */
    private fun savePlayProgress() {
        if (playerState.playbackState.value == PlaybackState.FINISHED) return
        val positionMillis = playerState.currentPositionMillis.value
        val epId = episodeId.value
        val durationMillis = playerState.videoProperties.value?.durationMillis.let {
            if (it == null) return@let 0L
            return@let max(0, it - 1000) // 最后一秒不会保存进度
        }
        if (positionMillis in 0..<durationMillis) {
            launchInBackground {
                logger.info { "Saving position for epId=$epId: ${positionMillis.milliseconds}" }
                episodePlayHistoryRepository.saveOrUpdate(epId, positionMillis)
            }
        }
    }

    private val playerLauncher: PlayerLauncher = PlayerLauncher(
        mediaSelector, videoSourceResolver, playerState, mediaSourceInfoProvider,
        episodeInfo,
        mediaFetchSession.flatMapLatest { it.hasCompleted }.map { !it.allCompleted() },
        backgroundScope.coroutineContext,
    )

    init { // after playerLauncher
        launchInBackground {
            // 播放失败时自动切换下一个 media.
            // 即使是 BT 出错, 我们也会尝试切换到下一个 WEB 类型的数据源, 而不是继续尝试 BT.
            settingsRepository.videoScaffoldConfig.flow.map { it.autoSwitchMediaOnPlayerError }
                .collectLatest { autoSwitchMediaOnPlayerError ->
                    if (!autoSwitchMediaOnPlayerError) {
                        // 设置关闭, 不要自动切换
                        return@collectLatest
                    }

                    mediaFetchSession.collectLatest { session ->
                        var blacklistedMediaIds = persistentHashSetOf<String>()
                        combine(
                            playerLauncher.videoLoadingState, // 解析链接出错 (未匹配到链接)
                            playerState.playbackState, // 解析成功, 但播放器出错 (无法链接到链接, 例如链接错误)
                        ) { videoLoadingState, playerState ->
                            videoLoadingState is VideoLoadingState.Failed || playerState == PlaybackState.ERROR
                        }.distinctUntilChanged()
                            .collectLatest { isError ->
                                if (isError) {
                                    // 播放出错了
                                    logger.info { "Player errored, automatically switching to next media" }

                                    // 将当前播放的 mediaId 加入黑名单
                                    mediaSelector.selected.value?.let {
                                        blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId) // thread-safe
                                    }

                                    delay(1.seconds) // 稍等让用户看到播放出错
                                    val result = mediaSelector.autoSelect.fastSelectSources(
                                        session,
                                        mediaSourceManager.allInstances.first() // no need to subscribe to changes
                                            .filter { it.source.kind == MediaSourceKind.WEB }
                                            .map { it.mediaSourceId },
                                        preferKind = settingsRepository.mediaSelectorSettings.flow.map<MediaSelectorSettings, MediaSourceKind?> { it.preferKind },
                                        overrideUserSelection = true, // Note: 覆盖用户选择
                                        blacklistMediaIds = blacklistedMediaIds,
                                        allowNonPreferredFlow = flowOf(true), // 偏好的如果全都播放错误了, 允许播放非偏好的
                                    )
                                    logger.info { "Player errored, automatically switched to next media: $result" }
                                } // else: cancel selection
                            }
                    }
                }
        }
    }


    /**
     * "视频统计" bottom sheet 显示内容
     */
    val videoStatisticsFlow: Flow<VideoStatistics> get() = playerLauncher.videoStatisticsFlow


    /**
     * 是否显示数据源选择器
     */
    var mediaSelectorVisible: Boolean by mutableStateOf(false)
    val videoScaffoldConfig: VideoScaffoldConfig by settingsRepository.videoScaffoldConfig
        .flow.produceState(VideoScaffoldConfig.Default)

    val danmakuRegexFilterState = DanmakuRegexFilterState(
        list = danmakuRegexFilterRepository.flow.produceState(emptyList()),
        add = {
            launchInBackground { danmakuRegexFilterRepository.add(it) }
        },
        edit = { regex, filter ->
            launchInBackground {
                danmakuRegexFilterRepository.update(filter.id, filter.copy(regex = regex))
            }
        },
        remove = {
            launchInBackground { danmakuRegexFilterRepository.remove(it) }
        },
        switch = {
            launchInBackground {
                danmakuRegexFilterRepository.update(it.id, it.copy(enabled = !it.enabled))
            }
        },
    )

    val subjectPresentation: SubjectPresentation by subjectInfo
        .map {
            SubjectPresentation(title = it.displayName, info = it)
        }
        .produceState(SubjectPresentation.Placeholder)

    private val episodePresentationFlow =
        episodeId
            .flatMapLatest { episodeId ->
                episodeCollectionRepository.episodeCollectionInfoFlow(subjectId, episodeId)
            }.combine(subjectCollection) { collection, subject ->
                collection.toPresentation(subject.recurrence)
            }
            .shareInBackground(SharingStarted.Eagerly)

    val episodePresentation: EpisodePresentation by episodePresentationFlow
        .produceState(EpisodePresentation.Placeholder)
    val authState: AuthState = AuthState()

    private val episodeCollectionsFlow = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
        .shareInBackground()

    val episodeDetailsState: EpisodeDetailsState = kotlin.run {
        EpisodeDetailsState(
            episodePresentation = episodePresentationFlow.filterNotNull().produceState(EpisodePresentation.Placeholder),
            subjectInfo = subjectInfo.produceState(SubjectInfo.Empty),
            airingLabelState = AiringLabelState(
                subjectCollection.map { it.airingInfo }.produceState(null),
                subjectCollection.map { it ->
                    SubjectProgressInfo.compute(it.subjectInfo, it.episodes, getCurrentDate(), it.recurrence)
                }
                    .produceState(null),
            ),
            subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope),
        )
    }

    /**
     * 剧集列表
     */
    val episodeCarouselState: EpisodeCarouselState = kotlin.run {
        val episodeCacheStatusListState by episodeCollectionsFlow.flatMapLatest { list ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { collection ->
                    mediaCacheManager.cacheStatusForEpisode(subjectId, collection.episodeId).map {
                        collection.episodeId to it
                    }
                },
            ) {
                it.toList()
            }
        }.produceState(emptyList())

        val collectionButtonEnabled = MutableStateFlow(false)
        EpisodeCarouselState(
            episodes = episodeCollectionsFlow.produceState(emptyList()),
            playingEpisode = episodeId.combine(episodeCollectionsFlow) { id, collections ->
                collections.firstOrNull { it.episodeId == id }
            }.produceState(null),
            cacheStatus = {
                episodeCacheStatusListState.firstOrNull { status ->
                    status.first == it.episodeInfo.episodeId
                }?.second ?: EpisodeCacheStatus.NotCached
            },
            onSelect = {
                switchEpisode(it.episodeInfo.episodeId)
            },
            onChangeCollectionType = { episode, it ->
                collectionButtonEnabled.value = false
                launchInBackground {
                    try {
                        episodeCollectionRepository.setEpisodeCollectionType(
                            subjectId,
                            episodeId = episode.episodeInfo.episodeId,
                            collectionType = it,
                        )
                    } finally {
                        collectionButtonEnabled.value = true
                    }
                }
            },
            backgroundScope = backgroundScope,
        )
    }

    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState =
        EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = subjectCollection
                .map { it.collectionType },
            hasAnyUnwatched = {
                val collections =
                    episodeCollectionsFlow.firstOrNull() ?: return@EditableSubjectCollectionTypeState true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = { subjectCollectionRepository.setSubjectCollectionTypeOrDelete(subjectId, it) },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
            },
            backgroundScope,
        )

    var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)
    var sidebarVisible: Boolean by mutableStateOf(true)
    val commentLazyListState: LazyListState = LazyListState()

    fun switchEpisode(episodeId: Int) {
        savePlayProgress()
        episodeDetailsState.showEpisodes = false // 选择后关闭弹窗
        mediaSelector.unselect() // 否则不会自动选择
        playerState.stop()
        switchEpisodeCompleted.value = false // 要在修改 episodeId 之前才安全, 但会有极小的概率在 fetchSession 更新前有 mediaList 更新
        this.episodeId.value = episodeId // ep 要在取消选择 media 之后才能变, 否则会导致使用旧的 media
    }

    /**
     * 播放器内切换剧集
     */
    val episodeSelectorState: EpisodeSelectorState = EpisodeSelectorState(
        itemsFlow = episodeCollectionsFlow.combine(subjectCollection) { list, subject ->
            list.map {
                it.toPresentation(subject.recurrence)
            }
        },
        onSelect = {
            switchEpisode(it.episodeId)
        },
        currentEpisodeId = episodeId,
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    private val danmakuLoader = DanmakuLoaderImpl(
        requestFlow = mediaFetchSession.transformLatest {
            emit(null)
            emitAll(
                playerState.mediaData.mapLatest {
                    if (it == null) {
                        return@mapLatest null
                    }
                    LoadDanmakuRequest(
                        subjectInfo.first(),
                        episodeInfo.filterNotNull().first(),
                        episodeId.value,
                        null,
                        withContext(Dispatchers.IO_) { it.fileLength() },
                    )
                },
            )
        },
        currentPosition = playerState.currentPositionMillis.map { it.milliseconds },
        danmakuFilterConfig = settingsRepository.danmakuFilterConfig.flow,
        danmakuRegexFilterList = danmakuRegexFilterRepository.flow,
        onFetch = {
            danmakuManager.fetch(it)
        },
        backgroundScope.coroutineContext,
    )

    val danmakuStatistics: DanmakuStatistics = DelegateDanmakuStatistics(
        danmakuLoader.state.produceState(),
    )

    val danmaku = PlayerDanmakuState(
        danmakuEnabled = settingsRepository.danmakuEnabled.flow.produceState(false),
        danmakuConfig = settingsRepository.danmakuConfig.flow.produceState(DanmakuConfig.Default),
        onSend = { info ->
            danmakuManager.post(episodeId.value, info)
        },
        onSetEnabled = {
            settingsRepository.danmakuEnabled.set(it)
        },
        onHideController = {
            playerControllerState.toggleFullVisible(false)
        },
        backgroundScope,
    )

    val episodeCommentState: CommentState = CommentState(
        list = episodeId.flatMapLatest { episodeId ->
            bangumiCommentRepository.subjectEpisodeCommentsPager(episodeId)
                .map { page ->
                    page.map { it.parseToUIComment() }
                }
        }.cachedIn(backgroundScope),
        countState = stateOf(null),
        onSubmitCommentReaction = { _, _ -> },
        backgroundScope = backgroundScope,
    )

    val commentEditorState: CommentEditorState = CommentEditorState(
        showExpandEditCommentButton = true,
        initialEditExpanded = false,
        panelTitle = subjectInfo
            .combine(episodeInfo) { sub, epi -> "${sub.displayName} ${epi?.renderEpisodeEp()}" }
            .produceState(null),
        stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
            .produceState(emptyList()),
        richTextRenderer = { text ->
            withContext(Dispatchers.Default) {
                with(CommentMapperContext) { parseBBCode(text) }
            }
        },
        onSend = { context, content ->
            when (context) {
                is CommentContext.Episode ->
                    bangumiCommentService.postEpisodeComment(episodeId.value, content)

                is CommentContext.Reply ->
                    bangumiCommentService.postEpisodeComment(episodeId.value, content, context.commentId)

                is CommentContext.SubjectReview -> {} // TODO: send subject comment
            }
        },
        backgroundScope = backgroundScope,
    )

    val playerSkipOpEdState: PlayerSkipOpEdState = PlayerSkipOpEdState(
        chapters = playerState.chapters.produceState(),
        onSkip = {
            playerState.seekTo(it)
        },
        videoLength = playerState.videoProperties.mapNotNull { it?.durationMillis?.milliseconds }
            .produceState(0.milliseconds),
    )

    fun refreshFetch() {
        mediaFetchSession.replayCache.firstOrNull()?.restartAll()
    }

    fun stopPlaying() {
        // 退出播放页前保存播放进度
        savePlayProgress()
        playerState.stop()
        mediaSelector.unselect()
    }

    private val selfUserId = danmakuManager.selfId

    init {
        launchInMain { // state changes must be in main thread
            playerState.playbackState.collect {
                danmaku.danmakuHostState.setPaused(!it.isPlaying)
            }
        }

        launchInBackground {
            cancellableCoroutineScope {
                val selfId = selfUserId.stateIn(this)
                danmakuLoader.eventFlow.collect { event ->
                    when (event) {
                        is DanmakuEvent.Add -> {
                            val data = event.danmaku
                            if (data.text.isBlank()) return@collect
                            danmaku.danmakuHostState.trySend(
                                createDanmakuPresentation(data, selfId.value),
                            )
                        }

                        is DanmakuEvent.Repopulate -> {
                            danmaku.danmakuHostState.repopulate(
                                event.list
                                    .filter { it.text.any { c -> !c.isWhitespace() } }
                                    .map { createDanmakuPresentation(it, selfId.value) },
                            )

                        }
                    }
                }
                cancelScope()
            }
        }

        // 自动标记看完
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoMarkDone }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用

                    mediaFetchSession.collectLatest {
                        cancellableCoroutineScope {
                            combine(
                                playerState.currentPositionMillis.sampleWithInitial(5000),
                                playerState.videoProperties.map { it?.durationMillis }.debounce(5000),
                                playerState.playbackState,
                            ) { pos, max, playback ->
                                if (max == null || !playback.isPlaying) return@combine
                                if (episodePresentationFlow.first().collectionType == UnifiedCollectionType.DONE) {
                                    cancelScope() // 已经看过了
                                }
                                if (pos > max.toFloat() * 0.9) {
                                    logger.info { "观看到 90%, 标记看过" }
                                    suspend {
                                        episodeCollectionRepository.setEpisodeCollectionType(
                                            subjectId,
                                            episodeId.value,
                                            UnifiedCollectionType.DONE,
                                        )
                                    }.asFlow().retryWithBackoffDelay().first()
                                    cancelScope() // 标记成功一次后就不要再检查了
                                }
                            }.collect()
                        }
                    }
                }
        }

        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoPlayNext }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    playerState.playbackState.collect { playback ->
                        if (playback == PlaybackState.FINISHED
                            && playerState.videoProperties.value.let { prop ->
                                prop != null && prop.durationMillis > 0L && prop.durationMillis - playerState.currentPositionMillis.value < 5000
                            }
                        ) {
                            logger.info("播放完毕，切换下一集")
                            launchInMain {// state changes must be in main thread
                                episodeSelectorState.takeIf { it.hasNextEpisode }?.selectNext()
                            }
                        }
                    }
                }
        }

        // 跳过 OP 和 ED
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoSkipOpEd }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用
                    combine(
                        playerState.currentPositionMillis.sampleWithInitial(1000),
                        episodeId,
                        episodeCollectionsFlow,
                    ) { pos, id, collections ->
                        // 不止一集并且当前是第一集时不跳过
                        if (collections.size > 1 && collections.getOrNull(0)?.episodeId == id) return@combine
                        playerSkipOpEdState.update(pos)
                    }.collect()
                }
        }

        launchInBackground {
            mediaSelector.events.onBeforeSelect.collect {
                // 切换 数据源 前保存播放进度
                savePlayProgress()
            }
        }
        launchInBackground {
            playerState.playbackState.collect {
                when (it) {
                    // 加载播放进度
                    PlaybackState.READY -> {
                        val positionMillis =
                            episodePlayHistoryRepository.getPositionMillisByEpisodeId(episodeId = episodeId.value)
                        if (positionMillis == null) {
                            logger.info { "Did not find saved position" }
                        } else {
                            logger.info { "Loaded saved position: $positionMillis, waiting for video properties" }
                            playerState.videoProperties.filter { it != null && it.durationMillis > 0L }.firstOrNull()
                            logger.info { "Loaded saved position: $positionMillis, video properties ready, seeking" }
                            withContext(Dispatchers.Main) { // android must call in main thread
                                playerState.seekTo(positionMillis)
                            }
                        }
                    }

                    PlaybackState.PAUSED -> savePlayProgress()

                    PlaybackState.FINISHED -> {
                        if (playerState.videoProperties.value.let { it != null && it.durationMillis > 0L }) {
                            // 视频长度有效, 说明正常播放中
                            episodePlayHistoryRepository.remove(episodeId.value)
                        } else {
                            // 视频加载失败或者在切换数据源时又切换了另一个数据源, 不要删除记录
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun createDanmakuPresentation(
        data: Danmaku,
        selfId: String?,
    ) = DanmakuPresentation(
        data,
        isSelf = selfId == data.senderId,
    )

    override fun onCleared() {
        super.onCleared()
        stopPlaying()
    }
}

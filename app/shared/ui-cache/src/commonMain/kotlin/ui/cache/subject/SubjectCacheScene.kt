/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.media.cache.DeleteCacheByEpisodeIdUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequesterImpl
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.cache.ForcedDarkTheme
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.PlayerFrameHolder
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.lists.ScrollStateVerticalScrollbar
import me.him188.ani.app.ui.foundation.lists.hasScrollableContent
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.coroutines.flows.combine
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration

@Stable
interface SubjectCacheViewModel {
    val subjectId: Int
    val subjectTitle: String?

    val mediaSelectorSettingsFlow: Flow<MediaSelectorSettings>

    /**
     * 单个条目的缓存管理页面的状态
     */
    val cacheListState: EpisodeCacheListState

    val mediaSourceInfoProvider: MediaSourceInfoProvider

    /** TV: 播放器跳转来时的暂停帧 (页面背景); 其他入口为 null. 见 [PlayerFrameHolder]. */
    val pausedFrame: ImageBitmap? get() = null
}

@Stable
class SubjectCacheViewModelImpl(
    override val subjectId: Int,
) : AbstractViewModel(), KoinComponent, SubjectCacheViewModel {
    // 在 VM (路由级生命周期) 而非组合里消费一次性的暂停帧: 页面从更深的返回栈页面
    // (管理全部缓存) 回来时会整个重新组合, remember { take() } 会重跑而帧已被消费,
    // 背景就丢了 (退回浅色白底). VM 存活于返回栈, 帧随路由退出一起销毁.
    override val pausedFrame = PlayerFrameHolder.take()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val cacheManager: MediaCacheManager by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()
    private val danmakuRepository: DanmakuRepository by inject()
    private val deleteCacheByEpisodeIdUseCase: DeleteCacheByEpisodeIdUseCase by inject()

    private val subjectInfoFlow = subjectCollectionRepository.subjectCollectionFlow(subjectId)
        .retryWithBackoffDelay()
        .shareInBackground()
    override val subjectTitle by subjectInfoFlow.map { it.subjectInfo.nameCnOrName }.produceState(null)
    override val mediaSelectorSettingsFlow: Flow<MediaSelectorSettings> get() = settingsRepository.mediaSelectorSettings.flow

    private val episodeCollectionsFlow =
        episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
            .retryWithBackoffDelay()
            .shareInBackground()

    private val episodesFlow =
        episodeCollectionsFlow.take(1).combineTransform(subjectInfoFlow) { episodes, subjectInfo ->
            supervisorScope {
                // 每个 episode 都为一个 flow, 然后合并
                emit(
                    episodes.map { episodeCollection ->
                        val episode = episodeCollection.episodeInfo

                        val cacheStatusFlow = cacheManager.cacheStatusForEpisode(subjectId, episode.episodeId)

                        val cacheRequester = EpisodeCacheRequester(
                            mediaSourceManager.mediaFetcher,
                            MediaSelectorFactory.withKoin(),
                            storagesLazy = cacheManager.enabledStorages,
                        )
                        EpisodeCacheState(
                            episodeId = episode.episodeId,
                            cacheRequester = cacheRequester,
                            currentStageState = cacheRequester.stage.produceState(scope = this),
                            infoState = stateOf(
                                EpisodeCacheInfo(
                                    sort = episode.sort,
                                    ep = episode.ep,
                                    title = episode.displayName,
                                    watchStatus = episodeCollection.collectionType,
                                    hasPublished = episode.isKnownCompleted(subjectInfo.recurrence),
                                ),
                            ),
                            cacheStatusState = cacheStatusFlow.produceState(null, this),
                            backgroundScope = this,
                        )
                    },
                )
            }
        }.shareInBackground()

    /**
     * 单个条目的缓存管理页面的状态
     */
    override val cacheListState: EpisodeCacheListState = EpisodeCacheListStateImpl(
        episodes = episodesFlow.produceState(emptyList()),
        currentEpisode = episodesFlow.flatMapLatest { episodes ->
            combine(
                episodes.map { episodeCacheState ->
                    episodeCacheState.cacheRequester.stage.map { episodeCacheState to it }
                },
            ) { results ->
                results.firstOrNull { (_, stage) ->
                    stage is CacheRequestStage.Working
                }?.first
            }
        }.produceState(null),
        onRequestCache = { episode, autoSelectByCached ->
            val subjectInfo = subjectInfoFlow.first().subjectInfo
            episode.cacheRequester.request(
                EpisodeCacheRequest(
                    subjectInfo,
                    episodeCollectionRepository.episodeCollectionInfoFlow(subjectInfo.subjectId, episode.episodeId)
                        .map { it.episodeInfo }.first(),
                ),
            ).run {
                if (autoSelectByCached) {
                    tryAutoSelectByCachedSeason(
                        cacheManager.listCacheForSubject(subjectId).first(),
                    )
                } else this
            }
        },
        onRequestCacheComplete = { target ->
            val episodeInfo = episodeCollectionsFlow.first().firstOrNull { it.episodeId == target.episode.episodeId }
                ?: error(
                    "Episode ${target.episode} not found from episodes: ${
                        episodeCollectionsFlow.first().joinToString { it.episodeId.toString() }
                    }",
                )

            val cache = target.storage.cache(
                target.media, target.metadata,
                episodeInfo.episodeInfo.toEpisodeMetadata(),
            )
            danmakuRepository.cacheDanmakuIfNeeded(target.toDanmakuFetchRequest(cache))
            Analytics.recordEvent(CacheCreate) {
                val subjectInfo = subjectInfoFlow.first()
                put("subject_id", subjectInfo.subjectId)
                put("episode_id", episodeInfo.episodeId)
                put(
                    "media_source_name",
                    when (target.media.kind) {
                        MediaSourceKind.WEB -> "web"
                        MediaSourceKind.BitTorrent -> "bt"
                        MediaSourceKind.LocalCache -> null // impossible
                    },
                )
            }
        },
        onDeleteCache = { episode ->
            deleteCacheByEpisodeIdUseCase(subjectId, episode.episodeId)
        },
    )
    override val mediaSourceInfoProvider: MediaSourceInfoProvider = MediaSourceInfoProvider(
        getSourceInfoFlow = {
            mediaSourceManager.infoFlowByMediaSourceId(it)
        },
    )

    init {
        launchInBackground {
            val firstWorkingEpisode = episodesFlow
                .flatMapLatest { list ->
                    list.map { state -> state.cacheRequester.stage.map { state to it } }
                        .combine {
                            it.firstNotNullOfOrNull { (state, stage) ->
                                if (stage is CacheRequestStage.Working || stage is CacheRequestStage.Done) {
                                    state
                                } else null
                            }
                        }
                }

            firstWorkingEpisode
                .mapLatest { episodeCacheState ->
                    episodeCacheState ?: return@mapLatest

                    // 请求缓存下一集时会 cancel 这个 scope
                    coroutineScope {
                        var job: Job? = null
                        episodeCacheState.cacheRequester.stage.collect { stage ->
                            when (stage) {
                                is EpisodeCacheRequesterImpl.SelectMedia -> {
                                    job?.cancel()
                                    job = null
                                    job = launch {
                                        stage.mediaSelector.eventHandling.savePreferenceOnSelect {
                                            episodePreferencesRepository.setMediaPreference(subjectId, it)
                                        }
                                    }
                                }

                                is EpisodeCacheRequesterImpl.SelectStorage -> {}
                                is CacheRequestStage.Done -> {} // SelectMedia 之后可能会立即到 Done, 还没来得及保存, 所以不能 cancel job
                                CacheRequestStage.Idle -> {
                                }
                            }
                        }
                    }
                }.collect()
        }
    }

    private suspend fun EpisodeCacheTargetInfo.toDanmakuFetchRequest(cache: MediaCache): DanmakuFetchRequest {
        return DanmakuFetchRequest(
            subjectId = request.subjectInfo.subjectId,
            subjectPrimaryName = request.subjectInfo.displayName,
            subjectNames = request.subjectInfo.allNames,
            subjectPublishDate = request.subjectInfo.airDate,
            episodeId = request.episodeInfo.episodeId,
            episodeSort = request.episodeInfo.sort,
            episodeEp = request.episodeInfo.ep,
            episodeName = request.episodeInfo.displayName,
            filename = media.originalTitle,
            fileSize = cache.fileStats.first().totalSize.inBytes,
            fileHash = null,
            videoDuration = Duration.ZERO,
        )
    }
}

@Composable
fun SubjectCacheScreen(
    vm: SubjectCacheViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    SubjectCachePageScaffold(
        title = {
            val title = vm.subjectTitle
            Text(
                title.orEmpty(), Modifier.placeholder(title == null),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        autoCacheGroup = {
            val navigator = LocalNavigator.current
            AutoCacheGroup {
                navigator.navigateCaches()
            }
        },
        cacheListGroup = {
            EpisodeCacheListGroup(
                vm.cacheListState,
                vm.mediaSourceInfoProvider,
                mediaSelectorSettingsProvider = {
                    vm.mediaSelectorSettingsFlow
                },
            )
        },
        modifier,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
        pausedFrame = vm.pausedFrame,
    )
}

/**
 * 条目缓存页面的布局框架
 *
 * @param title 顶部的标题
 * @param autoCacheGroup 自动缓存设置
 * @param cacheListGroup 管理该条目的所有剧集的缓存情况
 */
@Composable
fun SubjectCachePageScaffold(
    title: @Composable () -> Unit,
    autoCacheGroup: @Composable SettingsScope.() -> Unit,
    cacheListGroup: @Composable SettingsScope.() -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
    /**
     * TV: 播放器跳转来时的暂停帧, 加半透明遮罩作页面背景; null 走普通背景
     * (TV 上是深色纯底, 见 [ForcedDarkTheme]).
     * 由调用方从 VM 取 (路由级生命周期) —— 在组合里 take() 的话, 从更深页面返回时
     * 重组重跑而帧已被消费, 背景会丢.
     */
    pausedFrame: ImageBitmap? = null,
) {
    // TV 上整页无条件强制深色 (与管理全部缓存/缓存详情一致), 不依赖帧是否存在:
    // 帧捕获可能失败 (surface 无效/PixelCopy 失败返回 null), 依赖帧判断会漏出浅色白底
    ForcedDarkTheme {
        Box(modifier) {
            if (pausedFrame != null) {
                Image(
                    pausedFrame,
                    contentDescription = null,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = TV_CACHE_FRAME_SCRIM_ALPHA)))
            }
            SubjectCachePageScaffoldImpl(
                title = title,
                autoCacheGroup = autoCacheGroup,
                cacheListGroup = cacheListGroup,
                transparentBackground = pausedFrame != null,
                windowInsets = windowInsets,
                navigationIcon = navigationIcon,
            )
        }
    }
}

/** 暂停帧背景上的遮罩浓度 (内容可读性). */
private const val TV_CACHE_FRAME_SCRIM_ALPHA = 0.75f

@Composable
private fun SubjectCachePageScaffoldImpl(
    title: @Composable () -> Unit,
    autoCacheGroup: @Composable SettingsScope.() -> Unit,
    cacheListGroup: @Composable SettingsScope.() -> Unit,
    transparentBackground: Boolean,
    windowInsets: WindowInsets,
    navigationIcon: @Composable () -> Unit,
) {
    val appBarColors = AniThemeDefaults.topAppBarColors().let {
        if (transparentBackground) it.copy(containerColor = Color.Transparent) else it
    }
    val scrollState = rememberScrollState()
    val scrollbarEndPadding = if (scrollState.hasScrollableContent()) 16.dp else 0.dp
    Scaffold(
        Modifier.fillMaxSize(),
        topBar = {
            WindowDragArea {
                TopAppBar(
                    title = {
                        title()
                    },
                    navigationIcon = navigationIcon,
                    colors = appBarColors,
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            }
        },
        containerColor = if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        // 透明容器推导不出内容色 (contentColorFor(Transparent) = Unspecified), 会沿用外层
        // 的 LocalContentColor —— 暂停帧背景包了强制深色主题, 但外层是浅色时文字仍是黑的.
        // 显式取当前主题的 onBackground (普通分支下与默认推导一致, 无行为变化)
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = scrollbarEndPadding)) {
//                Surface(Modifier.fillMaxWidth(), color = appBarColors.containerColor) {
//                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
//                        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
//                            title()
//                        }
//                    }
//                }

                SettingsTab {
                    Spacer(Modifier.fillMaxWidth()) // tab has spacedBy arrangement
                    autoCacheGroup()
                    cacheListGroup()
                    Spacer(Modifier.fillMaxWidth()) // tab has spacedBy arrangement
                }

                Spacer(Modifier.windowInsetsBottomHeight(windowInsets))
            }

            ScrollStateVerticalScrollbar(
                state = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }
}

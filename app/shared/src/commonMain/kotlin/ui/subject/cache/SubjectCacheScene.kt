/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.cache

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequesterImpl
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.utils.coroutines.flows.combine
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
}

@Stable
class SubjectCacheViewModelImpl(
    override val subjectId: Int,
) : AbstractViewModel(), KoinComponent, SubjectCacheViewModel {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val cacheManager: MediaCacheManager by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()

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
            target.storage.cache(target.media, target.metadata)
        },
        onDeleteCache = { episode ->
            val episodeId = episode.episodeId.toString()
            cacheManager.deleteFirstCache {
                it.metadata.episodeId == episodeId
            }
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
            AutoCacheGroup(
                onClickGlobalCacheSettings = {
                    navigator.navigateSettings(SettingsTab.CACHE)
                },
                onClickGlobalCacheManage = {
                    navigator.navigateCaches()
                },
            )
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
) {
    val appBarColors = AniThemeDefaults.topAppBarColors()
    Scaffold(
        modifier,
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
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).verticalScroll(rememberScrollState())) {
//            Surface(Modifier.fillMaxWidth(), color = appBarColors.containerColor) {
//                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
//                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
//                        title()
//                    }
//                }
//            }

            SettingsTab {
                Spacer(Modifier.fillMaxWidth()) // tab has spacedBy arrangement
                autoCacheGroup()
                cacheListGroup()
                Spacer(Modifier.fillMaxWidth()) // tab has spacedBy arrangement
            }
        }
    }
}

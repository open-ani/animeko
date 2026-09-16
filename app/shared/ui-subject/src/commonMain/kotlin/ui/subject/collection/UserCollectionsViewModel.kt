/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.preference.MyCollectionsSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.datasources.api.topic.toggleCollected
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class UserCollectionsViewModel : AbstractViewModel(), KoinComponent {
    lateinit var navigator: AniNavigator

    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val episodeProgressRepository: EpisodeProgressRepository by inject()
    private val animeScheduleRepository: AnimeScheduleRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase by inject()
    private val setEpisodeCollectionTypeUseCase: SetEpisodeCollectionTypeUseCase by inject()

    val lazyGridState = LazyGridState()

    private val subjectProgressStateFactory: SubjectProgressStateFactory = SubjectProgressStateFactory(
        episodeProgressRepository,
    )

    val myCollectionsSettings: MyCollectionsSettings by settingsRepository.uiSettings.flow
        .map { it.myCollections }
        .produceState(MyCollectionsSettings.Default)

    private val fullSyncTasker = MonoTasker(backgroundScope)
    val fullSyncState: MutableStateFlow<BangumiSyncState?> = MutableStateFlow(null)

    /**
     * 重启各类型收藏数量流 (tab 标题的数量). 数量只在登录时拉取一次, 缓存失效 / 换账号后要重新拉取, 否则标题与刷新后的列表对不上.
     */
    private val countsRestarter = FlowRestarter()

    val state = UserCollectionsState(
        startSearch = { subjectCollectionRepository.subjectCollectionsPager(it) },
        collectionCountsState = subjectCollectionRepository.subjectCollectionCountsFlow()
            .restartable(countsRestarter)
            .produceState(null),
        subjectProgressStateFactory,
        createEditableSubjectCollectionTypeState = { createEditableSubjectCollectionTypeState(it) },
        backgroundScope,
    )

    // 必须用 Kotlin init 块而不是 AbstractViewModel.init(): 后者只在实例被 compose remember 时 (onRemembered) 调用,
    // 而本 ViewModel 由 androidx viewModel {} 取得, 不会被 remember, init() 永远不会执行.
    // 放在 state 之后, 保证收集回调里用到的 state 已初始化 (backgroundScope 由父类构造器创建, 可用).
    init {
        launchInBackground {
            sessionStateProvider.eventFlow.filter { it is SessionEvent.NewLogin }.collectLatest {
                logger.info { "登录信息变更, 清空缓存" }
                // 如果有变更登录, 清空缓存
                refreshCollections()
            }
        }

        launchInBackground {
            // 服务端改写了收藏 (解决 Bangumi 冲突 / 全量同步自动合并) 后本地缓存被失效:
            // 已创建的分页器不会自动重新拉取 (只在创建时判断是否刷新), 这里重建它, 并重新拉取数量.
            subjectCollectionRepository.collectionsInvalidated.collect {
                logger.info { "收藏缓存已失效, 刷新列表" }
                refreshCollections()
            }
        }
    }

    /**
     * 重建收藏列表分页器 (从服务端刷新) 并重新拉取各类型的收藏数量.
     */
    private fun refreshCollections() {
        state.refresh()
        countsRestarter.restart()
    }

    private fun createEditableSubjectCollectionTypeState(collection: SubjectCollectionInfo): EditableSubjectCollectionTypeState =
        // 必须不能有后台持续任务
        EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = flowOf(collection.collectionType),
            hasAnyUnwatched = hasAnyUnwatched@{
                val collections =
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(collection.subjectId)
                        .firstOrNull() ?: return@hasAnyUnwatched true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = { setSubjectCollectionTypeOrDeleteUseCase(collection.subjectId, it) },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(collection.subjectId)
            },
            backgroundScope,
        )

    suspend fun toggleEpisodeCollection(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType
    ): LoadError? = LoadError.runAndWrapOrThrowCancellation {
        setEpisodeCollectionTypeUseCase(
            subjectId,
            episodeId,
            collectionType.toggleCollected(),
        )
    }
}

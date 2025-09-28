/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.*
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.restartOnNewLogin
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * 用户正在追的条目仓库
 */
class FollowedSubjectsRepository(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
//    private val subjectProgressRepository: EpisodeProgressRepository,
//    private val subjectCollectionDao: SubjectCollectionDao,
    private val sessionManager: SessionStateProvider,
    settingsRepository: SettingsRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    private val nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    private fun followedSubjectsFlow(
        updatePeriod: Duration = 1.hours,
    ): Flow<List<FollowedSubjectInfo>> {
        require(updatePeriod > Duration.ZERO) { "updatePeriod must be positive" }

        val ticker = flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(updatePeriod)
            }
        }

        val now = PackedDate.now()

        // 对于最近看过的一些条目
        return ticker.flatMapLatest {
            try {
                subjectCollectionRepository.updateRecentlyUpdatedSubjectCollections(
                    30, // should be enough
                    UnifiedCollectionType.DOING,
                ) // refresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val displayE = when (e) {
                    is RepositoryUnknownException -> e
                    is RepositoryException -> null
                    else -> e
                }

                logger.error(displayE) { """Failed to update recently updated subject collections due to ${e}, ignoring. 这只会导致探索页的继续观看栏目可能显示旧结果. """ }
            }

            // 先查询完成 (插入数据库) 再返回 flow 去查数据库. 前端会展示 placeholder 所以延迟没问题.

            subjectCollectionRepository.mostRecentlyUpdatedSubjectCollectionsFlow(
                limit = 64,
                types = listOf(
                    UnifiedCollectionType.DOING,
                ),
            ).flatMapLatest { subjectCollectionInfoList ->
                // 对于每个条目, 获取其最新的集数信息
                if (subjectCollectionInfoList.isEmpty()) { // `combine(emptyList)` does not emit
                    return@flatMapLatest flowOf(emptyList())
                }
                getFollowedSubjectInfoFlows(subjectCollectionInfoList, now)
            }.map { followedSubjectInfoList ->
                followedSubjectInfoList
                    .toMutableList()
                    .apply {
                        sortWith(sorter)
                    }
            }.catch {
                throw RepositoryException.wrapOrThrowCancellation(it)
            }
        }.flowOn(defaultDispatcher)
    }

    private fun getFollowedSubjectInfoFlows(
        subjectCollectionInfoList: List<SubjectCollectionInfo>,
        now: PackedDate,
    ): Flow<List<FollowedSubjectInfo>> = combine(
        subjectCollectionInfoList.map { info ->
            episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId)
        },
    ) { array ->
        array.toList()
    }.combine(nsfwModeSettingsFlow) { epInfoLists, nsfwMode ->
        subjectCollectionInfoList.asSequence().zip(epInfoLists.asSequence()) { subjectCollectionInfo, episodes ->
            // 计算每个条目的播放进度
            FollowedSubjectInfo(
                subjectCollectionInfo,
                SubjectAiringInfo.computeFromEpisodeList(
                    episodes.map { it.episodeInfo },
                    subjectCollectionInfo.subjectInfo.airDate,
                    subjectCollectionInfo.recurrence,
                ),
                SubjectProgressInfo.compute(
                    subjectCollectionInfo.subjectInfo,
                    episodes,
                    now,
                    subjectCollectionInfo.recurrence,
                ),
                nsfwMode =
                    if (subjectCollectionInfo.subjectInfo.nsfw) nsfwMode
                    else NsfwMode.DISPLAY,
            )
        }.toList()
    }.flowOn(defaultDispatcher)

    fun followedSubjectsPager(
        updatePeriod: Duration = 1.hours,
    ) = followedSubjectsFlow(updatePeriod)
        .restartOnNewLogin(sessionManager)
        .map {
            PagingData.from(
                it,
                NotLoading,
            )
        }.flowOn(defaultDispatcher)

    private companion object {
        private val NotLoading = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        )

        val sorter: Comparator<FollowedSubjectInfo> =
            // 不要用最后访问时间排序, 因为刷新后时间会乱
            compareByDescending<FollowedSubjectInfo> { info ->
                // 1. 现在可以看的 > 现在不能看的
                info.subjectProgressInfo.hasNewEpisodeToPlay
            }.thenByDescending { info ->
                // 2. 在看 > 想看
                info.subjectCollectionInfo.collectionType == UnifiedCollectionType.DOING
            }.thenByDescending { info ->
                // 3. 最后播放时间降序
                info.subjectCollectionInfo.lastUpdated
            }.thenByDescending { info ->
                // 4. (已经看了的 sort - first sort) 降序
                val firstEp = info.subjectCollectionInfo.episodes.firstOrNull()?.episodeInfo?.sort
                val firstDone =
                    info.subjectCollectionInfo.episodes.firstOrNull { it.collectionType == UnifiedCollectionType.DONE }
                        ?.episodeInfo?.sort
                if (firstEp != null && firstDone != null) {
                    firstDone.compareTo(firstEp)
                } else {
                    Int.MIN_VALUE
                }
            }

    }
}


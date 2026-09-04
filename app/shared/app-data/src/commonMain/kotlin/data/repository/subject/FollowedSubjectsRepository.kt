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
import me.him188.ani.app.data.models.subject.*
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.restartOnNewLogin
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
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
//    private val subjectProgressRepository: EpisodeProgressRepository,
//    private val subjectCollectionDao: SubjectCollectionDao,
    private val sessionManager: SessionStateProvider,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
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

        // 对于最近看过的一些条目
        return ticker.flatMapLatest {
            try {
                // 必须与下面查本地的 limit 一致, 否则中间那段永远拿不到新播出的剧集
                subjectCollectionRepository.updateRecentlyUpdatedSubjectCollections(
                    FOLLOWED_SUBJECTS_LIMIT,
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
                limit = FOLLOWED_SUBJECTS_LIMIT,
                types = listOf(
                    UnifiedCollectionType.DOING,
                ),
            ).map { subjectCollectionInfoList ->
                toFollowedSubjectInfos(subjectCollectionInfoList)
                    .toMutableList()
                    .apply {
                        sortWith(sorter)
                    }
            }
                // 失败不能让异常抛穿: 上层 cachedIn 的收集协程一旦死掉, 这一栏就停在旧快照直到重启
                .retryWithBackoffDelay { e, _ ->
                    if (e is CancellationException) throw e
                    logger.error(e) { "Failed to collect followed subjects, retrying. 这只会导致探索页的继续观看栏目短暂显示旧结果." }
                    true
                }
        }.flowOn(defaultDispatcher)
    }

    private fun toFollowedSubjectInfos(
        subjectCollectionInfoList: List<SubjectCollectionInfo>,
    ): List<FollowedSubjectInfo> = subjectCollectionInfoList.map { subjectCollectionInfo ->
        FollowedSubjectInfo(
            subjectCollectionInfo,
            // SubjectCollectionInfo 里已按同样的参数算好, 直接复用
            subjectCollectionInfo.airingInfo,
            subjectCollectionInfo.progressInfo,
            nsfwMode = subjectCollectionInfo.nsfwMode,
        )
    }

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
        /**
         * 服务器刷新与本地查询共用, 两者必须一致.
         */
        private const val FOLLOWED_SUBJECTS_LIMIT = 64

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


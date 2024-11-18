/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.models.episode.EpisodeProgressInfo
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.renderEpAndSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.toLocalDateOrNull
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.CoroutineContext

// 在 VM 中创建
@Stable
class SubjectProgressStateFactory(
    private val episodeProgressRepository: EpisodeProgressRepository,
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
) {
//    fun subjectCollection(subjectId: Int) =
//        subjectManager.subjectCollectionFlow(subjectId)
//            .flowOn(flowCoroutineContext)

    fun episodeProgressInfoList(subjectId: Int) = episodeProgressRepository
        .subjectEpisodeProgressesInfoFlow(subjectId)
        .flowOn(flowCoroutineContext)
}

/**
 * 为特定条目创建一个 [SubjectProgressState], 绑定到当前 composition
 */
@Composable
fun SubjectProgressStateFactory.rememberSubjectProgressState(
    subjectCollection: SubjectCollectionInfo,
): SubjectProgressState {
    val subjectId: Int = subjectCollection.subjectId
    val subjectCollectionState by rememberUpdatedState(subjectCollection)
    val info = remember {
        derivedStateOf {
            SubjectProgressInfo.compute(
                subjectCollectionState.subjectInfo,
                subjectCollectionState.episodes,
                getCurrentDate(),
            )
        }
    }
    val episodeProgressInfoList = remember(subjectId) { episodeProgressInfoList(subjectId) }
        .collectAsStateWithLifecycle(emptyList())
    val navigator = LocalNavigator.current
    return remember(info, this, subjectId) {
        SubjectProgressState(
            info,
            episodeProgressInfoList,
        )
    }
}

/**
 * 条目的观看进度, 用于例如 "看到 12" 的按钮
 */
@Stable // Test: AiringProgressTests
class SubjectProgressState(
    info: State<SubjectProgressInfo?>,
    episodeProgressInfos: State<List<EpisodeProgressInfo>>,
    private val weekFormatter: WeekFormatter = WeekFormatter.System,
) {
    private val episodeProgressInfos by episodeProgressInfos

    @Stable
    fun episodeCacheStatus(episodeId: Int): EpisodeCacheStatus? {
        return episodeProgressInfos.find { it.episode.episodeId == episodeId }?.cacheStatus
    }

    private val continueWatchingStatus by derivedStateOf {
        info.value?.continueWatchingStatus
    }

    /**
     * 是否拥有至少一话, 并且已经观看了这一话, 并且没有更新的了.
     */
    val isLatestEpisodeWatched by derivedStateOf {
        continueWatchingStatus is ContinueWatchingStatus.Watched
    }

    /**
     * 已经完结并且看完了
     */
    val isDone by derivedStateOf {
        continueWatchingStatus == ContinueWatchingStatus.Done
    }

    val episodeIdToPlay: Int? by derivedStateOf {
        info.value?.nextEpisodeIdToPlay
    }

    val buttonText by derivedStateOf {
        when (val s = continueWatchingStatus) {
            is ContinueWatchingStatus.Continue -> "继续观看 ${renderEpAndSort(s.episodeEp, s.episodeSort)}"
            ContinueWatchingStatus.Done -> "已看完"
            is ContinueWatchingStatus.NotOnAir -> {
                val date = s.airDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    "${week}开播"
                } else {
                    "还未开播"
                }
            }

            ContinueWatchingStatus.Start -> "开始观看"
            is ContinueWatchingStatus.Watched -> {
                val date = s.nextEpisodeAirDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    "${week}更新"
                } else {
                    "看过 ${renderEpAndSort(s.episodeEp, s.episodeSort)}"
                }
            }

            null -> "未知"
        }
    }

    val buttonIsPrimary by derivedStateOf {
        when (continueWatchingStatus) {
            is ContinueWatchingStatus.Start,
            is ContinueWatchingStatus.Continue -> true

            else -> false
        }
    }
}

@Composable
@TestOnly
fun rememberTestSubjectProgressState(
    info: SubjectProgressInfo = SubjectProgressInfo.Done,
): SubjectProgressState {
    return remember {
        createTestSubjectProgressState(info)
    }
}

@TestOnly
fun createTestSubjectProgressState(info: SubjectProgressInfo = SubjectProgressInfo.Done) =
    SubjectProgressState(
        info = stateOf(info),
        episodeProgressInfos = mutableStateOf(emptyList()),
    )

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.datasources.api.PackedDate
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
    val episodeProgressInfoList = remember(subjectId) { episodeProgressInfoList(subjectId) }
        .collectAsStateWithLifecycle(emptyList())

    val info = remember {
        derivedStateOf {
            SubjectProgressInfo.compute(
                subjectCollectionState.subjectInfo,
                subjectCollectionState.episodes,
                getCurrentDate(),
                recurrence = subjectCollection.recurrence,
            )
        }
    }

    return remember(info, this, subjectId) {
        SubjectProgressState(
            info,
        )
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
    )

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeProgressInfo
import me.him188.ani.app.data.models.episode.EpisodeProgressItem
import me.him188.ani.app.data.models.episode.isKnownOnAir
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.subject.SubjectManager
import me.him188.ani.app.data.models.subject.setEpisodeWatched
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import kotlin.coroutines.CoroutineContext

@Stable
class EpisodeListStateFactory(
    settingsRepository: SettingsRepository,
    private val subjectManager: SubjectManager,
    val backgroundScope: CoroutineScope,
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
) {
    val theme = settingsRepository.uiSettings.flow.map { it.episodeProgress.theme }
        .flowOn(flowCoroutineContext)

    fun episodes(subjectId: Int) =
        subjectManager.subjectProgressFlow(subjectId)
            .flowOn(flowCoroutineContext)

    suspend fun onSetEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) {
        subjectManager.setEpisodeWatched(subjectId, episodeId, watched)
    }
}

@Composable
fun EpisodeListStateFactory.rememberEpisodeListState(
    subjectId: Int,
): EpisodeListState {
    val theme = theme.collectAsStateWithLifecycle(EpisodeListProgressTheme.Default)
    val episodes = remember(this, subjectId) { episodes(subjectId) }
        .collectAsStateWithLifecycle(emptyList())

    val subjectIdState = rememberUpdatedState(subjectId)
    return remember(this) {
        EpisodeListState(subjectIdState, theme, episodes, ::onSetEpisodeWatched, backgroundScope)
    }
}

@Stable
class EpisodeListState(
    subjectId: State<Int>,
    theme: State<EpisodeListProgressTheme>,
    episodeProgressInfoList: State<List<EpisodeProgressInfo>>,
    private val onSetEpisodeWatched: suspend (subjectId: Int, episodeId: Int, watched: Boolean) -> Unit,
    backgroundScope: CoroutineScope,
) {
    val subjectId: Int by subjectId

    val theme: EpisodeListProgressTheme by theme

    private val episodeProgressInfoList by episodeProgressInfoList
    val episodes: List<EpisodeProgressItem> by derivedStateOf {
        this.episodeProgressInfoList.map {
            EpisodeProgressItem(
                episodeId = it.episode.episodeId,
                episodeSort = it.episode.renderEpisodeEp(),
                collectionType = it.collectionType,
                isOnAir = it.episode.isKnownOnAir,
                cacheStatus = it.cacheStatus,
            )
        }
    }

    val hasAnyUnwatched: Boolean by derivedStateOf {
        this.episodes.any { !it.collectionType.isDoneOrDropped() }
    }

    private val toggleEpisodeWatchedTasker = MonoTasker(backgroundScope)
    fun toggleEpisodeWatched(item: EpisodeProgressItem) {
        if (item.isLoading) return
        item.isLoading = true
        toggleEpisodeWatchedTasker.launch {
            try {
                onSetEpisodeWatched(subjectId, item.episodeId, item.collectionType != UnifiedCollectionType.DONE)
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    item.isLoading = false
                }
            }
        }
    }
}

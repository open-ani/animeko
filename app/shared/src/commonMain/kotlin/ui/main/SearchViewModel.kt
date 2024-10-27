/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.repository.BangumiRelatedCharactersRepository
import me.him188.ani.app.data.repository.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.SubjectSearchRepository
import me.him188.ani.app.data.repository.TrendsRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectSearcherImpl
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.userInfo
import me.him188.ani.app.tools.ldc.LazyDataCache
import me.him188.ani.app.ui.exploration.search.SearchPageState
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.search.LdcSearchState
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.utils.coroutines.onReplacement
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SearchViewModel : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()
    private val bangumiRelatedCharactersRepository: BangumiRelatedCharactersRepository by inject()

    private val authState = AuthState()

    @OptIn(OpaqueSession::class)
    private val selfInfoState = sessionManager.userInfo.produceState(null)

    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()

    private val searcher = SubjectSearcherImpl(subjectSearchRepository, backgroundScope.coroutineContext)
    private val queryState = mutableStateOf("")

    val searchPageState: SearchPageState = SearchPageState(
        searchHistoryState = searchHistoryRepository.getHistoryFlow().produceState(emptyList()),
        suggestionsState = searchHistoryRepository.getHistoryFlow()
            .produceState(emptyList()),// todo: suggestions
        onRequestPlay = { info ->
            episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId).first().firstOrNull()?.let {
                SearchPageState.EpisodeTarget(info.subjectId, it.episodeInfo.episodeId)
            }
        },
        queryState = queryState,
        searchState = LdcSearchState(
            createLdc = {
                LazyDataCache(
                    createSource = {
                        ApiResponse.success(
                            subjectSearchRepository.startSearch(SubjectSearchQuery(keyword = queryState.value))
                                .map { SubjectPreviewItemInfo.compute(it, null, null) },
                        )
                    },
                    getKey = { it.subjectId },
                    debugName = "ExplorationTabViewModel.searchPageState.ldc",
                )
            },
            backgroundScope.coroutineContext,
        ),
        backgroundScope,
    )

    val subjectDetailsViewModelFlow = snapshotFlow { searchPageState.selectedItem }
        .flowOn(Dispatchers.Main)
        .map {
            SubjectDetailsViewModel(it?.subjectId ?: return@map null)
        }
        .onReplacement {
            it?.cancelScope()
        }
        .flowOn(Dispatchers.Default)
        .shareInBackground()

    override fun onCleared() {
        super.onCleared()
        subjectDetailsViewModelFlow.replayCache.firstOrNull()?.cancelScope()
    }
}

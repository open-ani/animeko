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
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.NSFWMode
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.exploration.search.SearchPageState
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SearchViewModel : AbstractViewModel(), KoinComponent {
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()

    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val nsfwSettingFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

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
        searchState = PagingSearchState(
            createPager = {
                combine(
                    nsfwSettingFlow,
                    subjectSearchRepository.searchSubjects(
                        SubjectSearchQuery(keyword = queryState.value),
                        useNewApi = {
                            settingsRepository.uiSettings.flow.map { it.searchSettings.enableNewSearchSubjectApi }
                                .first()
                        },
                    ).map { data ->
                        data.map {
                            SubjectPreviewItemInfo.compute(
                                it.subjectInfo,
                                it.mainEpisodeCount,
                                it.lightSubjectRelations.lightRelatedPersonInfoList,
                                it.lightSubjectRelations.lightRelatedCharacterInfoList,
                            )
                        }
                    }.cachedIn(backgroundScope),
                ) { nsfwMode, subjects ->
                    subjects.filter { if (nsfwMode == NSFWMode.HIDE) !it.nsfw else true }
                }.flowOn(Dispatchers.Default)
            },
        ),
        nsfwModeState = nsfwSettingFlow.produceState(NSFWMode.DISPLAY),
        backgroundScope = backgroundScope,
        onStartSearch = { query ->
            subjectDetailsStateLoader.clear()
            launchInBackground {
                searchHistoryRepository.addHistory(query)
            }
        },
    )

    val subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
    private var currentPreviewingSubject: SubjectInfo? = null

    fun viewSubjectDetails(previewItem: SubjectPreviewItemInfo) {
        subjectDetailsStateLoader.clear()
        subjectDetailsStateLoader.load(
            previewItem.subjectId,
            placeholder = SubjectInfo.createPlaceholder(
                previewItem.subjectId,
                previewItem.title,
                previewItem.imageUrl,
                previewItem.title,
            ).also { currentPreviewingSubject = it },
        )
    }

    fun reloadCurrentSubjectDetails() {
        val curr = currentPreviewingSubject ?: return
        subjectDetailsStateLoader.reload(curr.subjectId, curr)
    }

    override fun onCleared() {
        super.onCleared()
    }
}


/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.network.SubjectSearchField
import me.him188.ani.app.data.network.SubjectSearchFilters
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.error

/**
 * 搜索补全 (推荐)
 */
class SubjectSearchCompletionRepository(
    private val aniSubjectSearchService: AniSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    settingsRepository: SettingsRepository,
    private val subjectService: SubjectService,
    private val bangumiEpisodeService: BangumiEpisodeService,
) : Repository() {
    private val ignoreDoneAndDroppedFlow =
        settingsRepository.uiSettings.flow.map { it.searchSettings.ignoreDoneAndDroppedSubjects }
    private val nsfwSettings = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    fun completionsFlow(query: String): Flow<PagingData<String>> = flow {
        val nsfwMode = nsfwSettings.first()
        val excludedIds = if (ignoreDoneAndDroppedFlow.first()) {
            subjectCollectionRepository.getSubjectIdsByCollectionType(
                types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
            ).first().toSet()
        } else {
            emptySet()
        }
        emitAll(
            subjectSearchCompletionsFlow(
                query = query,
                nsfwMode = nsfwMode,
                excludedIds = excludedIds,
                getSubject = subjectService::getSubjectCollection,
                getEpisodeSubjectId = bangumiEpisodeService::getSubjectId,
            ) { keyword ->
                aniSubjectSearchService.searchSubjects(
                    keyword = keyword,
                    limit = defaultPagingConfig.initialLoadSize,
                    filters = SubjectSearchFilters(
                        nsfw = when (nsfwMode) {
                            NsfwMode.DISPLAY -> null
                            NsfwMode.BLUR, NsfwMode.HIDE -> false
                        },
                    ),
                    fields = listOf(SubjectSearchField.NAME),
                )
                    .filter { it.subjectInfo.subjectId !in excludedIds }
                    .map { it.subjectInfo.nameCn.ifEmpty { it.subjectInfo.name } }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.map { PagingData.from(it, NotLoading) },
        )
    }.catch { cause ->
        val error = RepositoryException.wrapOrThrowCancellation(cause)
        logger.error(error) { "Failed to get search completions, query: $query" }
        emit(PagingData.from(emptyList(), NotLoading.copy(refresh = LoadState.Error(error))))
    }.flowOn(defaultDispatcher)

    private companion object {
        val NotLoading = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        )
    }
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.network.BangumiSubjectSearchService
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 搜索补全 (推荐)
 */
class BangumiSubjectSearchCompletionRepository(
    private val bangumiSubjectSearchService: BangumiSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    settingsRepository: SettingsRepository,
) : Repository() {
    private val useNewApiFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.enableNewSearchSubjectApi }
    private val ignoreDoneAndDroppedFlow =
        settingsRepository.uiSettings.flow.map { it.searchSettings.ignoreDoneAndDroppedSubjects }
    private val nsfwSettings = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    fun completionsFlow(query: String): Flow<PagingData<String>> = Pager(
        config = defaultPagingConfig,
        pagingSourceFactory = {
            object : PagingSource<Int, String>() {
                override fun getRefreshKey(state: PagingState<Int, String>): Int? = null

                override suspend fun load(
                    params: LoadParams<Int>
                ): LoadResult<Int, String> = runWrappingExceptionAsLoadResult {
                    // 只加载第一页
                    val completions = bangumiSubjectSearchService.searchSubjectNames(
                        keyword = query,
                        useNewApi = useNewApiFlow.first(),
                        includeNsfw = nsfwSettings.first() == NsfwMode.DISPLAY,
                        limit = params.loadSize,
                    )

                    val filteredCompletions = if (ignoreDoneAndDroppedFlow.first()) {
                        val excludedNames = subjectCollectionRepository.getSubjectNamesCnByCollectionType(
                            types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                        ).first()

                        completions.filter { it !in excludedNames }
                    } else {
                        completions
                    }

                    LoadResult.Page(
                        data = filteredCompletions.distinct(),
                        prevKey = null,
                        nextKey = null,
                    )
                }
            }
        },
    ).flow.flowOn(defaultDispatcher)
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * TV 探索页薄 VM (atv-architecture.md §7.1): 热门趋势 (hero + 卡片行) + 推荐分页行.
 */
@Stable
class TvExplorationViewModel : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val recommendationRepository: RecommendationRepository by inject()

    private val _trends = MutableStateFlow(emptyList<TrendingSubjectInfo>())
    val trends: StateFlow<List<TrendingSubjectInfo>> = _trends.asStateFlow()

    val recommendations: Flow<PagingData<RecommendedItemInfo>> =
        recommendationRepository.recommendedSubjectsPager().cachedIn(backgroundScope)

    init {
        backgroundScope.launch {
            // 错误静默重试 (PR 结论, §7.1)
            while (_trends.value.isEmpty() && isActive) {
                runCatching { trendsRepository.getTrendsInfo() }
                    .onSuccess { _trends.value = it.subjects }
                    .onFailure {
                        logger.warn(it) { "Failed to load trends, retrying in 5s" }
                        delay(5.seconds)
                    }
            }
        }
    }

    private companion object {
        private val logger = logger<TvExplorationViewModel>()
    }
}

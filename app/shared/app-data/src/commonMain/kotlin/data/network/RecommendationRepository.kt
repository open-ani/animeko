/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo

class RecommendationRepository(
    private val trendsRepository: TrendsRepository,
) {
    fun recommendedSubjectsPager(): Flow<PagingData<RecommendedItemInfo>> {
        return trendsRepository.bangumiTrendingSubjectsPager().map { pagingData ->
            pagingData.map { info ->
                info.toRecommendedSubjectInfo()
            }
        }
    }

    private fun TrendingSubjectInfo.toRecommendedSubjectInfo(): RecommendedSubjectInfo = RecommendedSubjectInfo(
        bangumiId = bangumiId,
        nameCn = nameCn,
        imageLarge = imageLarge,
    )
}

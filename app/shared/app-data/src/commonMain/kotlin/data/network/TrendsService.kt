/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniTrends

class TrendsRepository(
    apiLazy: Lazy<TrendsAniApi>,
) : Repository {
    private val api by apiLazy

    suspend fun getTrending(): ApiResponse<TrendsInfo> = me.him188.ani.app.data.models.runApiRequest {
        api.getTrends().body().toTrendingInfo()
    }
}

fun AniTrends.toTrendingInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = trendingSubjects.map {
            TrendingSubjectInfo(it.bangumiId, it.nameCn, it.imageLarge)
        },
    )
}
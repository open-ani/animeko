/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi.client

import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.bangumi.BangumiRateLimitedException
import me.him188.ani.datasources.bangumi.BangumiSearchSubjectNewApi
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.search.BangumiSort
import me.him188.ani.datasources.bangumi.models.subjects.BangumiLegacySubject
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize

interface BangumiSearchApi {
    /**
     * 搜索条目.
     *
     * @param keyword 关键字, 例如番剧名称
     * @param types 条目类型, 默认为 [BangumiSubjectType.ANIME]
     * @param responseGroup 返回数据大小, 默认为 [BangumiResponseGroup.SMALL]
     * @param offset 开始条数, 默认为 0
     * @param limit 返回条数, 最大为 20
     * @return 搜索结果, null 表示已经到达最后一条
     */
    suspend fun searchSubjectByKeywords(
        keyword: String,
        offset: Int? = null,
        limit: Int? = null,
        sort: BangumiSort? = null,
        types: List<BangumiSubjectType>? = null,
        tags: List<String>? = null, // "童年", "原创"
        airDates: List<String>? = null, // YYYY-MM-DD
        ratings: List<String>? = null, // ">=6", "<8"
        ranks: List<String>? = null,
        nsfw: Boolean? = null,
    ): List<BangumiSearchSubjectNewApi>?

    /**
     * Search bangumi subjects by old api.
     *
     * @see <https://github.com/bangumi/server>
     * @see <https://bangumi.github.io/api/>
     *
     * @param keyword subject keyword, such as name.
     * @param type bangumi subject type, default search all types when it is null.
     * @param responseGroup return the data size which default is small, available values : [small, medium, large], default value is small.
     * @param start start page, default is first page.
     * @param maxResults max results in page, max is 25.
     * @see BangumiSubjectType
     * @see BangumiSubjectImageSize
     *
     * @throws BangumiRateLimitedException
     */
    suspend fun searchSubjectsByKeywordsWithOldApi(
        keyword: String,
        type: BangumiSubjectType,
        responseGroup: BangumiSubjectImageSize?,
        start: Int?,
        maxResults: Int?
    ): Paged<BangumiLegacySubject>

    fun getSubjectImageUrl(
        id: Int,
        size: BangumiSubjectImageSize,
    ): String
}
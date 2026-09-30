/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.utils.ktor.ApiInvoker

/** 使用 Bangumi 公开剧集接口查询剧集所属的条目。 */
class BangumiEpisodeService(private val api: ApiInvoker<DefaultApi>) {
    /** 剧集不存在、不可公开访问或所属条目 ID 无效时返回 null。 */
    suspend fun getSubjectId(episodeId: Int): Int? = try {
        api { getEpisodeById(episodeId).body().subjectId }.takeIf { it > 0 }
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.utils.ktor.ApiInvoker

/** 按 Bangumi 剧集 ID 查询所属条目, 无需预先知道条目 ID. */
class EpisodeSubjectRepository(private val api: ApiInvoker<EpisodesAniApi>) {
    /** 剧集不存在时返回 null. */
    suspend fun getSubjectId(episodeId: Int): Int? = withContext(Dispatchers.Default) {
        try {
            api { getEpisode(episodeId.toLong()).body().subjectId }
                .takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null
            else throw RepositoryException.wrapOrThrowCancellation(e)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

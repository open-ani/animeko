/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.SubjectRelationsAniApi
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext


// For 查询第一季时自动排除第二季的资源 #1324
// Server-side: https://github.com/open-ani/ani-api-server/commit/5b513e607eca222c6352d3e4243df43de83469ba
class AniSubjectRelationIndexService(
    apiLazy: Lazy<SubjectRelationsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private val api by apiLazy

    suspend fun getSubjectRelationIndex(subjectId: Int) = withContext(ioDispatcher) {
        try {
            // https://auth.myani.org/v1/subject-relations/239816
            api.getSubjectRelations(subjectId.toString()).body()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.mikan.MikanIndexCacheProvider

interface MikanIndexCacheRepository : MikanIndexCacheProvider {
    override suspend fun getMikanSubjectId(bangumiSubjectId: String): String?
    override suspend fun setMikanSubjectId(bangumiSubjectId: String, mikanSubjectId: String)
}

@Serializable
data class MikanIndexes(
    val data: Map<String, String> = emptyMap()
) {
    companion object {
        val Empty = MikanIndexes()
    }
}

class MikanIndexCacheRepositoryImpl(
    private val store: DataStore<MikanIndexes>,
) : MikanIndexCacheRepository {
    override suspend fun getMikanSubjectId(bangumiSubjectId: String): String? {
        return store.data.map {
            it.data[bangumiSubjectId]
        }.firstOrNull()
    }

    override suspend fun setMikanSubjectId(bangumiSubjectId: String, mikanSubjectId: String) {
        store.updateData {
            it.copy(data = it.data + (bangumiSubjectId to mikanSubjectId))
        }
    }
}
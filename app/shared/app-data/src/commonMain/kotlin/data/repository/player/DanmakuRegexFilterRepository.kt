/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.repository.Repository

interface DanmakuRegexFilterRepository : Repository {

    val flow: Flow<List<DanmakuRegexFilter>>

    suspend fun update(id: String, new: DanmakuRegexFilter)
    suspend fun remove(filter: DanmakuRegexFilter)
    suspend fun add(new: DanmakuRegexFilter)

}

class DanmakuRegexFilterRepositoryImpl(
    private val store: DataStore<List<DanmakuRegexFilter>>,
) : DanmakuRegexFilterRepository {

    override val flow: Flow<List<DanmakuRegexFilter>> = store.data

    override suspend fun update(id: String, new: DanmakuRegexFilter) {
        store.updateData { data ->
            data.map {
                if (it.id == id) new else it
            }
        }
    }

    override suspend fun remove(filter: DanmakuRegexFilter) {
        store.updateData { data ->
            data - filter
        }
    }

    override suspend fun add(new: DanmakuRegexFilter) {
        store.updateData { data ->
            data + new
        }
    }

    companion object {
        fun create(store: DataStore<List<DanmakuRegexFilter>>): DanmakuRegexFilterRepository {
            return DanmakuRegexFilterRepositoryImpl(store)
        }
    }

}
/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun createMemoryPreferredWebMediaSourceDao(): PreferredWebMediaSourceDao {
    return object : PreferredWebMediaSourceDao {
        private val store = MutableStateFlow(emptyMap<Int, String>())

        override suspend fun setPreferredMediaSource(preferredWebMediaSource: PreferredWebMediaSource) {
            store.value = store.value + (preferredWebMediaSource.subjectId to preferredWebMediaSource.mediaSourceId)
        }

        override fun getPreferredMediaSourceId(subjectId: Int): Flow<String?> {
            return store.map { it[subjectId] }
        }

        override suspend fun deletePreferredMediaSource(subjectId: Int) {
            store.value = store.value - subjectId
        }
    }
}

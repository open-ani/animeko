/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface WindowStateRepository : Repository {
    val flow: Flow<SavedWindowState?>
    suspend fun update(states: SavedWindowState)
}

@Serializable
class SavedWindowState(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

class WindowStateRepositoryImpl(
    private val store: DataStore<SavedWindowState?>,
) : WindowStateRepository {
    override val flow: Flow<SavedWindowState?> = store.data

    override suspend fun update(states: SavedWindowState) {
        store.updateData {
            states
        }
    }

}

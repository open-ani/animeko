/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.bangumi

import me.him188.ani.client.models.AniBangumiFullSyncState
import me.him188.ani.client.models.AniBangumiSyncError
import me.him188.ani.client.models.AniBangumiSyncStateEntity

sealed interface BangumiSyncState {
    val finished: Boolean get() = false

    data object Preparing : BangumiSyncState

    data class FetchingSubjects(val fetchedCount: Int) : BangumiSyncState
    data class FetchingEpisodes(val fetchedCount: Int) : BangumiSyncState
    data class Inserting(val savedCount: Int) : BangumiSyncState

    data class Finishing(val savedCount: Int) : BangumiSyncState

    data class Finished(val savedCount: Int, val error: AniBangumiSyncError?) : BangumiSyncState {
        override val finished: Boolean
            get() = true
    }

    data object Unsupported : BangumiSyncState

    companion object {
        fun fromEntity(entity: AniBangumiSyncStateEntity): BangumiSyncState? {
            return when (entity.state) {
                null -> Unsupported
                AniBangumiFullSyncState.PREPARING -> Preparing
                AniBangumiFullSyncState.FETCHING_SUBJECTS -> FetchingSubjects(entity.value ?: 0)
                AniBangumiFullSyncState.FETCHING_EPISODES -> FetchingEpisodes(entity.value ?: 0)
                AniBangumiFullSyncState.INSERTING_DATABASE -> Inserting(entity.value ?: 0)
                AniBangumiFullSyncState.FINISHING -> Finishing(entity.value ?: 0)
                AniBangumiFullSyncState.FINISHED -> Finished(entity.value ?: 0, entity.error)
            }
        }
    }
}
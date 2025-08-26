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
import me.him188.ani.client.models.AniBangumiSyncStateEntity

sealed interface BangumiSyncState {
    val finished: Boolean get() = false

    data object Started : BangumiSyncState

    data class Fetched(val fetchedCount: Int) : BangumiSyncState

    data class Saved(val savedCount: Int) : BangumiSyncState

    data object SyncingTimeline : BangumiSyncState

    data object Finished : BangumiSyncState {
        override val finished: Boolean
            get() = true
    }

    companion object {
        fun fromEntity(entity: AniBangumiSyncStateEntity): BangumiSyncState? {
            return when (entity.state) {
                AniBangumiFullSyncState.STARTED -> Started
                AniBangumiFullSyncState.FETCHED -> Fetched(entity.value)
                AniBangumiFullSyncState.SAVED -> Saved(entity.value)
                AniBangumiFullSyncState.SYNCING_TIMELINE -> SyncingTimeline
                AniBangumiFullSyncState.FINISHED -> Finished
                AniBangumiFullSyncState.UNKNOWN -> null
            }
        }
    }
}
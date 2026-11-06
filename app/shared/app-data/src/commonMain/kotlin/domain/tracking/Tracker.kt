/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.tracking

import kotlinx.coroutines.CancellationException
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/** A collection change recorded by Animeko, identified using Animeko subject and episode IDs. */
sealed interface TrackingChange {
    val subjectId: Int

    data class Episodes(
        override val subjectId: Int,
        val episodeIds: List<Int>,
        val collectionType: UnifiedCollectionType,
    ) : TrackingChange

    data class Subject(
        override val subjectId: Int,
        val collectionType: UnifiedCollectionType,
    ) : TrackingChange
}

/** An enabled destination for collection changes. Provider-specific mapping and authentication stay inside it. */
interface Tracker {
    /** Stable ID unique among destinations registered with a dispatcher. */
    val id: String

    /** Pushes a change or throws; cancellation must propagate to the caller. */
    suspend fun push(change: TrackingChange)
}

/** Dispatches independently to each destination. This dispatcher does not persist or retry changes. */
class TrackingDispatcher(
    private val trackers: List<Tracker>,
    private val onFailure: (trackerId: String, failure: Exception) -> Unit,
) {
    init {
        require(trackers.all { it.id.isNotBlank() }) { "Tracker IDs must not be blank" }
        require(trackers.map { it.id }.distinct().size == trackers.size) { "Tracker IDs must be unique" }
    }

    suspend fun dispatch(change: TrackingChange) {
        for (tracker in trackers) {
            try {
                tracker.push(change)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(tracker.id, e)
            }
        }
    }
}

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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.utils.logging.info

@Serializable
data class EpisodeHistories(
    val histories: List<EpisodeHistory> = emptyList(),
) {
    companion object {
        val Empty = EpisodeHistories(emptyList())
    }
}

interface EpisodePlayHistoryRepository {
    val flow: Flow<List<EpisodeHistory>>

    suspend fun clear()

    suspend fun remove(episodeId: Int)

    suspend fun saveOrUpdate(episodeId: Int, positionMillis: Long)

    suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long?
}

class EpisodePlayHistoryRepositoryImpl(
    private val dataStore: DataStore<EpisodeHistories>
) : Repository(), EpisodePlayHistoryRepository {
    override val flow: Flow<List<EpisodeHistory>> = dataStore.data.map { it.histories }

    override suspend fun clear() {
        dataStore.updateData { EpisodeHistories.Empty }
    }

    override suspend fun remove(episodeId: Int) {
        dataStore.updateData { current ->
            logger.info { "remove play progress for episode $episodeId" }
            current.copy(histories = current.histories.filter { it.episodeId != episodeId })
        }
    }

    override suspend fun saveOrUpdate(episodeId: Int, positionMillis: Long) {
        val episodeHistory = EpisodeHistory(
            episodeId = episodeId,
            positionMillis = positionMillis,
        )
        logger.info { "save or update play progress $episodeHistory" }
        dataStore.updateData { current ->
            val history = current.histories.find { it.episodeId == episodeId }
            return@updateData if (history == null) {
                current.copy(histories = current.histories + episodeHistory)
            } else {
                current.copy(
                    histories = current.histories.map { save ->
                        if (save.episodeId == episodeHistory.episodeId) {
                            episodeHistory
                        } else {
                            save
                        }
                    },
                )
            }
        }
    }

    override suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long? {
        return dataStore.data.map { current ->
            current.histories.find { it.episodeId == episodeId }?.positionMillis
        }.firstOrNull()?.also {
            logger.info { "load play progress for episode $episodeId: positionMillis=$it" }
        }
    }
}

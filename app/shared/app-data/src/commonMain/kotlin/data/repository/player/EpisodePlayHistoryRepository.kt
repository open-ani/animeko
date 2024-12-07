/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import me.him188.ani.app.data.persistent.database.dao.EpisodePlayHistoryDao
import me.him188.ani.app.data.persistent.database.dao.EpisodePlayHistoryEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.utils.logging.info


class EpisodePlayHistoryRepository(
    private val playHistoryDao: EpisodePlayHistoryDao
) : Repository() {

    suspend fun remove(episodeId: Int) {
        logger.info { "remove play progress for episode $episodeId" }
        playHistoryDao.deleteByEpisodeId(episodeId)
    }

    suspend fun saveOrUpdate(episodeId: Int, positionMillis: Long) {
        val playHistoryEntity = EpisodePlayHistoryEntity(
            episodeId = episodeId,
            positionMillis = positionMillis,
        )
        playHistoryDao.upsert(playHistoryEntity)
    }

    suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long? {
        val positionMillis = playHistoryDao.findByEpisodeId(episodeId)?.positionMillis
        logger.info { "load play progress for episode $episodeId: positionMillis=$positionMillis" }
        return positionMillis
    }
}

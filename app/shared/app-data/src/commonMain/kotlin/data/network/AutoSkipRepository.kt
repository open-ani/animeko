/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.client.apis.AutoSkipAniApi
import me.him188.ani.client.models.AutoSkipReportRequest
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * Client-side helper for AutoSkip reporting and querying rules.
 */
class AutoSkipRepository(
    private val api: ApiInvoker<AutoSkipAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : Repository() {

    private val reportStates = mutableMapOf<Int, EpisodeReportState>()
    private val lock = Mutex()

    /**
     * Report a manual skip action.
     * Client-side throttling: at most 2 reports per episode, and at most once per 10 minutes per episode.
     */
    suspend fun reportSkip(episodeId: Int, mediaSourceId: String, timeSeconds: Int) = lock.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val state = reportStates.getOrPut(episodeId) { EpisodeReportState(0, 0L) }
        if (state.count >= 2) return
        if (now - state.lastReportAt < TEN_MINUTES_MS) return

        withContext(ioDispatcher) {
            api {
                reportSkip(
                    episodeId.toLong(),
                    AutoSkipReportRequest(mediaSourceId = mediaSourceId, time = timeSeconds),
                ).body()
            }
        }
        state.count += 1
        state.lastReportAt = now
    }

    /**
     * Fetch autoskip rules for an episode. Emits once.
     */
    fun rulesFlow(episodeId: Int): Flow<List<Int>> = flow {
        val rules = withContext(ioDispatcher) {
            api { getAutoSkipRules(episodeId.toLong()).body().rules.map { it.time } }
        }
        emit(rules)
    }

    private data class EpisodeReportState(
        var count: Int,
        var lastReportAt: Long,
    )

    private companion object {
        private const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}


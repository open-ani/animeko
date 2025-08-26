/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Note that you must provide a scoped [CoroutineContext], which should be cancelled if not needed.
 */
class BangumiFullSyncStateResolver(
    coroutineContext: CoroutineContext,
    private val subjectService: SubjectService,
) {
    private val logger = logger<BangumiFullSyncStateResolver>()
    private val scope = coroutineContext.childScope()

    private val checkerState = MutableStateFlow(false)

    private val fullSyncState: MutableStateFlow<BangumiSyncState?> = MutableStateFlow(null)
    val state: StateFlow<BangumiSyncState?> get() = fullSyncState

    init {
        scope.launch {
            loopCheck()
        }
    }

    /**
     * 开始轮询检查 bangumi 同步在状态
     */
    fun setChecking(enabled: Boolean) {
        checkerState.value = enabled
        if (enabled) {
            fullSyncState.value = null
        }
    }

    private suspend fun loopCheck() {
        checkerState.collectLatest {
            if (!it) return@collectLatest
            fullSyncState.value = null // clear old state

            delay(1.seconds)
            getState()

            while (true) {
                delay(3.seconds)
                getState()
            }
        }
    }

    suspend fun getState() {
        fullSyncState.value = try {
            subjectService.getBangumiFullSyncState()
                .also {
                    logger.info { "Full sync state: $it" }
                }
        } catch (ex: RepositoryException) {
            logger.error(ex) { "Failed to get bangumi full sync state." }
            null
        }
    }
}
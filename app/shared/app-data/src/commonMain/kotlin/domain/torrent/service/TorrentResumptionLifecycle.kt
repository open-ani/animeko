/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.UnsafeTorrentEngineAccessApi
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

/**
 * A combined lifecycle which determines whether the [LifecycleAwareTorrentServiceConnection] should keep the torrent service alive.
 *
 * The lifecycle will be kept to [RESUMED][Lifecycle.State.RESUMED] if:
 * * [processLifecycle] is [RESUMED][Lifecycle.State.RESUMED]. On Android, it means the app is currently at foreground.
 * * If a torrent media cache is not completed.
 *
 * This is used on Android.
 */
class TorrentResumptionLifecycle(
    dataStoreFlow: StateFlow<DataStore<List<MediaCacheSave>>>,
    private val processLifecycle: Lifecycle,
    scope: CoroutineScope,
) : LifecycleOwner, TorrentEngineAccess {
    private val logger = logger<TorrentResumptionLifecycle>()
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry
    private val keepResumed = MutableStateFlow(false)

    override val useEngine: StateFlow<Boolean> =
        combine(
            dataStoreFlow.flatMapLatest { it.data.map(::checkIfAllTorrentMediaCacheCompleted) },
            keepResumed,
            processLifecycle.currentStateFlow,
        ) { allCompleted, keep, currentState ->
            withContext(Dispatchers.Main) {
                if (currentState != Lifecycle.State.RESUMED) {
                    registry.currentState = currentState
                    return@withContext false
                }
                val shouldMoveToResumed = keep || !allCompleted
                // We should not move state to RESUMED if all torrent media cache were completed.
                registry.currentState = if (shouldMoveToResumed) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
                // If not all torrent media cache were completed or 
                // user request to keep torrent engine alive, we should use torrent engine.
                shouldMoveToResumed
            }
        }
            .distinctUntilChanged()
            .onEach { logger.info { "Current use engine: $it" } }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        useEngine.value
    }

    @UnsafeTorrentEngineAccessApi
    override fun requestUseEngine(use: Boolean): Boolean {
        keepResumed.value = use
        return true
    }

    /**
     * Check if all torrent media cache is completed. If not, the service will be kept alive.
     */
    private fun checkIfAllTorrentMediaCacheCompleted(list: List<MediaCacheSave>): Boolean {
        list.forEach { save ->
            if (save.engine != MediaCacheEngineKey(TorrentEngineType.Anitorrent.id)) {
                return@forEach
            }

            if (save.metadata.extra[TorrentMediaCacheEngine.EXTRA_TORRENT_COMPLETED] != "true") {
                return false
            }
        }

        return true
    }
}
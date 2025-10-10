/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("LeakingThis")

package me.him188.ani.app.ui.cache.subject

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata


/**
 * 一个条目的一个剧集的缓存状态
 */
@Stable
class EpisodeCacheState(
    val episodeId: Int,
    val cacheRequester: EpisodeCacheRequester,
    currentStageState: State<CacheRequestStage>,
    infoState: State<EpisodeCacheInfo>,
    cacheStatusState: State<EpisodeCacheStatus?>,
    backgroundScope: CoroutineScope,
) {
    /**
     * [episodeId] 对应的剧集信息, 初始值为 [EpisodeCacheInfo.Placeholder], 表示正在加载.
     * @see isInfoLoading
     */
    val info: EpisodeCacheInfo by infoState

    /**
     * [info] 是否仍然在加载中
     */
    val isInfoLoading by derivedStateOf {
        this.info === EpisodeCacheInfo.Placeholder
    }

    val currentStage by currentStageState

    /**
     * 该剧集当前的缓存状态. `null` 表示还在加载中
     */
    val cacheStatus: EpisodeCacheStatus? by cacheStatusState

    /**
     * 是否允许缓存该剧集
     */
    val canCache: Boolean get() = true

    /**
     * 是否正在进行添加缓存或删除缓存等有进度的操作
     */
    val actionTasker = MonoTasker(backgroundScope)

    val showProgressIndicator by derivedStateOf {
        if (currentStageState.value is CacheRequestStage.Working) {
            MutableStateFlow(true)
        } else {
            actionTasker.isRunning
        }
    }
}

sealed class AbstractTask {
    abstract val attemptedTrySelect: StateFlow<Boolean>
}

/**
 * @see EpisodeCacheState.currentStage
 */
@Stable
class SelectMediaTask(
    val episode: EpisodeCacheState,
    val fetchSession: MediaFetchSession,
    val mediaSelector: MediaSelector,
    override val attemptedTrySelect: StateFlow<Boolean>
) : AbstractTask()

/**
 * @see EpisodeCacheState.currentSelectStorageTask
 */
@Stable
class SelectStorageTask(
    val episode: EpisodeCacheState,
    val options: List<MediaCacheStorage>,
    override val attemptedTrySelect: StateFlow<Boolean>
) : AbstractTask()

/**
 * 从用户侧收集到的, 期望为一个剧集创建的缓存的目标 [Media], [MediaCacheStorage], 以及其他相关信息.
 *
 * @see EpisodeCacheListStateImpl.onRequestCacheComplete
 */
data class EpisodeCacheTargetInfo(
    val episode: EpisodeCacheState,
    val request: EpisodeCacheRequest,
    val media: Media,
    val storage: MediaCacheStorage,
    /**
     * 用于提供给 [MediaCacheEngine.createCache] 和 [CachedMedia] 的缓存元数据. 数据主要来自 [request] 的条目和剧集信息.
     */
    val metadata: MediaCacheMetadata,
)


/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.persistent.PlatformDataStoreManager
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.torrent.TorrentEngine

/**
 * [TorrentMediaCacheEngine] 是否使用 [TorrentEngine] 来执行[恢复缓存][MediaCacheEngine.restore] 操作.
 *
 * 在 Android 平台, APP 启动尝试[恢复缓存][MediaCacheStorage.restorePersistedCaches]时,
 * 如果所有的 torrent 缓存任务都[已经完成][TorrentMediaCacheEngine.EXTRA_TORRENT_COMPLETED],
 * [TorrentMediaCacheEngine] 将会使用 [datastore][PlatformDataStoreManager.mediaCacheMetadataStore] 内存储的本地信息来恢复操作.
 * 不会访问 [TorrentEngine]. 这样可以省去启动 AniTorrentService 的过程, 节省设备电量.
 *
 * 此接口的其中一个实现 [TorrentServiceConnectionManager][me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager]
 * 会在其 [Lifecycle] 转变为 [Lifecycle.State.RESUMED] 时
 * 使 [useEngine] 返回 `true`, 并且保持 [TorrentEngine] 处于可用状态.
 *
 * @see me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager
 */
interface TorrentEngineAccess {
    val useEngine: StateFlow<Boolean>

    /**
     * 请求使用 [TorrentEngine] 来执行[恢复缓存][MediaCacheEngine.restore] 操作.
     * 在请求使用 [TorrentEngine] 之后, [useEngine] 无论如何都返回 `true`.
     *
     * 此接口的其中一个实现 [TorrentServiceConnectionManager][me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager]
     * 会在请求时使用 [TorrentEngine] 之后, 启动 torrent service 服务.
     */
    @UnsafeTorrentEngineAccessApi
    fun requestUseEngine(token: Any, use: Boolean): Boolean
}

/**
 * 保证 [block] 调用中 [TorrentEngine] 可用.
 *
 * 注意: 若在 [block] 中调用了需要 [TorrentEngine] 长期可用的方法, 例如[创建缓存][MediaCacheEngine.createCache],
 * 需要保证在 [block] 返回前使 [useEngine][TorrentEngineAccess.useEngine] 已经置为 `true`.
 * 否则, 若在 [block] 返回后, [TorrentEngine] 可能立刻变得不可用, 进而导致无法继续访问 [TorrentEngine]
 * (因为 block 返回后立刻使用 [requestUseEngine][TorrentEngineAccess.requestUseEngine] 释放了 [TorrentEngine] 存活需求,
 * 而 [useEngine][TorrentEngineAccess.useEngine] 又为 `false`).
 *
 * 此接口的其中一个实现 [TorrentServiceConnectionManager][me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager]
 * 会在其 [Lifecycle] 转变为 [Lifecycle.State.RESUMED] 时使
 * [useEngine][TorrentEngineAccess.useEngine] 返回 `true`, 并且保持 [TorrentEngine] 处于可用状态.
 * 也就是说需要在 [block] 返回前必须使其 [Lifecycle] 转变为 [Lifecycle.State.RESUMED]
 * (a.k.a. [TorrentEngine] 创建好了新的缓存并且写到了 metadata datastore 里).
 */
@EnsureTorrentEngineIsAccessible
@OptIn(UnsafeTorrentEngineAccessApi::class)
inline fun <T> TorrentEngineAccess.withEngineAccessible(token: Any, block: () -> T): T {
    try {
        requestUseEngine(token, true)
        return block()
    } finally {
        requestUseEngine(token, false)
    }
}

/**
 * 总是在 [TorrentMediaCacheEngine] 使用 [TorrentEngine].
 */
object AlwaysUseTorrentEngineAccess : TorrentEngineAccess {
    override val useEngine: StateFlow<Boolean> = MutableStateFlow(true)

    @UnsafeTorrentEngineAccessApi
    override fun requestUseEngine(token: Any, use: Boolean): Boolean {
        return true
    }
}

@RequiresOptIn(
    message = "若在 block 中调用了需要 TorrentEngine 长期可用的方法, " +
            "请确保在此函数返回后 TorrentEngine 一定可用 " +
            "(例如在其中一个实现 TorrentServiceConnectionManager 中, " +
            "使 checkIfAllTorrentMediaCacheCompleted 为 false, 也就是创建了 BT 媒体缓存), " +
            "否则会导致错误的 useEngine 状态被 emit. " +
            "调用此函数需要有明确的注释来说明必须调用此函数的原因和合理性, 方便后人理解这里的逻辑",
    level = RequiresOptIn.Level.ERROR,
)
annotation class EnsureTorrentEngineIsAccessible


@RequiresOptIn(
    message = "请总是使用 TorrentEngineAccess.withEngineAccessible 来使 TorrentEngine 可用, " +
            "除非你能确保请求 TorrentEngine 可用后有对应的 `requestUseEngine(false)` 来释放 TorrentEngine 可用状态. " +
            "调用此函数需要有明确的注释来说明必须调用此函数的原因和合理性, 方便后人理解这里的逻辑",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeTorrentEngineAccessApi



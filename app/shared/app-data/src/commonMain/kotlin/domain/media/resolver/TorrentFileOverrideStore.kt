/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media

/**
 * 用户在「资源中未找到本集」时手动指定的种子内文件, 按 media 与集数记住.
 *
 * 没有做成 [MediaResolver.resolve] 的参数: 这个选择还要被
 * [me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine] 用到, 而那条路径不经过
 * `resolve` —— 缓存记录由 `CacheOnBtPlayExtension` 在播放成功之后才建立, 建立时会重新选一次文件.
 * 加参数只能解决播放这一半, 还会让所有 resolver 都认识一个只有 BT 才有的概念.
 *
 * 只活在本次进程内. 持久化由缓存记录 (`torrent_cache.pathInTorrent`) 负责, 见
 * [me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine].
 */
class TorrentFileOverrideStore {
    private val lock = SynchronizedObject()
    private val overrides = mutableMapOf<String, String>()

    fun put(media: Media, episodeSort: EpisodeSort, pathInTorrent: String) {
        synchronized(lock) { overrides[keyOf(media, episodeSort)] = pathInTorrent }
    }

    fun get(media: Media, episodeSort: EpisodeSort): String? =
        synchronized(lock) { overrides[keyOf(media, episodeSort)] }

    fun get(mediaId: String, episodeSort: EpisodeSort): String? =
        synchronized(lock) { overrides[keyOf(mediaId, episodeSort)] }

    // 用 origin 的 mediaId: 同一个种子在选择器里可能以 CachedMedia 出现 (整季包的派生命中),
    // 它的 mediaId 带缓存数据源前缀, 与网络 media 的不是同一个字符串.
    private fun keyOf(media: Media, episodeSort: EpisodeSort): String =
        keyOf((media as? CachedMedia)?.origin?.mediaId ?: media.mediaId, episodeSort)

    private fun keyOf(mediaId: String, episodeSort: EpisodeSort): String = "$mediaId@$episodeSort"

    companion object {
        /**
         * 各处默认使用的实例. 用单例而不是 Koin 注入, 是为了不改动三个平台各自的 Koin module
         * (Android/Desktop/iOS 各有一份 resolver 构造代码).
         */
        val Default = TorrentFileOverrideStore()
    }
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Picks the single storage that may cache this BitTorrent [media].
 *
 * One torrent is cached by at most one engine: an existing record decides, then the engine playback
 * actually used, then the preference. Both torrent storages share
 * [MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID], so the same torrent on both sides would produce two
 * caches with identical ids.
 *
 * @param playedWith the engine that is currently playing this media, if any
 */
internal suspend fun selectTorrentStorage(
    storages: List<MediaCacheStorage>,
    media: Media,
    playedWith: MediaCacheEngineKey? = null,
): MediaCacheStorage? {
    // Persisted ownership survives disabling an engine and failures to restore its records.
    val owner = storages.firstOrNull { it.hasRecordForMedia(media.mediaId) }
    if (owner != null) return owner.takeIf { it.engine.supports(media) }

    val supported = storages.filter { it.engine.supports(media) }
    if (supported.isEmpty()) return null

    playedWith
        ?.let { key -> supported.firstOrNull { it.engine.engineKey == key } }
        ?.let { return it }

    val preferred = preferredCacheEngineKey(media, supported.map { it.engine })
    return supported.firstOrNull { it.engine.engineKey == preferred } ?: supported.firstOrNull()
}

// Manual and automatic caches must choose the same engine to avoid duplicate downloads.
internal suspend fun preferredCacheEngineKey(
    media: Media,
    supported: List<MediaCacheEngine>,
): MediaCacheEngineKey? {
    if (media.kind != MediaSourceKind.BitTorrent) return null

    val pikPak = supported.firstOrNull { it.engineKey == MediaCacheEngineKey.PikPak }
    if (pikPak != null && pikPak.canServeWithin(CAN_SERVE_TIMEOUT, media.download.uri)) {
        return MediaCacheEngineKey.PikPak
    }
    return MediaCacheEngineKey.Anitorrent.takeIf { key -> supported.any { it.engineKey == key } }
}

// canServe may log in over the network; a stalled login must not block download creation.
private val CAN_SERVE_TIMEOUT = 10.seconds

private suspend fun MediaCacheEngine.canServeWithin(timeout: Duration, uri: String): Boolean {
    if (this !is TorrentMediaCacheEngine) return true
    return withTimeoutOrNull(timeout) { torrentEngine.canServe(uri) } ?: false
}

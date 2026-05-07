/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.offline

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * 云端离线下载后端 (Cloud offline download backend).
 *
 * 将一个磁链/种子提交到云盘服务 (PikPak, 115, 迅雷, ...), 等待云端下载完成,
 * 然后返回一个可直接流式播放的 HTTPS URL.
 */
interface OfflineDownloadEngine {
    /** Stable identifier, e.g. `"pikpak"`, `"cloud115"`. */
    val id: String

    /** Human-readable name for UI. */
    val displayName: String

    /**
     * `true` iff the engine is configured and ready to accept [resolve] calls.
     * Drives the `supports()` check in `OfflineDownloadMediaResolver`.
     */
    val isSupported: StateFlow<Boolean>

    /**
     * Submit the magnet (or http `.torrent` URL) to the provider, wait for the
     * offline download to finish, and return a playable HTTPS URL plus metadata.
     *
     * For multi-file torrents (season packs), the provider typically returns a
     * folder. The engine asks [pickVideoFile] to choose one of the folder's
     * children by filename; implementations should supply the caller's normal
     * file-selection logic here (e.g. reuse upstream's
     * `TorrentMediaResolver.selectVideoFileEntry`). Single-file torrents skip
     * the callback entirely.
     *
     * Throws on failure; callers translate exceptions to domain-level
     * `MediaResolutionException`. Coroutine cancellation cancels the resolve
     * cleanly.
     */
    suspend fun resolve(
        uri: String,
        pickVideoFile: (candidateFilenames: List<String>) -> String? = { null },
    ): ResolvedMedia
}

/**
 * The outcome of a successful [OfflineDownloadEngine.resolve].
 *
 * @property streamUrl HTTPS URL that `mediamp` can open. Usually short-lived
 *                    (PikPak URLs expire in ~24h) — callers should treat it as
 *                    fresh-at-this-moment and refetch if playback hits 403.
 * @property expiresAt Best-effort expiry instant; null if the provider doesn't
 *                    surface one. Not load-bearing — used only for diagnostics.
 * @property fileName Original filename from the torrent.
 * @property fileSize In bytes, if known.
 */
data class ResolvedMedia(
    val streamUrl: String,
    val expiresAt: Instant? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    /**
     * Provider-side file identifier, if any. Surfaced so callers (and tests)
     * can issue a follow-up cleanup — e.g. trash/delete the PikPak file after
     * the stream URL has been handed off. Null for providers without a concept
     * of server-side file ids.
     */
    val providerFileId: String? = null,
)

/**
 * Provider rejected the submitted magnet (unsupported scheme, dead torrent,
 * content violates ToS, etc.). Maps to `NO_MATCHING_RESOURCE` upstream.
 */
class OfflineDownloadRejectedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Provider authentication failed (wrong credentials, expired refresh token,
 * captcha challenge we cannot solve). Maps to `ENGINE_ERROR` upstream.
 */
class OfflineDownloadAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

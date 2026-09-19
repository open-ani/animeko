/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.details

import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.media.cache.DownloaderStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_details_http_state_resolving
import me.him188.ani.app.ui.lang.cache_details_last_error_summary
import me.him188.ani.app.ui.lang.cache_details_peers_none
import me.him188.ani.app.ui.lang.cache_details_peers_summary
import me.him188.ani.app.ui.lang.cache_details_segments
import me.him188.ani.app.ui.lang.cache_details_state_downloading
import me.him188.ani.app.ui.lang.cache_details_state_failed
import me.him188.ani.app.ui.lang.cache_details_torrent_state_checking_files
import me.him188.ani.utils.httpdownloader.DownloadError
import me.him188.ani.utils.httpdownloader.DownloadErrorCode
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.SegmentFailure
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class MediaDetailsLazyGridTest {
    @Test
    fun `torrent without peers explains the stalled download`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaDetailsLazyGrid(
                    TestMediaDetails,
                    downloader = DownloaderDetails(
                        cacheState = MediaCacheState.IN_PROGRESS,
                        stats = MediaCache.SessionStats.Unspecified,
                        status = DownloaderStatus.Torrent(
                            serviceConnected = true,
                            startup = DownloaderStatus.TorrentStartup.STARTED,
                            state = TorrentHandleState.CHECKING_FILES,
                            connectedPeers = 0,
                            seeds = 0,
                        ),
                    ),
                )
            }
        }
        val downloading = runBlocking { getString(Lang.cache_details_state_downloading) }
        val checking = runBlocking { getString(Lang.cache_details_torrent_state_checking_files) }
        onNodeWithText("$downloading · $checking").assertExists()
        onNodeWithText(runBlocking { getString(Lang.cache_details_peers_none) }).assertExists()
    }

    @Test
    fun `torrent with peers shows the peer summary`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaDetailsLazyGrid(
                    TestMediaDetails,
                    downloader = DownloaderDetails(
                        cacheState = MediaCacheState.IN_PROGRESS,
                        stats = MediaCache.SessionStats.Unspecified,
                        status = DownloaderStatus.Torrent(
                            serviceConnected = true,
                            startup = DownloaderStatus.TorrentStartup.STARTED,
                            state = TorrentHandleState.DOWNLOADING,
                            connectedPeers = 5,
                            seeds = 2,
                        ),
                    ),
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_details_state_downloading) }).assertExists()
        onNodeWithText(runBlocking { getString(Lang.cache_details_peers_summary, 5, 2) }).assertExists()
    }

    @Test
    fun `resolving placeholder explains the pending web download`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaDetailsLazyGrid(
                    TestMediaDetails,
                    downloader = DownloaderDetails(
                        cacheState = MediaCacheState.IN_PROGRESS,
                        stats = MediaCache.SessionStats.Unspecified,
                        status = DownloaderStatus.Resolving,
                    ),
                )
            }
        }
        val downloading = runBlocking { getString(Lang.cache_details_state_downloading) }
        val resolving = runBlocking { getString(Lang.cache_details_http_state_resolving) }
        onNodeWithText("$downloading · $resolving").assertExists()
    }

    @Test
    fun `http retry shows the last segment failure`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaDetailsLazyGrid(
                    TestMediaDetails,
                    downloader = DownloaderDetails(
                        cacheState = MediaCacheState.IN_PROGRESS,
                        stats = MediaCache.SessionStats.Unspecified,
                        status = DownloaderStatus.Http(
                            status = DownloadStatus.DOWNLOADING,
                            error = null,
                            downloadedSegments = 0,
                            totalSegments = 8,
                            lastSegmentFailure = SegmentFailure(
                                segmentIndex = 0,
                                attempt = 3,
                                maxAttempts = 100,
                                message = "HTTP 403 Forbidden",
                                timestampMillis = 0,
                            ),
                        ),
                    ),
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_details_last_error_summary, 3, 100, "HTTP 403 Forbidden") }).assertExists()
    }

    @Test
    fun `http failure shows the error and segment progress`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaDetailsLazyGrid(
                    TestMediaDetails,
                    downloader = DownloaderDetails(
                        cacheState = MediaCacheState.FAILED,
                        stats = MediaCache.SessionStats.Unspecified,
                        status = DownloaderStatus.Http(
                            status = DownloadStatus.FAILED,
                            error = DownloadError(DownloadErrorCode.UNEXPECTED_ERROR, "connection reset"),
                            downloadedSegments = 2,
                            totalSegments = 8,
                        ),
                    ),
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_details_state_failed) }).assertExists()
        onNodeWithText(runBlocking { getString(Lang.cache_details_segments, 2, 8) }).assertExists()
        onNodeWithText("connection reset").assertExists()
    }
}

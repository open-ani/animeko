/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.engines

import io.github.nihildigit.pikpak.SessionStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.domain.torrent.TorrentDownloaderInitializationException
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.torrent.pikpak.PikPakCredentials
import me.him188.ani.torrent.pikpak.PikPakDriveUsage
import me.him188.ani.torrent.pikpak.PikPakDriveItem
import me.him188.ani.torrent.pikpak.PikPakEngineConfig
import me.him188.ani.torrent.pikpak.PikPakTorrentDownloader
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

// Runs in-process on every platform; configuration and credentials can change without recreating the engine.
class PikPakEngine(
    private val config: StateFlow<PikPakConfig>,
    private val credentials: StateFlow<PikPakCredentials?>,
    private val sessionStore: SessionStore,
    client: ScopedHttpClient,
    override val saveDir: SystemPath,
    parentCoroutineContext: CoroutineContext,
) : TorrentEngine {
    private val logger = logger<PikPakEngine>()
    private val scope = parentCoroutineContext.childScope()

    override val type: TorrentEngineType get() = TorrentEngineType.PikPak

    override val location: MediaSourceLocation get() = MediaSourceLocation.Local

    override val isSupported: Boolean
        get() = config.value.enabled && credentials.value != null

    @OptIn(UnsafeScopedHttpClientApi::class)
    private val httpClient = client.borrowForever().client

    private val engineConfig: StateFlow<PikPakEngineConfig> = config
        .map { it.toEngineConfig() }
        .stateIn(scope, SharingStarted.Eagerly, config.value.toEngineConfig())

    private val downloaderLock = Mutex()

    @Volatile
    private var downloader: PikPakTorrentDownloader? = null

    override suspend fun testConnection(): Boolean {
        if (!isSupported) return false
        return try {
            (getDownloader() as PikPakTorrentDownloader).testConnection()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "PikPak connection test failed" }
            false
        }
    }

    override suspend fun canServe(uri: String): Boolean {
        if (!isSupported) return false
        return try {
            (getDownloader() as PikPakTorrentDownloader).canServe(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "PikPak canServe query failed for $uri, falling back to anitorrent" }
            false
        }
    }

    suspend fun driveUsage(): PikPakDriveUsage? {
        if (!isSupported) return null
        return (getDownloader() as PikPakTorrentDownloader).driveUsage()
    }

    suspend fun legacyFolderItems(): List<PikPakDriveItem> {
        if (!isSupported) return emptyList()
        return try {
            (getDownloader() as PikPakTorrentDownloader).legacyFolderItems()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to list the PikPak legacy folder" }
            emptyList()
        }
    }

    suspend fun clearLegacyFolder(ids: List<String>) {
        if (!isSupported) return
        (getDownloader() as PikPakTorrentDownloader).clearLegacyFolder(ids)
    }

    suspend fun sweepLeftoversOnStartup() {
        if (!isSupported) return
        try {
            getDownloader()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to start the PikPak engine for its startup sweep" }
        }
    }

    override suspend fun getDownloader(): TorrentDownloader {
        if (!isSupported) throw UnsupportedOperationException("PikPakEngine is not enabled")
        downloader?.let { return it }
        return downloaderLock.withLock {
            downloader?.let { return it }
            try {
                PikPakTorrentDownloader(
                    httpClient = httpClient,
                    credentials = credentials,
                    sessionStore = sessionStore,
                    rootDataDirectory = saveDir,
                    config = engineConfig,
                    parentCoroutineContext = scope.coroutineContext,
                ).also { created ->
                    downloader = created
                    scope.coroutineContext.job.invokeOnCompletion { created.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw TorrentDownloaderInitializationException(cause = e)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    // concurrency 不从 PikPakConfig 来: 上限由 PikPak 对同一个签名链接的连接数限制决定, 不是用户偏好,
    // 引擎侧的默认值已经取到这个上限.
    private fun PikPakConfig.toEngineConfig() = PikPakEngineConfig()
}

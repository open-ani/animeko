/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.engines

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.MediaSourceProxySettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.domain.torrent.AbstractTorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.torrent.anitorrent.AnitorrentDownloaderFactory
import me.him188.ani.app.torrent.anitorrent.AnitorrentTorrentDownloader
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentDownloaderFactory
import me.him188.ani.app.torrent.api.peer.PeerFilter
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext

class AnitorrentEngine(
    config: Flow<AnitorrentConfig>,
    proxySettings: Flow<ProxySettings>,
    peerFilterSettings: Flow<PeerFilterSettings>,
    private val saveDir: SystemPath,
    parentCoroutineContext: CoroutineContext,
    private val anitorrentFactory: TorrentDownloaderFactory = AnitorrentDownloaderFactory()
) : AbstractTorrentEngine<AnitorrentTorrentDownloader<*, *>, AnitorrentConfig>(
    type = TorrentEngineType.Anitorrent,
    config = config,
    parentCoroutineContext = parentCoroutineContext,
    proxySettings = proxySettings.map { it.default },
    peerFilterSettings = peerFilterSettings,
) {
    override val location: MediaSourceLocation get() = MediaSourceLocation.Local
    override val isSupported: Flow<Boolean>
        get() = flowOf(tryLoadLibraries())
    
    init {
        initialized.complete(Unit)
    }
    
    private fun tryLoadLibraries(): Boolean {
        try {
            anitorrentFactory.libraryLoader.loadLibraries()
            logger.info { "Loaded libraries for AnitorrentEngine" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load libraries for AnitorrentEngine" }
            return false
        }
    }

    override suspend fun testConnection(): Boolean = isSupported.first()

    private fun createTorrentFileDownloader(proxySettings: MediaSourceProxySettings): HttpFileDownloader {
        return createDefaultHttpClient {
            install(UserAgent) {
                agent = getAniUserAgent()
            }
            proxy(proxySettings.toClientProxyConfig())
            expectSuccess = true
        }.asHttpFileDownloader()
    }

    override suspend fun newInstance(
        config: AnitorrentConfig,
        proxySettings: MediaSourceProxySettings
    ): AnitorrentTorrentDownloader<*, *> {
        if (!isSupported.first()) {
            logger.error { "Anitorrent is disabled because it is not built. Read `/torrent/anitorrent/README.md` for more information." }
            throw UnsupportedOperationException("AnitorrentEngine is not supported")
        }
        return anitorrentFactory.createDownloader(
            rootDataDirectory = saveDir,
            createTorrentFileDownloader(proxySettings),
            config.toTorrentDownloaderConfig(),
            parentCoroutineContext = scope.coroutineContext,
        ) as AnitorrentTorrentDownloader<*, *>
    }

    override suspend fun AnitorrentTorrentDownloader<*, *>.applyConfig(config: AnitorrentConfig) {
        this.applyConfig(config.toTorrentDownloaderConfig())
    }

    override suspend fun AnitorrentTorrentDownloader<*, *>.applyPeerFilter(filter: PeerFilter) {
        this.setPeerFilter(filter)
    }

    private fun AnitorrentConfig.toTorrentDownloaderConfig() =
        TorrentDownloaderConfig(
            peerFingerprint = computeTorrentFingerprint(),
            userAgent = computeTorrentUserAgent(),
            handshakeClientVersion = "Anitorrent ${currentAniBuildConfig.fourDigitVersionCode}",
            downloadRateLimitBytes = downloadRateLimit.toLibtorrentRate(),
            uploadRateLimitBytes = uploadRateLimit.toLibtorrentRate(),
            shareRatioLimit = shareRatioLimit.toLibtorrentShareRatio(),
        )
}

private fun FileSize.toLibtorrentRate(): Int = when (this) {
    FileSize.Unspecified -> 0
    FileSize.Zero -> 1024 // libtorrent 没法禁用, 那就限速到 1KB/s
    else -> inBytes.toInt()
}

private fun Double.toLibtorrentShareRatio(): Int = times(100).toInt()

private fun computeTorrentFingerprint(
    versionCode: String = currentAniBuildConfig.fourDigitVersionCode,
): String = "-AL${versionCode}-"

private fun computeTorrentUserAgent(
    versionCode: String = currentAniBuildConfig.fourDigitVersionCode,
): String = "ani_libtorrent/${versionCode}"

private fun HttpClient.asHttpFileDownloader(): HttpFileDownloader = object : HttpFileDownloader {
    override suspend fun download(url: String): ByteArray = get(url).readBytes()
    override fun close() {
        this@asHttpFileDownloader.close()
    }

    override fun toString(): String {
        return "HttpClientAsHttpFileDownloader(client=$this@asHttpFileDownloader)"
    }
}

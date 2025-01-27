/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlin.coroutines.CoroutineContext

interface TorrentEngineFactory {
    fun createTorrentEngine(
        parentCoroutineContext: CoroutineContext,
        config: Flow<AnitorrentConfig>,
        client: ScopedHttpClient,
        peerFilterSettings: Flow<PeerFilterSettings>,
        saveDir: SystemPath,
    ): TorrentEngine
}

object LocalAnitorrentEngineFactory : TorrentEngineFactory {
    override fun createTorrentEngine(
        parentCoroutineContext: CoroutineContext,
        config: Flow<AnitorrentConfig>,
        client: ScopedHttpClient,
        peerFilterSettings: Flow<PeerFilterSettings>,
        saveDir: SystemPath
    ): TorrentEngine {
        return AnitorrentEngine(config, client, peerFilterSettings, saveDir, parentCoroutineContext)
    }
}
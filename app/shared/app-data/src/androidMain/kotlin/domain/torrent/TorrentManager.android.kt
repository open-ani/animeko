/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import android.os.Build
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.domain.torrent.client.RemoteAnitorrentEngine
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.utils.io.SystemPath
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext

actual fun createAniTorrentEngine(
    config: Flow<AnitorrentConfig>,
    proxySettings: Flow<ProxySettings>,
    peerFilterSettings: Flow<TorrentPeerConfig>,
    saveDir: SystemPath,
    parentCoroutineContext: CoroutineContext,
): TorrentEngine {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        RemoteAnitorrentEngine(
            KoinPlatform.getKoin().get<TorrentServiceConnection>(),
            config,
            proxySettings,
            peerFilterSettings,
            saveDir,
            parentCoroutineContext,
        )
    } else {
        AnitorrentEngine(
            config,
            proxySettings,
            peerFilterSettings,
            saveDir,
            parentCoroutineContext,
        )
    }
}
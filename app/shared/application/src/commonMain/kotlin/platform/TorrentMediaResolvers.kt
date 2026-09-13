/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType

/**
 * The torrent part of every platform's [MediaResolver] list, PikPak first and the local BT
 * engines after it. Platform modules append their own local-file, HTTP streaming and web
 * resolvers to the result.
 *
 * Hand PikPak the local-BT resolver as its fallback so a failing PikPak (auth/network/limit)
 * doesn't lock the user out of BT playback.
 */
fun torrentMediaResolvers(
    engines: List<TorrentEngine>,
    localEngineAccess: TorrentEngineAccess,
): List<MediaResolver> {
    val localTorrentResolvers = engines.filter { it.type != TorrentEngineType.PikPak }
        .map { TorrentMediaResolver(it, localEngineAccess) }

    val pikpakResolvers = engines.filter { it.type == TorrentEngineType.PikPak }
        .map {
            TorrentMediaResolver(
                it, AlwaysUseTorrentEngineAccess,
                fallback = localTorrentResolvers.firstOrNull(),
            )
        }

    return pikpakResolvers + localTorrentResolvers
}

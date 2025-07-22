/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import io.ktor.http.Url
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt

data class AniServer(
    val id: String,
    val url: Url,
)

object AniServers {
    val optimizedForCNWithName: List<AniServer>
    val optimizedForGlobalWithName: List<AniServer>

    init {
        val override = currentAniBuildConfig.overrideAniApiServer.takeIf { it.isNotBlank() }

        val (cn, global) = if (override != null) {
            val server = AniServer("api", Url(override))
            Pair(listOf(server), listOf(server))
        } else {
            getServers()
        }
        optimizedForCNWithName = cn
        optimizedForGlobalWithName = global
    }

    private fun getServers(): Pair<List<AniServer>, List<AniServer>> {
        val cnServers = arrayOf(
            AniServer("api", Url("https://api.animeko.org")),
            AniServer("danmaku-cn", Url("https://danmaku-cn.myani.org")),
            AniServer("s1", Url("https://s1.animeko.openani.org")),
        )

        val globalServers = arrayOf(
            AniServer("danmaku-global", Url("https://danmaku-global.myani.org")),
        )

        val cn = buildList(cnServers.size + globalServers.size) {
            cnServers.forEach { add(it) }
            globalServers.forEach { add(it) }
        }
        val global = buildList(globalServers.size + cnServers.size) {
            globalServers.forEach { add(it) }
            cnServers.forEach { add(it) }
        }
        return Pair(cn, global)
    }

    val optimizedForCN: List<Url> = optimizedForCNWithName.map { it.url }
    val optimizedForGlobal: List<Url> = optimizedForGlobalWithName.map { it.url }

    fun shouldUseGlobalServer(): Boolean {
        return TimeZone.currentSystemDefault().offsetAt(Clock.System.now()) != UtcOffset(8)
    }
}
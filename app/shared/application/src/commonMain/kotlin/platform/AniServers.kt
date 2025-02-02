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

object AniServers {
    val optimizedForCN: List<Url>
    val optimizedForGlobal: List<Url>

    init {
        val cnServers = arrayOf(
            Url("https://danmaku-cn.myani.org"),
            Url("https://auth.myani.org"),
            Url("https://s1.animeko.openani.org"),
            Url("https://s2.animeko.openani.org"),
        )

        val globalServers = arrayOf(
            Url("https://danmaku-global.myani.org"),
        )

        optimizedForCN = buildList(cnServers.size + globalServers.size) {
            cnServers.forEach { add(it) }
            globalServers.forEach { add(it) }
        }
        optimizedForGlobal = buildList(globalServers.size + cnServers.size) {
            globalServers.forEach { add(it) }
            cnServers.forEach { add(it) }
        }
    }
}
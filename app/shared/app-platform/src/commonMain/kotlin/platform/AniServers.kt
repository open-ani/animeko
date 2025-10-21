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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlin.time.Clock

data class AniServer(
    val id: String,
    val url: Url,
)

object AniServers {
    val allServers: List<AniServer>

    val overrideServer = currentAniBuildConfig.overrideAniApiServer.takeIf { it.isNotBlank() }
    const val DANMAKU_CN_MYANI_ORG = "https://danmaku-cn.myani.org"
    const val API_ANIMEKO_ORG = "https://api.animeko.org"
    const val DANMAKU_GLOBAL_MYANI_ORG = "https://danmaku-global.myani.org"
    const val S1_ANIMEKO_OPENANI_ORG = "https://s1.animeko.openani.org"

    val preferGlobal = listOf(
        AniServer("danmaku-global", Url(DANMAKU_GLOBAL_MYANI_ORG)),
        AniServer("api", Url(API_ANIMEKO_ORG)),
        AniServer("s1-animeko", Url(S1_ANIMEKO_OPENANI_ORG)),
        AniServer("danmaku-cn", Url(DANMAKU_CN_MYANI_ORG)),
    )

    val preferDirect = listOf(
        AniServer("api", Url(API_ANIMEKO_ORG)),
        AniServer("danmaku-global", Url(DANMAKU_GLOBAL_MYANI_ORG)),
        AniServer("danmaku-cn", Url(DANMAKU_CN_MYANI_ORG)),
        AniServer("s1-animeko", Url(S1_ANIMEKO_OPENANI_ORG)),
    )

    init {
        val override = overrideServer
        allServers = if (override != null) {
            val server = AniServer("api", Url(override))
            listOf(server)
        } else {
            listOf(
                AniServer("api", Url(API_ANIMEKO_ORG)),
                AniServer("danmaku-cn", Url(DANMAKU_CN_MYANI_ORG)),
                AniServer("danmaku-global", Url(DANMAKU_GLOBAL_MYANI_ORG)),
                AniServer("s1-animeko", Url(S1_ANIMEKO_OPENANI_ORG)),
            )
        }
    }

    fun shouldUseGlobalServer(): Boolean {
        return TimeZone.currentSystemDefault().offsetAt(Clock.System.now()) != UtcOffset(8)
    }
}
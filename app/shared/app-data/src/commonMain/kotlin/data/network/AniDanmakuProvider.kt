/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import me.him188.ani.app.data.network.protocol.DanmakuGetResponse
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.danmaku.api.AbstractDanmakuProvider
import me.him188.ani.danmaku.api.DanmakuFetchResult
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.danmaku.api.DanmakuProvider
import me.him188.ani.danmaku.api.DanmakuProviderConfig
import me.him188.ani.danmaku.api.DanmakuProviderFactory
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.app.data.network.protocol.DanmakuLocation as ProtocolDanmakuLocation
import me.him188.ani.danmaku.api.Danmaku as ApiDanmaku
import me.him188.ani.danmaku.api.DanmakuLocation as ApiDanmakuLocation

object AniBangumiSeverBaseUrls {
    const val GLOBAL = "https://danmaku-global.myani.org"
    const val CN = "https://danmaku-cn.myani.org"

    val list = listOf(CN, GLOBAL)

    fun getBaseUrl(useGlobal: Boolean) = if (useGlobal) GLOBAL else CN
}

class AniDanmakuProvider(
    config: DanmakuProviderConfig,
    private val client: ScopedHttpClient,
) : AbstractDanmakuProvider() {
    private val baseUrl = ServerListFeatureConfig.MAGIC_ANI_SERVER

    companion object {
        const val ID = "ani"
    }

    class Factory : DanmakuProviderFactory {
        override val id: String get() = ID

        override fun create(config: DanmakuProviderConfig, client: ScopedHttpClient): DanmakuProvider =
            AniDanmakuProvider(config, client)
    }

    override val id: String get() = ID

    override suspend fun fetch(request: DanmakuSearchRequest): List<DanmakuFetchResult> {
        val list = client.use { get("$baseUrl/v1/danmaku/${request.episodeId}").body<DanmakuGetResponse>().danmakuList }
        logger.info { "$ID Fetched danmaku list: ${list.size}" }
        return listOf(
            DanmakuFetchResult(
                matchInfo = DanmakuMatchInfo(
                    providerId = id,
                    count = list.size,
                    method = DanmakuMatchMethod.ExactId(request.subjectId, request.episodeId),
                ),
                list = list.asSequence().map {
                    ApiDanmaku(
                        id = it.id,
                        providerId = ID,
                        playTimeMillis = it.danmakuInfo.playTime,
                        senderId = it.senderId,
                        location = it.danmakuInfo.location.toApi(),
                        text = it.danmakuInfo.text,
                        color = it.danmakuInfo.color,
                    )
                },
            ),
        )
    }
}

fun ProtocolDanmakuLocation.toApi(): ApiDanmakuLocation = when (this) {
    ProtocolDanmakuLocation.TOP -> ApiDanmakuLocation.TOP
    ProtocolDanmakuLocation.BOTTOM -> ApiDanmakuLocation.BOTTOM
    ProtocolDanmakuLocation.NORMAL -> ApiDanmakuLocation.NORMAL
}

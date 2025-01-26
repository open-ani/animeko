/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.Dispatchers
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.ktor.userAgent
import kotlin.coroutines.CoroutineContext

interface DanmakuProviderFactory { // SPI interface
    /**
     * @see DanmakuProvider.id
     */
    val id: String

    fun create(
        config: DanmakuProviderConfig,
        client: ScopedHttpClient,
    ): DanmakuProvider
}

class DanmakuProviderConfig(
    val proxy: ClientProxyConfig? = null,
    val userAgent: String? = null,
    val useGlobal: Boolean = false,
    val coroutineContext: CoroutineContext = Dispatchers.Default,
    val dandanplayAppId: String,
    val dandanplayAppSecret: String,
)

fun HttpClientConfig<*>.applyDanmakuProviderConfig(
    config: DanmakuProviderConfig,
) {
    config.proxy?.let { proxy(it) }
    config.userAgent?.let { userAgent(it) }
}

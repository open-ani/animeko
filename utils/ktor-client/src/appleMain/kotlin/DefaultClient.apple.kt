/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert

actual fun getPlatformKtorEngine(): HttpClientEngineFactory<*> = Darwin

@OptIn(ExperimentalForeignApi::class)
actual fun HttpClientConfig<*>.engineMaxRequestsPerHost(value: Int) {
    @Suppress("UNCHECKED_CAST") // engine 块只会作用于实际的引擎配置, 类型在块内判断
    (this as HttpClientConfig<HttpClientEngineConfig>).engine {
        if (this !is DarwinClientEngineConfig) return@engine
        configureSession {
            HTTPMaximumConnectionsPerHost = value.convert()
        }
    }
}

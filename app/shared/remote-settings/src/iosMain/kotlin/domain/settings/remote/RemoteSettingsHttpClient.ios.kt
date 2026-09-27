/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import me.him188.ani.remote.settings.RemoteSettingsProtocol

actual fun createRemoteSettingsHttpClient(): HttpClient =
    HttpClient(Darwin) {
        followRedirects = false
        engine {
            configureSession {
                waitsForConnectivity = true
                connectionProxyDictionary = emptyMap<Any?, Any>()
            }
        }
        install(ContentNegotiation) { json(RemoteSettingsProtocol.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 15_000
        }
    }

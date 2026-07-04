/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.asScopedHttpClient
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.time.Duration.Companion.seconds

fun main() {
    val protocolOutput = System.out
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpRequestRetry) {
            maxRetries = 1
            delayMillis { 1_000 }
        }
        install(HttpCookies)
        install(HttpTimeout) {
            requestTimeoutMillis = 300.seconds.inWholeMilliseconds
            connectTimeoutMillis = 30.seconds.inWholeMilliseconds
            socketTimeoutMillis = 30.seconds.inWholeMilliseconds
        }
        BrowserUserAgent()
        followRedirects = true
        install(HttpRedirect) {
            checkHttpMethod = false
            allowHttpsDowngrade = true
        }
        expectSuccess = true
    }
    try {
        val service = SourceTestService(
            httpClient = client,
            registry = DataSourceRegistry(client.asScopedHttpClient()),
            json = json,
        )
        StdioMcpServer(
            input = System.`in`,
            output = protocolOutput,
            service = service,
            json = json,
        ).run()
    } finally {
        client.close()
    }
}

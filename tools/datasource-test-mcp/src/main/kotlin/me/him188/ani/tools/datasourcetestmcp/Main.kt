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
import me.him188.ani.app.domain.mediasource.web.DefaultSelectorMediaSourceEngine
import me.him188.ani.tools.datasourcetestmcp.info.AniInfoService
import me.him188.ani.tools.datasourcetestmcp.mcp.StdioMcpServer
import me.him188.ani.tools.datasourcetestmcp.mcp.buildToolRegistrations
import me.him188.ani.tools.datasourcetestmcp.resolver.WebViewVideoResolverEngine
import me.him188.ani.tools.datasourcetestmcp.selector.SelectorEngineService
import me.him188.ani.tools.datasourcetestmcp.source.DataSourceRegistry
import me.him188.ani.tools.datasourcetestmcp.source.SourceTestService
import me.him188.ani.tools.datasourcetestmcp.video.M3u8AdAnalyzer
import me.him188.ani.tools.datasourcetestmcp.video.MpvVideoAnalyzer
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbe
import me.him188.ani.tools.datasourcetestmcp.video.VideoService
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
            // Ani API 搜索接口冷启动可能需要 40s+
            socketTimeoutMillis = 90.seconds.inWholeMilliseconds
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
        val scopedClient = client.asScopedHttpClient()
        val resolver = WebViewVideoResolverEngine()
        val probe = VideoProbe(client)

        val aniInfoService = AniInfoService(client)
        val selectorEngineService = SelectorEngineService(
            engine = DefaultSelectorMediaSourceEngine(scopedClient),
            aniInfoService = aniInfoService,
            json = json,
            resolver = resolver,
            probe = probe,
        )
        val videoService = VideoService(
            probe = probe,
            analyzer = MpvVideoAnalyzer(),
            adAnalyzer = M3u8AdAnalyzer(client),
        )
        val sourceTestService = SourceTestService(
            httpClient = client,
            registry = DataSourceRegistry(scopedClient),
            json = json,
            resolver = resolver,
            probe = probe,
        )

        StdioMcpServer(
            input = System.`in`,
            output = protocolOutput,
            registrations = buildToolRegistrations(
                json = json,
                aniInfoService = aniInfoService,
                selectorEngineService = selectorEngineService,
                videoService = videoService,
                sourceTestService = sourceTestService,
            ),
            json = json,
        ).run()
    } finally {
        client.close()
    }
}

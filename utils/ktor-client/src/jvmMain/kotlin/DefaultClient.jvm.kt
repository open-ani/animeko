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
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asInput
import okhttp3.Dispatcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.math.max

actual fun getPlatformKtorEngine(): HttpClientEngineFactory<*> = OkHttp

/**
 * OkHttp 在 HTTP/1.x 下每个并发请求各占一条连接, 连接数只受 [engineMaxRequestsPerHost] 间接限制;
 * HTTP/2 下同一 host 的请求在一条连接上多路复用.
 */
actual fun HttpResponse.sharesConnectionsAcrossRequests(): Boolean =
    version == HttpProtocolVersion.HTTP_2_0 || version == HttpProtocolVersion.QUIC

actual fun HttpClientConfig<*>.engineMaxRequestsPerHost(value: Int) {
    @Suppress("UNCHECKED_CAST") // engine 块只会作用于实际的引擎配置, 类型在块内判断
    (this as HttpClientConfig<HttpClientEngineConfig>).engine {
        if (this !is OkHttpConfig) return@engine
        // Ktor 先给每个 OkHttpClient 装上默认的 Dispatcher, 再应用 config 块, 所以这里的 Dispatcher 会生效
        config {
            dispatcher(
                Dispatcher().apply {
                    maxRequestsPerHost = value
                    maxRequests = max(maxRequests, value)
                },
            )
        }
    }
}

suspend inline fun HttpResponse.bodyAsDocument(): Document = body()

internal actual fun getXmlConverter(): ContentConverter = XmlConverter

private object XmlConverter : ContentConverter {
    override suspend fun serialize(
        contentType: ContentType,
        charset: io.ktor.utils.io.charsets.Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? = null

    override suspend fun deserialize(
        charset: io.ktor.utils.io.charsets.Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        if (typeInfo.type.qualifiedName != Document::class.qualifiedName) return null
        content.awaitContent()
        val decoder = Charsets.UTF_8.newDecoder()
        val string = decoder.decode(content.toInputStream().asInput())
        return Jsoup.parse(string, charset.name())
    }
}

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // writeTo

package me.him188.ani.app.ui.foundation

import coil3.network.NetworkClient
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkRequestBody
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.ktor2.internal.writeTo
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import me.him188.ani.utils.ktor.WrapperHttpClient
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import kotlin.jvm.JvmInline

/**
 * Copied from [coil3.network.ktor2.internal.KtorNetworkClient],
 * but with support for [scopedClient]
 */
class ScopedHttpClientNetworkFetcher(
    private val scopedClient: WrapperHttpClient,
) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T
    ): T = scopedClient.use {
        prepareRequest(request.toHttpRequestBuilder()).execute { response ->
            block(response.toNetworkResponse())
        }
    }
}

@JvmInline
internal value class KtorNetworkClient(
    private val httpClient: HttpClient,
) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ) = httpClient.prepareRequest(request.toHttpRequestBuilder()).execute { response ->
        block(response.toNetworkResponse())
    }
}

private suspend fun NetworkRequest.toHttpRequestBuilder(): HttpRequestBuilder {
    val request = HttpRequestBuilder()
    request.url.takeFrom(url)
    request.method = HttpMethod.parse(method)
    request.headers.takeFrom(headers)
    body?.readByteArray()?.let(request::setBody)
    return request
}

private suspend fun NetworkRequestBody.readByteArray(): ByteArray {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readByteArray()
}

private suspend fun HttpResponse.toNetworkResponse(): NetworkResponse {
    return NetworkResponse(
        code = status.value,
        requestMillis = requestTime.timestamp,
        responseMillis = responseTime.timestamp,
        headers = headers.toNetworkHeaders(),
        body = KtorNetworkResponseBody(bodyAsChannel()),
        delegate = this,
    )
}

private fun HeadersBuilder.takeFrom(headers: NetworkHeaders) {
    for ((key, values) in headers.asMap()) {
        appendAll(key, values)
    }
}

private fun Headers.toNetworkHeaders(): NetworkHeaders {
    val headers = NetworkHeaders.Builder()
    for ((key, values) in entries()) {
        headers[key] = values
    }
    return headers.build()
}

@JvmInline
private value class KtorNetworkResponseBody(
    private val channel: ByteReadChannel,
) : NetworkResponseBody {

    @Suppress("INVISIBLE_MEMBER")
    override suspend fun writeTo(sink: BufferedSink) {
        channel.writeTo(sink)
    }

    @Suppress("INVISIBLE_MEMBER")
    override suspend fun writeTo(fileSystem: FileSystem, path: Path) {
        channel.writeTo(fileSystem, path)
    }

    override fun close() {
        channel.cancel()
    }
}

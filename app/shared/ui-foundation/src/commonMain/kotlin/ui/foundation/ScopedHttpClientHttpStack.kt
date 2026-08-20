/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import com.github.panpf.sketch.fetch.Fetcher
import com.github.panpf.sketch.fetch.HttpUriFetcher
import com.github.panpf.sketch.fetch.isHttpUri
import com.github.panpf.sketch.http.HttpHeaders
import com.github.panpf.sketch.http.HttpStack
import com.github.panpf.sketch.request.Extras
import com.github.panpf.sketch.request.RequestContext
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import me.him188.ani.utils.ktor.ScopedHttpClient
import io.ktor.http.HttpHeaders as KtorHttpHeaders

/**
 * Sketch HTTP stack backed by Ani's dynamically replaceable [ScopedHttpClient].
 *
 * The response block deliberately stays inside [ScopedHttpClient.use]. A borrowed Ktor client may
 * be closed as soon as the block returns, so neither the response nor its body can escape it.
 */
internal class ScopedHttpClientHttpStack(
    private val scopedClient: ScopedHttpClient,
) : HttpStack {
    override suspend fun <T> request(
        url: String,
        httpHeaders: HttpHeaders?,
        extras: Extras?,
        block: suspend (HttpStack.Response) -> T,
    ): T = scopedClient.use {
        val request = HttpRequestBuilder().apply {
            url(url)
            httpHeaders?.addList?.forEach { (name, value) -> headers.append(name, value) }
            httpHeaders?.setList?.forEach { (name, value) -> headers[name] = value }
        }
        prepareRequest(request).execute { response ->
            block(KtorResponse(response))
        }
    }

    override fun toString(): String = "ScopedHttpClientHttpStack"

    private class KtorResponse(
        private val response: HttpResponse,
    ) : HttpStack.Response {
        override val code: Int = response.status.value
        override val message: String = response.status.description
        override val contentLength: Long =
            response.headers[KtorHttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        override val contentType: String? = response.headers[KtorHttpHeaders.ContentType]

        override fun getHeaderField(name: String): String? = response.headers[name]

        override suspend fun content(): HttpStack.Content = KtorContent(response.bodyAsChannel())
    }

    private class KtorContent(
        private val channel: ByteReadChannel,
    ) : HttpStack.Content {
        override suspend fun read(buffer: ByteArray): Int =
            channel.readAvailable(buffer, 0, buffer.size)

        override fun close() {
            channel.cancel()
        }
    }
}

/** Registers Sketch's standard HTTP fetch pipeline with Ani's scoped Ktor stack. */
internal class ScopedHttpClientHttpUriFetcherFactory(
    private val httpStack: ScopedHttpClientHttpStack,
) : Fetcher.Factory {
    override val sortWeight: Int = HttpUriFetcher.SORT_WEIGHT

    override fun create(requestContext: RequestContext): HttpUriFetcher? {
        if (!isHttpUri(requestContext.request.uri)) return null
        return HttpUriFetcher(
            sketch = requestContext.sketch,
            httpStack = httpStack,
            request = requestContext.request,
            downloadCacheKey = requestContext.downloadCacheKey,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ScopedHttpClientHttpUriFetcherFactory && httpStack == other.httpStack

    override fun hashCode(): Int = httpStack.hashCode()

    override fun toString(): String = "ScopedHttpClientHttpUriFetcherFactory"
}

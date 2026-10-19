/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.DEFAULT_BUFFER_SIZE
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.bufferedSink
import me.him188.ani.utils.io.bufferedSource
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * [downloadToFile] 同时发出的分块请求数. 调用方需要让 client 对单个 host 放行这么多并发请求, 见 `engineMaxRequestsPerHost`.
 *
 * GitHub artifact 的直链 (Azure Blob, HTTP/1.1) 单连接只有 2 MB/s 左右; 实测 300 MB 的 artifact 用 4 个连接下载 47 秒,
 * 8 个 38 秒, 再多收益很小.
 */
const val PARALLEL_DOWNLOAD_CONNECTIONS = 8

/**
 * 块越小, 最后几块被慢连接拖住的时间越短, 但请求数越多. 同一次下载里各连接的速度可以相差数倍.
 */
internal const val DOWNLOAD_CHUNK_BYTES = 4 * 1024 * 1024L

/**
 * 把 [url] 下载到 [target], 覆盖已有文件.
 *
 * 先以 `Range: bytes=0-0` 探测. 服务器支持 Range 时把文件切成 [DOWNLOAD_CHUNK_BYTES] 大小的块,
 * 由 [PARALLEL_DOWNLOAD_CONNECTIONS] 个并发请求依次领取, 快的连接多下几块; 失败的块单独重试.
 * 服务器忽略 Range 时直接写入完整响应, 即单连接下载.
 * 分块请求在整个下载过程中陆续发出, 所以 [url] 必须在下载期间一直有效.
 *
 * client 没有自动跟随重定向时由本函数跟随. 不论 client 的 `expectSuccess` 如何, 非预期的状态码都抛出 [DownloadHttpException].
 *
 * [onProgress] 在下载过程中周期性回调, 可能从多个线程同时回调; 完成时最后回调一次.
 */
suspend fun HttpClient.downloadToFile(
    url: String,
    target: SystemPath,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
) {
    var currentUrl = url
    var redirects = 0
    while (true) {
        when (val probe = probeDownload(currentUrl, target, onProgress)) {
            is DownloadProbe.Redirect -> {
                if (++redirects > MAX_REDIRECTS) {
                    throw DownloadHttpException(HttpStatusCode.Found, "重定向次数过多: $url")
                }
                currentUrl = resolveRedirect(currentUrl, probe.location)
            }

            is DownloadProbe.RangeSupported -> return downloadChunks(probe.url, target, probe.totalBytes, onProgress)
            DownloadProbe.Completed -> return
        }
    }
}

/**
 * 下载时服务器返回了非预期的状态码.
 */
class DownloadHttpException(
    val status: HttpStatusCode,
    message: String,
) : Exception("HTTP ${status.value}: $message")

private sealed interface DownloadProbe {
    class Redirect(val location: String) : DownloadProbe

    /**
     * @param url client 自动跟随重定向后的最终地址
     */
    class RangeSupported(val url: String, val totalBytes: Long) : DownloadProbe

    /**
     * 服务器忽略了 Range, 完整的文件已经写入.
     */
    data object Completed : DownloadProbe
}

/**
 * 不用 HEAD 探测: 预签名直链的签名可能绑定了 GET 方法.
 */
private suspend fun HttpClient.probeDownload(
    url: String,
    target: SystemPath,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): DownloadProbe {
    return prepareGet(url) {
        header(HttpHeaders.Range, "bytes=0-0")
        configureDownloadRequest()
    }.execute { response ->
        val status = response.status
        when {
            status.value in 300..399 -> DownloadProbe.Redirect(
                response.headers[HttpHeaders.Location]
                    ?: throw DownloadHttpException(status, "服务器返回了重定向但没有 Location 头"),
            )

            status == HttpStatusCode.PartialContent -> {
                // Content-Range: bytes 0-0/<total>
                val totalBytes = response.headers[HttpHeaders.ContentRange]
                    ?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }
                    ?: throw DownloadHttpException(status, "服务器返回的 Content-Range 没有文件大小")
                DownloadProbe.RangeSupported(response.request.url.toString(), totalBytes)
            }

            status.isSuccess() -> {
                val total = response.contentLength()
                logger.info { "Downloading $url in one connection, total=$total, target=$target" }
                val progress = DownloadProgress(total, onProgress)
                writeBody(response, target) { progress.add(it.toLong()) }
                progress.complete()
                DownloadProbe.Completed
            }

            else -> throw response.toDownloadException()
        }
    }
}

/**
 * 各块写入 [target] 旁的临时文件, 全部完成后按顺序拼接.
 */
@OptIn(ExperimentalAtomicApi::class)
private suspend fun HttpClient.downloadChunks(
    url: String,
    target: SystemPath,
    totalBytes: Long,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
) {
    val chunkCount = ((totalBytes + DOWNLOAD_CHUNK_BYTES - 1) / DOWNLOAD_CHUNK_BYTES).toInt()
    val chunkFiles = List(chunkCount) { target.resolveSibling("${target.name}.part$it") }
    logger.info { "Downloading $url in $chunkCount chunks, total=$totalBytes, target=$target" }

    val progress = DownloadProgress(totalBytes, onProgress)
    val nextChunk = AtomicInt(0)
    try {
        coroutineScope {
            repeat(minOf(PARALLEL_DOWNLOAD_CONNECTIONS, chunkCount)) {
                launch {
                    while (true) {
                        val index = nextChunk.fetchAndAdd(1)
                        if (index >= chunkCount) break
                        val start = index * DOWNLOAD_CHUNK_BYTES
                        val range = start..<minOf(start + DOWNLOAD_CHUNK_BYTES, totalBytes)
                        downloadChunk(url, range, chunkFiles[index], progress)
                    }
                }
            }
        }
        withContext(Dispatchers.IO_) {
            target.bufferedSink().use { sink ->
                for (chunk in chunkFiles) {
                    chunk.bufferedSource().use { it.transferTo(sink) }
                    // 边拼接边删除, 磁盘峰值占用约为文件大小加一块
                    chunk.delete()
                }
            }
        }
    } finally {
        chunkFiles.forEach { it.delete() }
    }
    progress.complete()
}

private suspend fun HttpClient.downloadChunk(
    url: String,
    range: LongRange,
    file: SystemPath,
    progress: DownloadProgress,
) {
    var attempt = 1
    while (true) {
        var written = 0L
        try {
            prepareGet(url) {
                header(HttpHeaders.Range, "bytes=${range.first}-${range.last}")
                configureDownloadRequest()
            }.execute { response ->
                when {
                    response.status == HttpStatusCode.PartialContent -> {}
                    // 不读响应体: 服务器没按 Range 返回时响应体是整个文件
                    response.status.isSuccess() -> throw DownloadHttpException(response.status, "服务器没有按 Range 返回分块")
                    else -> throw response.toDownloadException()
                }
                writeBody(response, file) { read ->
                    written += read
                    progress.add(read.toLong())
                }
            }
            val expected = range.last - range.first + 1
            if (written != expected) {
                throw IOException("分块 $range 应有 $expected 字节, 实际收到 $written 字节")
            }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progress.add(-written)
            if (attempt >= CHUNK_ATTEMPTS || !e.isRetryable()) throw e
            logger.info(e) { "Chunk $range of $url failed on attempt $attempt, retrying" }
            delay(CHUNK_RETRY_DELAY * attempt)
            attempt++
        }
    }
}

/**
 * 网络错误和服务器 5xx 可能是暂时的; 4xx (例如直链过期后的 403) 重试也不会成功.
 */
private fun Exception.isRetryable(): Boolean = when (this) {
    is DownloadHttpException -> status.value >= 500
    is IOException -> true
    else -> false
}

private fun HttpRequestBuilder.configureDownloadRequest() {
    // 自行检查状态码, 才能手动跟随重定向
    expectSuccess = false
    timeout {
        // 单连接下载整个文件可能要好几分钟, 不能用 client 默认的请求超时
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

private suspend fun writeBody(response: HttpResponse, target: SystemPath, onRead: (Int) -> Unit) {
    val channel = response.bodyAsChannel()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    withContext(Dispatchers.IO_) {
        target.bufferedSink().use { sink ->
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                onRead(read)
            }
        }
    }
}

private suspend fun HttpResponse.toDownloadException(): DownloadHttpException {
    val body = runCatching { bodyAsText() }.getOrDefault("")
    return DownloadHttpException(status, body.take(200).ifBlank { status.toString() })
}

/**
 * 按 RFC 3986 把 `Location` 头 [location] 解析为绝对地址: 绝对地址原样使用, 相对地址基于 [base] 解析且不继承 [base] 的 query.
 */
private fun resolveRedirect(base: String, location: String): String {
    if (ABSOLUTE_URL_REGEX.containsMatchIn(location)) return location
    return URLBuilder(base).apply {
        parameters.clear()
        fragment = ""
        takeFrom(location)
    }.buildString()
}

/**
 * 累计各连接读到的字节数, 每跨过一个 [PROGRESS_REPORT_INTERVAL_BYTES] 回调一次 [onProgress]. [add] 可以从多个线程同时调用.
 */
@OptIn(ExperimentalAtomicApi::class)
private class DownloadProgress(
    private val totalBytes: Long?,
    private val onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
) {
    private val downloaded = AtomicLong(0)

    /**
     * @param bytes 分块重试时为负数, 撤回失败的那次尝试已经计入的字节
     */
    fun add(bytes: Long) {
        val after = downloaded.addAndFetch(bytes)
        if (after / PROGRESS_REPORT_INTERVAL_BYTES != (after - bytes) / PROGRESS_REPORT_INTERVAL_BYTES) {
            onProgress(after, totalBytes)
        }
    }

    fun complete() {
        val bytes = downloaded.load()
        onProgress(bytes, totalBytes ?: bytes)
    }
}

private const val MAX_REDIRECTS = 5
private const val REQUEST_TIMEOUT_MILLIS = 1_000_000L
private const val CHUNK_ATTEMPTS = 3
private val CHUNK_RETRY_DELAY = 1.seconds
private const val PROGRESS_REPORT_INTERVAL_BYTES = 512 * 1024L
private val ABSOLUTE_URL_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
private val logger = logger("ParallelDownload")

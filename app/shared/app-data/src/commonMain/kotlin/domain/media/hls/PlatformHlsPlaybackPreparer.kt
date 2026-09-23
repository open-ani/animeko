/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.media.player.prefetch.PrefetchSegmentInfo
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.ktor.engineMaxRequestsPerHost
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.UriMediaData
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 在本机 127.0.0.1 上起一个极简 HTTP 服务代理 HLS 播放:
 *
 * - [HlsPlaybackOptions.filterSegments]: 改写播放列表, 移除疑似广告分片 ([HlsManifestFilter]).
 *   可能含广告的播放列表同时代理分片: 转发时把时间戳平移到播放列表时间轴, 并在探测广告期间预先下载起播要用的分片 (首片, 续播时为续播位置处的分片).
 * - [HlsPlaybackOptions.proxySegments]: 把媒体分片的地址也改写到本地, 由本地转发. 这样才能在播放器之外
 *   提前下载指定时间范围的分片 ([HlsPlaybackProxySession.setPrefetchRange]), 供自动跳过 OP/ED 后立即续播.
 *
 * 分片代理只对点播 (含 `#EXT-X-ENDLIST`) 且不使用 `#EXT-X-BYTERANGE` 的播放列表启用.
 *
 * 逻辑与平台无关, 只有 socket 部分由各平台的 [HlsProxyServer] 提供.
 */
class PlatformHlsPlaybackPreparer internal constructor(
    private val httpClientProvider: HttpClientProvider,
    private val segmentCacheMaxBytes: Long,
    private val serverFactory: HlsProxyServerFactory,
) : HlsPlaybackPreparer {
    /**
     * @param segmentCacheMaxBytes 预缓存分片的内存缓存上限. 正在被预缓存请求引用的分片不会被淘汰.
     */
    constructor(
        httpClientProvider: HttpClientProvider,
        segmentCacheMaxBytes: Long = DEFAULT_SEGMENT_CACHE_MAX_BYTES,
    ) : this(httpClientProvider, segmentCacheMaxBytes, PlatformHlsProxyServerFactory)

    override suspend fun prepare(
        data: UriMediaData,
        options: HlsPlaybackOptions,
        startPositionHintMillis: Long?,
    ): HlsPlaybackPreparerResult {
        if (!options.isEnabled) {
            return HlsPlaybackPreparerResult(data)
        }
        if (!data.uri.isCandidateHlsUri()) {
            return HlsPlaybackPreparerResult(data)
        }
        // 先借出会话的 client 再取播放列表: 取播放列表建好的连接留给之后的变体列表、探测和分片.
        // 会话建成后归它所有, 由 LocalHlsProxySession.close 归还; 没有建成会话时在这里归还.
        val sessionClient = SessionHttpClient(
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER, maxRequestsPerHost = SESSION_MAX_REQUESTS_PER_HOST),
        )
        var session: LocalHlsProxySession? = null
        try {
            var baseUri = data.uri
            val manifest = try {
                val response = sessionClient.client.get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }
                baseUri = response.call.request.url.toString()
                response.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return HlsPlaybackPreparerResult(data)
            }

            session = try {
                LocalHlsProxySession.createOrNull(
                    manifest = manifest,
                    baseUri = baseUri,
                    headers = data.headers,
                    client = sessionClient,
                    options = options,
                    startPositionHintMillis = startPositionHintMillis,
                    segmentCacheMaxBytes = segmentCacheMaxBytes,
                    serverFactory = serverFactory,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to prepare HLS playback proxy; falling back to original media data" }
                null
            } ?: return HlsPlaybackPreparerResult(data)

            return HlsPlaybackPreparerResult(
                data = UriMediaData(session.playlistUri, data.headers, data.extraFiles),
                session = session,
            )
        } finally {
            if (session == null) sessionClient.close()
        }
    }

    companion object {
        const val DEFAULT_SEGMENT_CACHE_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}

/**
 * 一个代理会话独占的 [HttpClient]: 创建时借出, [close] 时归还, 会话发出的请求 (播放列表、时间戳探测、
 * 首片与范围预缓存、分片转发) 都经由它.
 *
 * Ktor 的 OkHttp 引擎从同一个原型 OkHttpClient 派生各个 client, 连接池全进程共用; 任一 client 关闭时,
 * 引擎清空这个池里的全部空闲连接. 应用里每次 [HttpClientProvider.get] 都会得到新的 client (它附带的
 * DistributionChannelFeature 取值是一个捕获变量的 lambda, 每次调用都不相等, 对象池的键永远对不上), 归还时引用计数归零即关闭.
 * 所以逐个请求借还 client 的话, 每个请求结束都清掉刚建好的连接, 下一个请求重新握手. 实测到这几个源站的 TLS 握手
 * 0.4-1.7 秒 (ffzy 1.66 秒, dytt-hot 1.35 秒, dytt-cinema 0.39 秒, modu 0.40 秒), 它们都支持 HTTP/2 (ALPN h2).
 *
 * 会话从头到尾持有同一个 client, 自己的请求不会触发清空: 取主播放列表时建好的连接由之后的请求复用,
 * HTTP/2 下探测与播放器的请求在这条连接上多路复用. 应用其他地方关闭 client 时仍会清掉会话的空闲连接,
 * 正在传输的连接不受影响.
 *
 * 并发上限见 [SESSION_MAX_REQUESTS_PER_HOST].
 */
@OptIn(UnsafeScopedHttpClientApi::class)
private class SessionHttpClient(private val scopedClient: ScopedHttpClient) : AutoCloseable {
    private val ticket = scopedClient.borrow()
    private val released = atomic(false)

    val client: HttpClient get() = ticket.client

    /** 可重复调用, 只归还一次. */
    override fun close() {
        if (released.compareAndSet(false, true)) {
            scopedClient.returnClient(ticket)
        }
    }
}

/**
 * 探测耗时主要是请求往返而不是传输, 并发决定要等几轮. 实测 287 组的播放列表 16 路 14.4 秒、32 路 7.7 秒、
 * 64 路 4.4 秒 (每请求新建连接), 这几档都没有遇到限流.
 *
 * 实测的几个 CDN 都走 HTTP/2, 会话 client 的探测复用同一条连接, 每一轮的耗时与这一轮发出多少个请求基本无关:
 * 一集 63 组、32 路时, 首批 32 个 1.6 秒同时返回, 其余 31 个排到第二轮, 全部完成用了 5.3 秒.
 * 常见的一集在 70 组以内, 取 64 让它们一轮发完. 不走 HTTP/2 的源站会因此同时建立这么多条连接, 所以不再往上加.
 */
private const val PTS_PROBE_CONCURRENCY = 64

/**
 * 探测期间预缓存的开头分片数. 不探测时播放器拿到播放列表就开始下载, mpv (libavformat) 打开时先读前两片分析流信息,
 * 实测第二片常要数秒. 探测把交出播放列表推迟了, 这段时间里播放器本该下载的就是这两片, 预缓存它们才抵消探测的等待.
 */
private const val STARTUP_PREFETCH_SEGMENTS = 2

/**
 * 会话 client 对单个 host 的并发请求上限. 探测最多占 [PTS_PROBE_CONCURRENCY] 个, 其余留给同时进行的
 * 首片预缓存、范围预缓存和播放器的分片与播放列表请求, 以免它们在引擎里排在探测后面.
 * 引擎默认只放行 5 个, 见 [engineMaxRequestsPerHost].
 */
private const val SESSION_MAX_REQUESTS_PER_HOST = PTS_PROBE_CONCURRENCY + 8

/**
 * 一次播放对应一个代理会话. 关闭后本地端口释放, 所有预缓存任务取消.
 */
private class LocalHlsProxySession private constructor(
    private val headers: Map<String, String>,
    /** 会话发出的所有请求都用它, 关闭会话时归还. 见 [SessionHttpClient]. */
    private val sessionClient: SessionHttpClient,
    private val options: HlsPlaybackOptions,
    /** 见 [HlsPlaybackPreparer.prepare] 与 [StartPositionPrefetch]. */
    private val startPositionHintMillis: Long?,
    segmentCacheMaxBytes: Long,
    private val server: HlsProxyServer,
) : HlsPlaybackProxySession {
    private val closed = atomic(false)
    private val client: HttpClient get() = sessionClient.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO_ + CoroutineName("HlsProxy-${server.port}"))

    /** 保护 [nextRouteId], [playlistRoutes] 和 [segmentRoutes]: 播放器会并发请求多个播放列表. */
    private val routesLock = SynchronizedObject()
    private var nextRouteId = 1

    /** `/playlist/N.m3u8` -> 远端播放列表 */
    private val playlistRoutes = HashMap<String, String>()

    /** `/segment/N` -> 分片 */
    private val segmentRoutes = HashMap<String, ProxiedSegment>()

    /** 最近一次提供给播放器的、分片已代理的媒体播放列表. 预缓存以它的时间轴为准. */
    @Volatile
    private var activePlaylist: ProxiedPlaylist? = null

    private lateinit var initialContent: String

    /**
     * 远端媒体播放列表地址 -> 给播放器的本地版本. 播放器会重复请求同一个播放列表 (预览缩略图的播放器也会请求),
     * 负载下再探测一次可能得出不同结论, 交给播放器一份分片不同的列表, 时间轴和跳转都对不上.
     * 所以点播列表每个会话只处理一次, 之后原样复用; 并发的首次请求共用同一次处理.
     *
     * 处理在 [scope] 中进行, 请求方断开不会取消它. 失败的结果不保留; 直播列表会更新, 结果也不保留. 两者下次请求都重新获取.
     */
    private val localPlaylistsLock = SynchronizedObject()
    private val localPlaylists = HashMap<String, Deferred<LocalPlaylist>>()

    val playlistUri: String = "http://127.0.0.1:${server.port}/playlist.m3u8"

    // ---------- 预缓存 ----------

    private val segmentCache = SegmentCache(maxBytes = segmentCacheMaxBytes)
    private val prefetchLock = SynchronizedObject()
    private var requestedPrefetchRange: MediaTimeRange? = null
    private var prefetchJob: Job? = null

    /** 当前预缓存任务针对的分片地址, 用于判断新的请求是否与正在进行的完全相同. */
    private var prefetchTargetUris: List<String> = emptyList()

    /** 请求已被清除: 正在下载的分片继续下完, 但不再开始新的. */
    @Volatile
    private var prefetchStopRequested = false
    private val prefetchProgressFlow = MutableStateFlow<List<PrefetchSegmentInfo>>(emptyList())

    override val prefetchProgress: Flow<List<PrefetchSegmentInfo>> get() = prefetchProgressFlow

    override fun setPrefetchRange(range: MediaTimeRange?) {
        if (!options.proxySegments) return
        synchronized(prefetchLock) {
            if (requestedPrefetchRange == range) return
            requestedPrefetchRange = range
            restartPrefetchLocked()
        }
    }

    private fun onActivePlaylistChanged() {
        synchronized(prefetchLock) {
            if (requestedPrefetchRange != null) restartPrefetchLocked()
        }
    }

    private fun restartPrefetchLocked() {
        val range = requestedPrefetchRange
        val playlist = activePlaylist
        val targets = if (range == null || playlist == null || closed.value) {
            emptyList()
        } else {
            playlist.segments.filter { it.timeRange.overlaps(range) }
        }
        val targetUris = targets.map { it.remoteUri }
        // 播放器会重复请求同一个播放列表 (每次都会走到这里). 目标分片没变时保留正在进行的任务,
        // 否则会反复取消重下, 白白浪费已经下载了一半的分片.
        if (targetUris.isNotEmpty() && targetUris == prefetchTargetUris && prefetchJob?.isActive == true) {
            return
        }
        if (targets.isEmpty()) {
            // 请求被清除. 最常见的原因是播放器已经跳到了预缓存的位置, 此时正在下载的那个分片很可能就是它马上要的:
            // 让这一片下完 (播放器的请求会直接等它, 见 serveSegment), 只是不再开始新的. 直接取消的话播放器得从头重下.
            prefetchStopRequested = true
            prefetchTargetUris = emptyList()
            prefetchProgressFlow.value = emptyList()
            return
        }
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchStopRequested = false
        prefetchTargetUris = targetUris
        range!!
        logger.info { "HLS prefetch $range -> segments ${targets.first().index}..${targets.last().index}" }
        segmentCache.pin(targets.map { it.remoteUri })
        prefetchProgressFlow.value = targets.map { segment ->
            val state = if (segmentCache.isComplete(segment.remoteUri)) ChunkState.DONE else ChunkState.DOWNLOADING
            PrefetchSegmentInfo(segment.timeRange, state)
        }
        prefetchJob = scope.launch {
            for (segment in targets) {
                if (prefetchStopRequested) break
                if (segmentCache.isComplete(segment.remoteUri)) continue
                val success = try {
                    // 缓存里存改写后的字节: 命中时走 serveBytes, 不经过转发循环, 没有第二次改写的机会
                    segmentCache.getOrDownload(segment.remoteUri) {
                        rewriteWhole(segment, downloadSegment(segment.remoteUri))
                    }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn(e) { "HLS prefetch failed for segment ${segment.index}: ${segment.remoteUri}" }
                    false
                }
                prefetchProgressFlow.update { list ->
                    if (success) {
                        list.map { if (it.range == segment.timeRange) it.copy(state = ChunkState.DONE) else it }
                    } else {
                        list.filterNot { it.range == segment.timeRange }
                    }
                }
            }
        }
    }

    /**
     * 把整片的时间戳平移到播放列表时间轴. 改写严格等长, 所以之后按 `Range` 切片仍然正确.
     */
    private suspend fun rewriteWhole(segment: ProxiedSegment, bytes: ByteArray): ByteArray {
        if (!segment.rewriteTimestamps) return bytes
        val out = ByteArray(bytes.size)
        var written = 0
        val sink = TsByteSink { buffer, offset, length ->
            if (written + length > out.size) {
                // 改写理应等长, 真不等长时宁可原样返回也不要截断出一个坏分片
                throw IOException("Rewritten segment grew beyond ${out.size} bytes")
            }
            buffer.copyInto(out, written, offset, offset + length)
            written += length
        }
        return try {
            val rewriter = TsTimestampRewriter(segment.timeRange.startMillis)
            rewriter.rewrite(bytes, 0, bytes.size, sink)
            rewriter.finish(sink)
            if (written == out.size) out else bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to rewrite timestamps of ${segment.remoteUri}, serving as is" }
            bytes
        }
    }

    private suspend fun downloadSegment(remoteUri: String): ByteArray {
        val response = client.get(remoteUri) {
            this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
        }
        if (response.status.value !in 200..299) {
            throw IOException("Remote returned ${response.status} for $remoteUri")
        }
        return response.bodyAsBytes()
    }

    // ---------- HTTP 服务 ----------

    private suspend fun respond(request: HlsProxyRequest, output: HlsProxyResponseSink) {
        val path = request.path
        val segment = synchronized(routesLock) { segmentRoutes[path] }
        if (segment != null) {
            serveSegment(segment, request, output)
            return
        }
        val content = try {
            playlistContentFor(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare HLS playlist proxy response" }
            null
        }
        if (content == null) {
            output.write(errorResponseHeader(502, "Bad Gateway").encodeToByteArray())
        } else {
            val bytes = content.encodeToByteArray()
            output.write(playlistResponseHeader(bytes.size).encodeToByteArray())
            output.write(bytes)
        }
    }

    private suspend fun playlistContentFor(path: String): String? {
        if (path == "/playlist.m3u8") {
            return initialContent
        }
        val remoteUri = synchronized(routesLock) { playlistRoutes[path] } ?: return null
        return localPlaylistFor(remoteUri).await().content
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun localPlaylistFor(remoteUri: String): Deferred<LocalPlaylist> {
        var created = false
        val computation = synchronized(localPlaylistsLock) {
            localPlaylists.getOrPut(remoteUri) {
                created = true
                scope.async(start = CoroutineStart.LAZY) { fetchRemotePlaylist(remoteUri).toLocalPlaylist() }
            }
        }
        if (created) {
            computation.invokeOnCompletion { cause ->
                val reusable = cause == null && computation.getCompleted().isVod
                if (!reusable) {
                    synchronized(localPlaylistsLock) {
                        if (localPlaylists[remoteUri] === computation) localPlaylists.remove(remoteUri)
                    }
                }
            }
            computation.start()
        }
        return computation
    }

    private suspend fun fetchRemotePlaylist(uri: String): RemotePlaylist {
        val response = client.get(uri) {
            this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
        }
        return RemotePlaylist(response.bodyAsText(), response.call.request.url.toString())
    }

    /**
     * 提供分片: 已预缓存的直接从内存返回, 否则从远端流式转发 (不缓存).
     */
    private suspend fun serveSegment(segment: ProxiedSegment, request: HlsProxyRequest, output: HlsProxyResponseSink) {
        val cached = segmentCache.getCompleted(segment.remoteUri) ?: segmentCache.awaitInFlight(segment.remoteUri)
        if (cached != null) {
            serveBytes(cached, request.headers["range"], output)
            return
        }
        // Range 请求拿到的不是分片开头, 流式改写定不了锚. 取整片改写后再切片, 改写等长所以偏移不变.
        if (segment.rewriteTimestamps && request.headers["range"] != null) {
            val whole = rewriteWhole(segment, downloadSegment(segment.remoteUri))
            serveBytes(whole, request.headers["range"], output)
            return
        }
        var headersWritten = false
        try {
            client.prepareGet(segment.remoteUri) {
                // 源站的错误状态原样转发给播放器, 由播放器决定重试策略
                expectSuccess = false
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
                request.headers["range"]?.let { header(HttpHeaders.Range, it) }
            }.execute { response ->
                val contentLength = response.contentLength()
                val header = buildString {
                    append("HTTP/1.1 ").append(response.status.value).append(' ').append(response.status.description).append("\r\n")
                    append("Content-Type: ").append(response.contentType()?.toString() ?: DEFAULT_SEGMENT_CONTENT_TYPE).append("\r\n")
                    response.headers[HttpHeaders.ContentRange]?.let { append("Content-Range: ").append(it).append("\r\n") }
                    response.headers[HttpHeaders.AcceptRanges]?.let { append("Accept-Ranges: ").append(it).append("\r\n") }
                    if (contentLength != null) {
                        append("Content-Length: ").append(contentLength).append("\r\n")
                    } else {
                        append("Transfer-Encoding: chunked\r\n")
                    }
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                headersWritten = true
                output.write(header.encodeToByteArray())
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                // 改写严格等长, Content-Length 不受影响
                val rewriter = if (segment.rewriteTimestamps && response.status.value in 200..299) {
                    TsTimestampRewriter(segment.timeRange.startMillis)
                } else {
                    null
                }
                val sink = TsByteSink { bytes, offset, length ->
                    if (contentLength != null) {
                        output.write(bytes, offset, length)
                    } else {
                        output.write(length.toString(16).encodeToByteArray())
                        output.write(CRLF)
                        output.write(bytes, offset, length)
                        output.write(CRLF)
                    }
                }
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    if (rewriter != null) {
                        rewriter.rewrite(buffer, 0, read, sink)
                    } else {
                        sink.write(buffer, 0, read)
                    }
                }
                rewriter?.finish(sink)
                if (contentLength == null) {
                    output.write("0\r\n\r\n".encodeToByteArray())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 连接源站失败等: 若还没开始写响应, 回一个 502, 让播放器按加载失败处理; 已经在写正文则只能断开
            if (!headersWritten) {
                logger.warn(e) { "Failed to proxy HLS segment ${segment.remoteUri}" }
                output.write(errorResponseHeader(502, "Bad Gateway").encodeToByteArray())
            } else {
                throw e
            }
        }
    }

    private suspend fun serveBytes(bytes: ByteArray, rangeHeader: String?, output: HlsProxyResponseSink) {
        val range = rangeHeader?.let { parseByteRange(it, bytes.size.toLong()) }
        if (rangeHeader != null && range == null) {
            output.write(errorResponseHeader(416, "Range Not Satisfiable").encodeToByteArray())
            return
        }
        val start = range?.first ?: 0L
        val endInclusive = range?.last ?: (bytes.size - 1L)
        val length = (endInclusive - start + 1).toInt()
        val header = buildString {
            if (range != null) {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Range: bytes ").append(start).append('-').append(endInclusive).append('/').append(bytes.size).append("\r\n")
            } else {
                append("HTTP/1.1 200 OK\r\n")
            }
            append("Content-Type: ").append(DEFAULT_SEGMENT_CONTENT_TYPE).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: ").append(length).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.encodeToByteArray())
        output.write(bytes, start.toInt(), length)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scope.cancel()
            server.close()
            segmentCache.clear()
            sessionClient.close()
        }
    }

    // ---------- 播放列表改写 ----------

    private class RemotePlaylist(val content: String, val baseUri: String)
    /**
     * @param isVod 远端是点播列表 (含 `#EXT-X-ENDLIST`), 内容不会再变.
     */
    private class LocalPlaylist(val content: String, val isVod: Boolean)

    /**
     * 把远端播放列表处理成给播放器的版本: 主播放列表把各变体指向本地路由; 媒体播放列表按需过滤广告并代理分片.
     */
    private suspend fun RemotePlaylist.toLocalPlaylist(): LocalPlaylist {
        val analysis = HlsManifestFilter.analyze(content, baseUri)
        if (analysis.isMasterPlaylist) {
            return LocalPlaylist(content.rewriteMasterPlaylistUris(baseUri), isVod = false)
        }
        val isVod = isVodMediaPlaylist(content, baseUri)
        if (!options.filterSegments) {
            return LocalPlaylist(rewriteMediaPlaylist(content, baseUri, options.proxySegments).content, isVod)
        }
        val mayContainAds = !analysis.isConclusive
        // 播放列表在过滤后才确定, 预缓存的分片按它改写时间戳, 所以要等到这里给出结果. 失败时给空列表.
        val proxiedSegments = CompletableDeferred<List<ProxiedSegment>>()
        try {
            val proxyable = if (mayContainAds) parseProxyableMediaPlaylist(content, baseUri) else null
            val startPrefetch = proxyable?.let { startPositionPrefetchOrNull(analysis, baseUri, proxiedSegments) }
            // 续播时也要开头的分片: 播放器打开时先读它们分析流信息, 续播的跳转在开始播放之后才执行
            proxyable?.segments?.take(STARTUP_PREFETCH_SEGMENTS)?.forEach { segment ->
                prefetchSegment(resolveHlsUri(baseUri, segment.uri), proxiedSegments)
            }
            val filterResult = HlsManifestFilter.filter(analysis) { targets -> probeFirstPts(targets, startPrefetch) }
            logger.info {
                "HLS filter result $baseUri is ${filterResult.status}, reason: ${filterResult.reason}, " +
                        "removed groups: ${filterResult.removedGroups}, oversized groups: ${filterResult.oversizedGroups}"
            }
            val rewritten = rewriteMediaPlaylist(filterResult.content, baseUri, options.proxySegments || mayContainAds)
            proxiedSegments.complete(rewritten.segments)
            startPrefetch?.onPlaylistReady(rewritten.segments)
            return LocalPlaylist(rewritten.content, isVod)
        } finally {
            proxiedSegments.complete(emptyList())
        }
    }

    /**
     * 在探测时间戳期间把一个分片下载进 [segmentCache]: 开头的 [STARTUP_PREFETCH_SEGMENTS] 个分片,
     * 续播时还有记忆进度处的分片 (见 [StartPositionPrefetch]).
     * 播放器拿到播放列表后先要的就是它们, 与探测重叠后起播只等两者中较慢的那个.
     *
     * 不阻塞播放列表的返回: 播放器请求这一片时若还没下完, [serveSegment] 会等这次下载而不是重下.
     * 下载失败, 或这一片不在最终的播放列表里 (被判为广告), 则缓存里没有它, 播放器的请求照常转发到源站.
     *
     * 缓存里存的是改写后的字节 (见 [restartPrefetchLocked]), 改写的锚点是分片在过滤后时间轴上的起点,
     * 取自最终的播放列表 [proxiedSegments], 所以下完后还要等它给出. 锚点不按探测中途的判定来算:
     * 之后的探测结果可能改变前面某组的判定, 锚点随之移动, 而缓存命中时字节原样发出, 没有再改写的机会.
     *
     * 不钉住这一片 ([SegmentCache.pin]): 播放器紧接着就会请求它, 在那之前能把它挤出缓存的只有超过上限的范围预缓存.
     */
    private fun prefetchSegment(remoteUri: String, proxiedSegments: Deferred<List<ProxiedSegment>>) {
        // UNDISPATCHED: 缓存项在返回前就已建立, 播放器的请求一定能找到它
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                segmentCache.getOrDownload(remoteUri) {
                    val bytes = downloadSegment(remoteUri)
                    val proxied = proxiedSegments.await().firstOrNull { it.remoteUri == remoteUri }
                        ?: throw IllegalStateException("Segment is not in the local playlist")
                    rewriteWhole(proxied, bytes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "HLS startup segment prefetch failed: $remoteUri" }
            }
        }
    }

    /**
     * 有续播位置且它落在播放列表范围内时返回 [StartPositionPrefetch], 否则返回 `null`.
     */
    private fun startPositionPrefetchOrNull(
        analysis: HlsManifestAnalysis,
        baseUri: String,
        proxiedSegments: Deferred<List<ProxiedSegment>>,
    ): StartPositionPrefetch? {
        val positionMillis = startPositionHintMillis ?: return null
        // 过滤只会缩短时间轴, 超出原播放列表总时长的位置在过滤后也不存在
        val totalMillis = analysis.probeTargets.sumOf { it.durationMillis }
        if (positionMillis <= 0 || positionMillis >= totalMillis) return null
        return StartPositionPrefetch(analysis, baseUri, positionMillis, proxiedSegments)
    }

    /**
     * 续播时在首片之外预缓存记忆进度处的分片.
     *
     * 多数播放是从上次看到的地方续播: 播放器打开时先读开头的分片分析流信息, 开始播放后才跳到记忆的进度
     * (由 RememberPlayProgressExtension 执行), 接着要的是那里的分片. 要是等跳转后才开始下载, 整段探测时间就叠加在续播耗时上.
     *
     * 记忆的进度在去除广告后的时间轴上, 对应到远端播放列表的哪个分片取决于它之前有哪些组是广告, 这要等探测给出结论.
     * 所以按组序探测 ([probeFirstPts]), 每当从头连续有结果的组增加, 就用与最终判定相同的规则
     * ([HlsManifestFilter.classify], [HlsManifestFilter.locate]) 对已有结果判定一次 (未探测的组记为 `null`, 按未知保留),
     * 从头累加保留组的时长直到超过记忆的进度, 找到所在的分片. 这个分片连同它之前的所有组都有了结果, 就开始下载它和下一片,
     * 不等其余的组. 已有结果只覆盖一部分组, 之后的结果仍可能改变前面某组的判定, 所以最终播放列表给出后再核对一次
     * ([onPlaylistReady]).
     */
    private inner class StartPositionPrefetch(
        private val analysis: HlsManifestAnalysis,
        private val baseUri: String,
        private val positionMillis: Long,
        private val proxiedSegments: Deferred<List<ProxiedSegment>>,
    ) {
        private val targets = analysis.probeTargets
        private val targetIndexesByUri: Map<String, List<Int>> = targets.indices.groupBy { targets[it].uri }

        private val lock = SynchronizedObject()
        private val firstPts = arrayOfNulls<Long>(targets.size)
        private val probed = BooleanArray(targets.size)

        /** 序号小于它的组都已有探测结果. */
        private var probedPrefix = 0

        /** 探测期间已开始预缓存的分片, 尚未开始时为 `null`. */
        private var earlyUris: List<String>? = null

        /** 探测完一个地址时调用. [pts] 为 `null` 表示探测失败, 同样算作有了结果. */
        fun onProbed(uri: String, pts: Long?) {
            val uris = synchronized(lock) {
                if (earlyUris != null) return
                for (index in targetIndexesByUri[uri].orEmpty()) {
                    firstPts[index] = pts
                    probed[index] = true
                }
                val previousPrefix = probedPrefix
                while (probedPrefix < probed.size && probed[probedPrefix]) probedPrefix++
                if (probedPrefix == previousPrefix) return
                val verdict = HlsManifestFilter.classify(analysis, firstPts.asList())
                val location = HlsManifestFilter.locate(analysis, verdict, positionMillis, SEGMENT_COUNT) ?: return
                if (location.lastGroupIndex >= probedPrefix) return
                location.segmentUris.map { resolveHlsUri(baseUri, it) }.also { earlyUris = it }
            }
            logger.info { "HLS start position ${positionMillis}ms: prefetching $uris before the probe finishes" }
            uris.forEach { prefetchSegment(it, proxiedSegments) }
        }

        /** 最终播放列表给出后, 补上探测期间没有预缓存或定位有误的分片. 不等待下载. */
        fun onPlaylistReady(segments: List<ProxiedSegment>) {
            val index = segments.indexOfFirst { positionMillis < it.timeRange.endMillis }
            if (index < 0) return
            val uris = segments.subList(index, minOf(index + SEGMENT_COUNT, segments.size)).map { it.remoteUri }
            val early = synchronized(lock) { earlyUris }.orEmpty()
            val missing = uris - early.toSet()
            if (missing.isEmpty()) return
            logger.info { "HLS start position ${positionMillis}ms: prefetching $missing after the probe (early: $early)" }
            missing.forEach { prefetchSegment(it, proxiedSegments) }
        }
    }

    /**
     * 并发取回各组首片的开头, 解出首个 PTS. 如何使用见 [HlsManifestFilter.filter].
     *
     * 探测夹在取回播放列表与播放器启动之间. 正常情况下等全部完成, 最多等 [PTS_PROBE_SAFETY_CAP], 届时未完成的组记为 `null`.
     *
     * 同一个地址只探测一次: 同一段广告常被插入多处, 各组首片是同一个文件.
     *
     * 按组序开始探测: 续播位置之前的组先有结果, [startPrefetch] 能尽早定位续播处的分片. 并发下这只是先后的倾向.
     */
    private suspend fun probeFirstPts(targets: List<HlsProbeTarget>, startPrefetch: StartPositionPrefetch?): List<Long?> {
        val start = TimeSource.Monotonic.markNow()
        val uris = targets.map { it.uri }.distinct()
        val firstPts = arrayOfNulls<Long>(uris.size)
        val finished = BooleanArray(uris.size)
        withTimeoutOrNull(PTS_PROBE_SAFETY_CAP) {
            coroutineScope {
                val limit = Semaphore(PTS_PROBE_CONCURRENCY)
                uris.forEachIndexed { index, uri ->
                    // 启动前取得许可, 探测才严格按组序开始; 在各自的协程里取的话, 排队先后取决于调度
                    limit.acquire()
                    launch {
                        try {
                            val pts = client.probeFirstPts(uri)
                            firstPts[index] = pts
                            finished[index] = true
                            startPrefetch?.onProbed(uri, pts)
                        } finally {
                            limit.release()
                        }
                    }
                }
            }
        }
        logger.info {
            "HLS pts probe: ${finished.count { it }}/${uris.size} segments of ${targets.size} groups finished, " +
                    "${firstPts.count { it != null }} with pts, in ${start.elapsedNow().inWholeMilliseconds}ms"
        }
        val ptsByUri = uris.zip(firstPts).toMap()
        return targets.map { ptsByUri[it.uri] }
    }

    private suspend fun HttpClient.probeFirstPts(uri: String): Long? {
        return try {
            prepareGet(uri) {
                expectSuccess = false
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Range, "bytes=0-${PTS_PROBE_BYTES - 1}")
            }.execute { response ->
                if (response.status.value !in 200..299) return@execute null
                // 源站不支持 Range 时会回整个分片, 读满开头就断开, 不把几 MB 的分片下完
                val channel = response.bodyAsChannel()
                val head = ByteArray(PTS_PROBE_BYTES)
                var length = 0
                while (length < head.size) {
                    val read = channel.readAvailable(head, length, head.size - length)
                    if (read < 0) break
                    length += read
                }
                TsPacketReader.firstPts(head, 0, length)?.let { TsPacketReader.ticksToMillis(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to probe first PTS of $uri" }
            null
        }
    }

    private class RewrittenMediaPlaylist(
        val content: String,
        /** 经代理的分片; 不代理时为空. */
        val segments: List<ProxiedSegment>,
    )

    /**
     * 媒体播放列表: 分片地址改到本地 ([proxySegments] 且可代理时) 或改为绝对地址.
     */
    private fun rewriteMediaPlaylist(content: String, baseUri: String, proxySegments: Boolean): RewrittenMediaPlaylist {
        val playlist = if (proxySegments) parseProxyableMediaPlaylist(content, baseUri) else null
        if (playlist == null) {
            return RewrittenMediaPlaylist(content.rewriteMediaPlaylistUris(baseUri), emptyList())
        }

        // 拼接流才需要把时间戳对齐到播放列表: 没有 discontinuity 时各分片本来就是一条时间轴, 改写只是白费解析.
        // 加密分片是密文, 同步字节探测有极小概率蒙对而把分片改坏, 一律不碰.
        val rewriteTimestamps = playlist.segments.any { it.isDiscontinuity } &&
                playlist.segments.none { it.encryption != null }

        val proxied = ArrayList<ProxiedSegment>(playlist.segments.size)
        var cursorMillis = 0L
        synchronized(routesLock) {
            for ((index, segment) in playlist.segments.withIndex()) {
                val durationMillis = segmentDurationMillis(segment.duration.toDouble())
                val remoteUri = resolveHlsUri(baseUri, segment.uri)
                // 保留原分片的扩展名: 新版 FFmpeg (mpv 的解复用器) 会拒绝扩展名不在白名单内的分片地址
                val route = "/segment/${nextRouteId++}${segmentExtension(remoteUri)}"
                val item = ProxiedSegment(
                    index = index,
                    route = route,
                    remoteUri = remoteUri,
                    timeRange = MediaTimeRange(cursorMillis, cursorMillis + durationMillis),
                    rewriteTimestamps = rewriteTimestamps,
                )
                proxied += item
                segmentRoutes[route] = item
                cursorMillis += durationMillis
            }
        }
        var segmentCursor = 0
        val rewritten = content.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        match.groupValues[1] + resolveHlsUri(baseUri, match.groupValues[2]) + match.groupValues[3]
                    }
                }

                else -> "http://127.0.0.1:${server.port}${proxied[segmentCursor++].route}"
            }
        } + if (content.endsWith('\n')) "\n" else ""

        activePlaylist = ProxiedPlaylist(proxied)
        onActivePlaylistChanged()
        return RewrittenMediaPlaylist(rewritten, proxied)
    }

    private fun String.rewriteMasterPlaylistUris(baseUri: String): String {
        return lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = resolveHlsUri(baseUri, match.groupValues[2])
                        match.groupValues[1] + localPlaylistUri(uri) + match.groupValues[3]
                    }
                }

                line.startsWith("#") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = resolveHlsUri(baseUri, match.groupValues[2])
                        match.groupValues[1] + uri + match.groupValues[3]
                    }
                }

                line.isBlank() -> line
                else -> localPlaylistUri(resolveHlsUri(baseUri, line))
            }
        } + if (endsWith('\n')) "\n" else ""
    }

    private fun localPlaylistUri(remoteUri: String): String {
        val route = synchronized(routesLock) {
            "/playlist/${nextRouteId++}.m3u8".also { playlistRoutes[it] = remoteUri }
        }
        return "http://127.0.0.1:${server.port}$route"
    }

    private class ProxiedSegment(
        val index: Int,
        val route: String,
        val remoteUri: String,
        val timeRange: MediaTimeRange,
        /** 见 [serveSegment]. */
        val rewriteTimestamps: Boolean,
    )

    private class ProxiedPlaylist(val segments: List<ProxiedSegment>)

    companion object {
        private const val DEFAULT_SEGMENT_CONTENT_TYPE = "video/mp2t"
        private val CRLF = "\r\n".encodeToByteArray()

        /**
         * 探测的安全上限, 只防源站异常 (个别请求卡住不回), 正常情况下探测全部完成.
         *
         * 探测经会话 client 已建好的 HTTP/2 连接进行. 同一条连接上一次发出全部探测实测: dytt-cinema 56 组 1.6-1.8 秒,
         * ffzy 63 组 2.8-3.6 秒, dytt-hot 68 组 2.1-2.7 秒, modu 7 个不同地址 0.3-0.4 秒, 没有长尾.
         * 首片与探测同时下载 (见 [prefetchSegment]). 截止时间短于完整探测的话, 只要首片比探测先到就会漏掉广告:
         * 慢速网络下 3 秒截止时, dytt 与 ffzy 8 次起播有 4 次漏删广告, 其中一次一段也没删. 所以探测要跑完.
         *
         * 到时仍未完成的组按探测失败处理. [HlsPtsContinuity] 不删未探测的组, 建链时也把它们的时长
         * 当作可能占正片时间轴的区间, 所以截断只会漏掉广告, 不会删掉正片.
         */
        private val PTS_PROBE_SAFETY_CAP = 8.seconds

        /** 续播时预缓存的分片数: 所在的一片和下一片. 见 [StartPositionPrefetch]. */
        private const val SEGMENT_COUNT = 2

        /**
         * @param client 返回会话时归会话所有, 由 [close] 归还; 返回 `null` 或抛出异常时仍由调用方归还.
         * @return `null` 表示这个播放列表不需要代理 (例如只开了广告过滤但没有可过滤的内容), 应直接播放原地址.
         */
        suspend fun createOrNull(
            manifest: String,
            baseUri: String,
            headers: Map<String, String>,
            client: SessionHttpClient,
            options: HlsPlaybackOptions,
            startPositionHintMillis: Long?,
            segmentCacheMaxBytes: Long,
            serverFactory: HlsProxyServerFactory,
        ): LocalHlsProxySession? {
            val analysis = HlsManifestFilter.analyze(manifest, baseUri)
            val isMaster = analysis.isMasterPlaylist
            // 要探测时间戳才知道有没有广告, 这里只判断"可能要过滤", 结果由 toLocalPlaylist 给出.
            // 可能要过滤的播放列表同时代理分片 (可代理时), 与 proxySegments 无关.
            val mayFilter = options.filterSegments && !analysis.isConclusive
            val needsProxy = isMaster || mayFilter || (options.proxySegments && manifest.isProxyableMediaPlaylist(baseUri))
            if (!needsProxy) return null

            val session = LocalHlsProxySession(
                headers,
                client,
                options,
                startPositionHintMillis,
                segmentCacheMaxBytes,
                serverFactory.create(),
            )
            try {
                session.initialContent = with(session) { RemotePlaylist(manifest, baseUri).toLocalPlaylist().content }
                session.server.start { request, sink -> session.respond(request, sink) }
            } catch (e: Throwable) {
                session.close()
                throw e
            }
            return session
        }

        private fun isVodMediaPlaylist(content: String, baseUri: String): Boolean {
            val playlist = runCatching { DefaultM3u8Parser.parse(content, baseUri) }.getOrNull()
            return playlist is M3u8Playlist.MediaPlaylist && playlist.isEndlist
        }

        private fun String.isProxyableMediaPlaylist(baseUri: String): Boolean {
            val playlist = runCatching { DefaultM3u8Parser.parse(this, baseUri) }.getOrNull()
            return playlist is M3u8Playlist.MediaPlaylist &&
                    playlist.isEndlist &&
                    playlist.segments.isNotEmpty() &&
                    playlist.segments.none { it.byteRange != null }
        }
    }
}

/**
 * 预缓存分片的内存缓存, 按 URI 索引. 正在下载的分片以 [Deferred] 形式存在, 便于播放器请求时等待其完成而不是重复下载.
 */
private class SegmentCache(private val maxBytes: Long) {
    private class Entry(val deferred: kotlinx.coroutines.CompletableDeferred<ByteArray>) {
        val bytes: ByteArray? get() = if (deferred.isCompleted && !deferred.isCancelled) deferred.getCompleted() else null
    }

    private val lock = SynchronizedObject()

    /** 按最近使用排序, 最久未使用的在最前. 见 [touchLocked]. */
    private val entries = LinkedHashMap<String, Entry>()
    private var pinned: Set<String> = emptySet()
    private var totalBytes = 0L

    fun pin(uris: List<String>) = synchronized(lock) { pinned = uris.toSet() }

    /** 取出 [uri] 并标记为最近使用. */
    private fun touchLocked(uri: String): Entry? {
        val entry = entries.remove(uri) ?: return null
        entries[uri] = entry
        return entry
    }

    fun isComplete(uri: String): Boolean = synchronized(lock) { touchLocked(uri)?.bytes != null }

    fun getCompleted(uri: String): ByteArray? = synchronized(lock) { touchLocked(uri)?.bytes }

    suspend fun awaitInFlight(uri: String): ByteArray? {
        val deferred = synchronized(lock) { touchLocked(uri)?.deferred } ?: return null
        return runCatching { deferred.await() }.getOrNull()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun getOrDownload(uri: String, download: suspend () -> ByteArray): ByteArray {
        while (true) {
            val (entry, owner) = synchronized(lock) {
                val existing = touchLocked(uri)
                if (existing != null && !existing.deferred.isCancelled) {
                    existing to false
                } else {
                    val created = Entry(kotlinx.coroutines.CompletableDeferred())
                    entries[uri] = created
                    created to true
                }
            }
            if (!owner) {
                try {
                    return entry.deferred.await()
                } catch (e: CancellationException) {
                    // 要区分 "我被取消了" 和 "正在下载它的那个任务被取消/失败了". 后者不是我的取消, 由我接手重新下载.
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    continue
                }
            }
            try {
                val bytes = download()
                synchronized(lock) {
                    totalBytes += bytes.size
                    entry.deferred.complete(bytes)
                    evictLocked()
                }
                return bytes
            } catch (e: Throwable) {
                synchronized(lock) {
                    if (entries[uri] === entry) entries.remove(uri)
                    // Deferred.cancel 要的是 kotlinx 的类型; 它和标准库的那个在公共元数据编译里不是同一个类型
                    entry.deferred.cancel(kotlinx.coroutines.CancellationException("download failed", e))
                }
                throw e
            }
        }
    }

    private fun evictLocked() {
        if (totalBytes <= maxBytes) return
        val iterator = entries.entries.iterator()
        while (totalBytes > maxBytes && iterator.hasNext()) {
            val (uri, entry) = iterator.next()
            val bytes = entry.bytes ?: continue
            if (uri in pinned) continue
            totalBytes -= bytes.size
            iterator.remove()
        }
    }

    fun clear() = synchronized(lock) {
        entries.values.forEach { it.deferred.cancel() }
        entries.clear()
        totalBytes = 0
    }
}

private val logger = me.him188.ani.utils.logging.logger<PlatformHlsPlaybackPreparer>()

/**
 * 可以代理分片的媒体播放列表: 点播, 不用 `#EXT-X-BYTERANGE`, 且分片行与解析结果一一对应. 否则返回 `null`.
 */
private fun parseProxyableMediaPlaylist(content: String, baseUri: String): M3u8Playlist.MediaPlaylist? {
    val playlist = runCatching { DefaultM3u8Parser.parse(content, baseUri) }.getOrNull()
    val eligible = playlist is M3u8Playlist.MediaPlaylist &&
            playlist.isEndlist &&
            playlist.segments.isNotEmpty() &&
            playlist.segments.none { it.byteRange != null }
    if (!eligible) return null
    val segmentLineCount = content.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
    if (segmentLineCount != playlist.segments.size) {
        logger.warn { "HLS segment line count $segmentLineCount != parsed ${playlist.segments.size}; not proxying segments for $baseUri" }
        return null
    }
    return playlist
}

private fun String.isCandidateHlsUri(): Boolean {
    val scheme = substringBefore("://", missingDelimiterValue = "").lowercase()
    return (scheme == "http" || scheme == "https") && lowercase().contains(".m3u8")
}

private fun String.rewriteMediaPlaylistUris(baseUri: String): String {
    return lineSequence().joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.startsWith("#") -> {
                line.replace(URI_ATTRIBUTE_REGEX) { match ->
                    val uri = match.groupValues[2]
                    match.groupValues[1] + resolveHlsUri(baseUri, uri) + match.groupValues[3]
                }
            }

            else -> resolveHlsUri(baseUri, line)
        }
    } + if (endsWith('\n')) "\n" else ""
}

/**
 * 分片地址的扩展名 (含点), 取不到合理的扩展名时用 `.ts`.
 */
private fun segmentExtension(remoteUri: String): String {
    val fileName = remoteUri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) ".$extension" else ".ts"
}

/**
 * 解析 `bytes=start-end` 形式的 Range 头 (只支持单个范围). 无法满足时返回 `null`.
 */
private fun parseByteRange(header: String, totalLength: Long): LongRange? {
    val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() } ?: return null
    if (',' in spec) return null
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val startText = spec.substring(0, dash).trim()
    val endText = spec.substring(dash + 1).trim()
    val start: Long
    val end: Long
    if (startText.isEmpty()) {
        val suffix = endText.toLongOrNull() ?: return null
        if (suffix <= 0) return null
        start = (totalLength - suffix).coerceAtLeast(0)
        end = totalLength - 1
    } else {
        start = startText.toLongOrNull() ?: return null
        end = if (endText.isEmpty()) totalLength - 1 else (endText.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1)
    }
    if (start < 0 || start >= totalLength || end < start) return null
    return start..end
}

private fun playlistResponseHeader(contentLength: Int): String {
    return buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: application/vnd.apple.mpegurl; charset=utf-8\r\n")
        append("Content-Length: ").append(contentLength).append("\r\n")
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
}

private fun errorResponseHeader(code: Int, reason: String): String {
    return buildString {
        append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        append("Content-Length: 0\r\n")
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
}

private val URI_ATTRIBUTE_REGEX = Regex("""(URI=")([^"]+)(")""")

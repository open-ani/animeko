/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.UriMediaData
import kotlin.concurrent.Volatile
import kotlin.math.roundToLong

/**
 * 在本机 127.0.0.1 上起一个极简 HTTP 服务代理 HLS 播放:
 *
 * - [HlsPlaybackOptions.filterSegments]: 改写播放列表, 移除疑似广告分片 ([HlsManifestFilter]).
 * - [HlsPlaybackOptions.proxySegments]: 把媒体分片的地址也改写到本地, 由本地转发. 这样才能在播放器之外
 *   提前下载指定时间范围的分片 ([HlsPlaybackProxySession.setPrefetchRange]), 供自动跳过 OP/ED 后立即续播.
 *   只对点播 (含 `#EXT-X-ENDLIST`) 且不使用 `#EXT-X-BYTERANGE` 的播放列表启用.
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

    override suspend fun prepare(data: UriMediaData, options: HlsPlaybackOptions): HlsPlaybackPreparerResult {
        if (!options.isEnabled) {
            return HlsPlaybackPreparerResult(data)
        }
        if (!data.uri.isCandidateHlsUri()) {
            return HlsPlaybackPreparerResult(data)
        }
        var baseUri = data.uri
        val manifest = try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }
                baseUri = response.call.request.url.toString()
                response.bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return HlsPlaybackPreparerResult(data)
        }

        val session = try {
            LocalHlsProxySession.createOrNull(
                manifest = manifest,
                baseUri = baseUri,
                headers = data.headers,
                httpClientProvider = httpClientProvider,
                options = options,
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
    }

    companion object {
        const val DEFAULT_SEGMENT_CACHE_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}

/**
 * 一次播放对应一个代理会话. 关闭后本地端口释放, 所有预缓存任务取消.
 */
private class LocalHlsProxySession private constructor(
    private val headers: Map<String, String>,
    private val httpClientProvider: HttpClientProvider,
    private val options: HlsPlaybackOptions,
    segmentCacheMaxBytes: Long,
    private val server: HlsProxyServer,
) : HlsPlaybackProxySession {
    private val closed = atomic(false)
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
                    segmentCache.getOrDownload(segment.remoteUri) { downloadSegment(segment.remoteUri) }
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

    private suspend fun downloadSegment(remoteUri: String): ByteArray {
        return httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
            val response = get(remoteUri) {
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
            }
            if (response.status.value !in 200..299) {
                throw IOException("Remote returned ${response.status} for $remoteUri")
            }
            response.bodyAsBytes()
        }
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
        return fetchRemotePlaylist(remoteUri).toLocalPlaylist().content
    }

    private suspend fun fetchRemotePlaylist(uri: String): RemotePlaylist {
        return httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
            val response = get(uri) {
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
            }
            RemotePlaylist(response.bodyAsText(), response.call.request.url.toString())
        }
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
        var headersWritten = false
        try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                prepareGet(segment.remoteUri) {
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
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        if (contentLength != null) {
                            output.write(buffer, 0, read)
                        } else {
                            output.write(read.toString(16).encodeToByteArray())
                            output.write(CRLF)
                            output.write(buffer, 0, read)
                            output.write(CRLF)
                        }
                    }
                    if (contentLength == null) {
                        output.write("0\r\n\r\n".encodeToByteArray())
                    }
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
        }
    }

    // ---------- 播放列表改写 ----------

    private class RemotePlaylist(val content: String, val baseUri: String)
    private class LocalPlaylist(val content: String)

    /**
     * 把远端播放列表处理成给播放器的版本: 主播放列表把各变体指向本地路由; 媒体播放列表按需过滤广告并代理分片.
     */
    private fun RemotePlaylist.toLocalPlaylist(): LocalPlaylist {
        val filterResult = HlsManifestFilter.filter(content, baseUri)
        if (options.filterSegments) {
            logger.info { "HLS filter result $baseUri is ${filterResult.status}, reason: ${filterResult.reason}, removed groups: ${filterResult.removedGroups}" }
        }
        val isMaster = filterResult.status == HlsManifestFilterStatus.Unsupported && filterResult.reason == "master_playlist"
        if (isMaster) {
            return LocalPlaylist(content.rewriteMasterPlaylistUris(baseUri))
        }
        val mediaContent = if (options.filterSegments && filterResult.status == HlsManifestFilterStatus.Filtered) {
            filterResult.content
        } else {
            content
        }
        return LocalPlaylist(rewriteMediaPlaylist(mediaContent, baseUri))
    }

    /**
     * 媒体播放列表: 分片地址改到本地 (可代理时) 或改为绝对地址.
     */
    private fun rewriteMediaPlaylist(content: String, baseUri: String): String {
        if (!options.proxySegments) {
            return content.rewriteMediaPlaylistUris(baseUri)
        }
        val playlist = runCatching { DefaultM3u8Parser.parse(content, baseUri) }.getOrNull()
        val eligible = playlist is M3u8Playlist.MediaPlaylist &&
                playlist.isEndlist &&
                playlist.segments.isNotEmpty() &&
                playlist.segments.none { it.byteRange != null }
        if (!eligible) {
            return content.rewriteMediaPlaylistUris(baseUri)
        }
        playlist as M3u8Playlist.MediaPlaylist
        val segmentLineCount = content.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
        if (segmentLineCount != playlist.segments.size) {
            logger.warn { "HLS segment line count $segmentLineCount != parsed ${playlist.segments.size}; not proxying segments for $baseUri" }
            return content.rewriteMediaPlaylistUris(baseUri)
        }

        val proxied = ArrayList<ProxiedSegment>(playlist.segments.size)
        var cursorMillis = 0L
        synchronized(routesLock) {
            for ((index, segment) in playlist.segments.withIndex()) {
                val durationMillis = (segment.duration.toDouble() * 1000).roundToLong().coerceAtLeast(0L)
                val remoteUri = resolveHlsUri(baseUri, segment.uri)
                // 保留原分片的扩展名: 新版 FFmpeg (mpv 的解复用器) 会拒绝扩展名不在白名单内的分片地址
                val route = "/segment/${nextRouteId++}${segmentExtension(remoteUri)}"
                val item = ProxiedSegment(
                    index = index,
                    route = route,
                    remoteUri = remoteUri,
                    timeRange = MediaTimeRange(cursorMillis, cursorMillis + durationMillis),
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
        return rewritten
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
    )

    private class ProxiedPlaylist(val segments: List<ProxiedSegment>)

    companion object {
        private const val DEFAULT_SEGMENT_CONTENT_TYPE = "video/mp2t"
        private val CRLF = "\r\n".encodeToByteArray()

        /**
         * @return `null` 表示这个播放列表不需要代理 (例如只开了广告过滤但没有可过滤的内容), 应直接播放原地址.
         */
        suspend fun createOrNull(
            manifest: String,
            baseUri: String,
            headers: Map<String, String>,
            httpClientProvider: HttpClientProvider,
            options: HlsPlaybackOptions,
            segmentCacheMaxBytes: Long,
            serverFactory: HlsProxyServerFactory,
        ): LocalHlsProxySession? {
            val filterResult = HlsManifestFilter.filter(manifest, baseUri)
            logger.info { "HLS prepare filter result $baseUri is ${filterResult.status}, reason: ${filterResult.reason}, removed groups: ${filterResult.removedGroups}" }
            val isMaster = filterResult.status == HlsManifestFilterStatus.Unsupported && filterResult.reason == "master_playlist"
            val isFiltered = options.filterSegments && filterResult.status == HlsManifestFilterStatus.Filtered
            val needsProxy = isMaster || isFiltered || (options.proxySegments && manifest.isProxyableMediaPlaylist(baseUri))
            if (!needsProxy) return null

            val session = LocalHlsProxySession(headers, httpClientProvider, options, segmentCacheMaxBytes, serverFactory.create())
            try {
                session.initialContent = with(session) { RemotePlaylist(manifest, baseUri).toLocalPlaylist().content }
                session.server.start { request, sink -> session.respond(request, sink) }
            } catch (e: Throwable) {
                session.close()
                throw e
            }
            return session
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
                    entry.deferred.cancel(CancellationException("download failed", e))
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

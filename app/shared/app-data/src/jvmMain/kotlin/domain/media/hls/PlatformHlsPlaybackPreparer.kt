/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import androidx.collection.floatListOf
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.source.UriMediaData
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

class PlatformHlsPlaybackPreparer(
    private val httpClientProvider: HttpClientProvider,
) : HlsPlaybackPreparer {
    suspend fun prepare(data: UriMediaData): HlsPlaybackPreparerResult {
        return prepare(data, HlsPlaybackPrepareOptions(enableSegmentFiltering = true, enablePausePrefetch = false))
    }

    override suspend fun prepare(data: UriMediaData, options: HlsPlaybackPrepareOptions): HlsPlaybackPreparerResult {
        if (!data.uri.isCandidateHlsUri()) {
            if (options.enablePausePrefetch) {
                return prepareHttpRangePlayback(data)
            }
            return HlsPlaybackPreparerResult(data)
        }

        val requestedUri = runCatching { URI(data.uri) }.getOrNull() ?: return HlsPlaybackPreparerResult(data)
        var baseUri = requestedUri
        val manifest = try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }
                baseUri = runCatching { URI(response.call.request.url.toString()) }.getOrDefault(requestedUri)
                response.bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return HlsPlaybackPreparerResult(data)
        }

        val inspectedManifest = try {
            HlsManifestFilter.filter(manifest, baseUri.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to inspect HLS manifest; falling back to original media data" }
            return HlsPlaybackPreparerResult(data)
        }

        val isMaster = inspectedManifest.status == HlsManifestFilterStatus.Unsupported &&
            inspectedManifest.reason == "master_playlist"
        // Paused prefetch must not cache playlist groups the existing filter has identified as ads.
        val shouldFilterSegments = options.enableSegmentFiltering || options.enablePausePrefetch
        val playlistContent = if (shouldFilterSegments && inspectedManifest.status == HlsManifestFilterStatus.Filtered) {
            inspectedManifest.content
        } else {
            manifest
        }

        if (!options.enableSegmentFiltering && !options.enablePausePrefetch) {
            return HlsPlaybackPreparerResult(data)
        }

        val session = try {
            if (isMaster) {
                LocalHlsPlaylistSession.master(
                    content = playlistContent,
                    baseUri = baseUri,
                    headers = data.headers,
                    httpClientProvider = httpClientProvider,
                    filterSegments = shouldFilterSegments,
                    cacheSegments = options.enablePausePrefetch,
                )
            } else {
                LocalHlsPlaylistSession.media(
                    content = playlistContent,
                    baseUri = baseUri,
                    headers = data.headers,
                    httpClientProvider = httpClientProvider,
                    cacheSegments = options.enablePausePrefetch,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare HLS playback proxy; falling back to original media data" }
            return HlsPlaybackPreparerResult(data)
        }

        return HlsPlaybackPreparerResult(
            data = UriMediaData(session.playlistUri, data.headers, data.extraFiles),
            session = session,
        )
    }

    private suspend fun prepareHttpRangePlayback(data: UriMediaData): HlsPlaybackPreparerResult {
        val requestedUri = runCatching { URI(data.uri) }.getOrNull() ?: return HlsPlaybackPreparerResult(data)
        if (!requestedUri.isHttpUri()) return HlsPlaybackPreparerResult(data)

        val metadata = try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=0-0")
                }
                val totalSize = response.headers["Content-Range"]
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                if (response.status.value != 206 || totalSize == null || totalSize <= 0L) {
                    null
                } else {
                    DirectHttpMetadata(
                        finalUri = runCatching { URI(response.call.request.url.toString()) }.getOrDefault(requestedUri),
                        totalSize = totalSize,
                        contentType = response.headers["Content-Type"] ?: "application/octet-stream",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return HlsPlaybackPreparerResult(data)

        val session = try {
            LocalHttpRangeSession(
                remoteUri = metadata.finalUri,
                headers = data.headers,
                totalSize = metadata.totalSize,
                contentType = metadata.contentType,
                httpClientProvider = httpClientProvider,
            )
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare HTTP range playback proxy; falling back to original media data" }
            return HlsPlaybackPreparerResult(data)
        }
        logger.info { "Prepared HTTP range proxy for paused prefetch: ${metadata.finalUri}" }
        return HlsPlaybackPreparerResult(
            data = UriMediaData(session.mediaUri, data.headers, data.extraFiles),
            session = session,
        )
    }
}

private data class DirectHttpMetadata(
    val finalUri: URI,
    val totalSize: Long,
    val contentType: String,
)

private class LocalHttpRangeSession(
    private val remoteUri: URI,
    private val headers: Map<String, String>,
    private val totalSize: Long,
    private val contentType: String,
    private val httpClientProvider: HttpClientProvider,
) : HlsPlaybackProxySession {
    private val closed = AtomicBoolean(false)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val cacheDir = Files.createTempDirectory("animeko-http-range-")
    private val chunkCount = ((totalSize + HTTP_CACHE_CHUNK_SIZE - 1) / HTTP_CACHE_CHUNK_SIZE).toInt()
    private val chunkStates = Array(chunkCount) { ChunkState.NONE }
    private val cacheLock = Any()
    private val highestRequestedByte = AtomicLong(-1L)
    private val prefetchStarted = AtomicBoolean(false)
    private val _cacheProgressInfoFlow = MutableStateFlow(createProgressInfo())

    override val cacheProgressInfoFlow: StateFlow<MediaCacheProgressInfo> = _cacheProgressInfoFlow.asStateFlow()
    val mediaUri: String = "http://127.0.0.1:${serverSocket.localPort}/media"

    private val thread = thread(
        name = "HttpRangeProxy-${serverSocket.localPort}",
        isDaemon = true,
        start = true,
    ) {
        while (!closed.get()) {
            try {
                val socket = serverSocket.accept()
                thread(name = "HttpRangeProxyRequest-${serverSocket.localPort}", isDaemon = true) {
                    socket.use(::handleRequest)
                }
            } catch (e: SocketException) {
                if (!closed.get()) logger.warn(e) { "Failed to serve HTTP range request" }
            } catch (e: IOException) {
                if (!closed.get()) logger.warn(e) { "Failed to serve HTTP range request" }
            }
        }
    }

    override fun onPlaybackStateChanged(state: PlaybackState) {
        if (state != PlaybackState.PAUSED || !prefetchStarted.compareAndSet(false, true)) return
        thread(name = "HttpPausePrefetch-${serverSocket.localPort}", isDaemon = true) {
            val firstChunk = ((highestRequestedByte.get() + 1).coerceAtLeast(0L) / HTTP_CACHE_CHUNK_SIZE).toInt()
            logger.info { "Started paused HTTP range prefetch from chunk $firstChunk of $chunkCount" }
            for (chunkIndex in firstChunk until chunkCount) {
                if (closed.get()) return@thread
                fetchChunk(chunkIndex)
            }
            logger.info { "Finished paused HTTP range prefetch" }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            serverSocket.close()
            deleteDirectory(cacheDir)
        }
    }

    @Suppress("unused")
    private fun keepThreadReachable(): Thread = thread

    private fun handleRequest(socket: java.net.Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
        val requestLine = reader.readLine() ?: return
        val requestHeaders = buildMap {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) put(line.substring(0, separator).trim().lowercase(), line.substring(separator + 1).trim())
            }
        }
        val path = requestLine.substringAfter(' ', missingDelimiterValue = "").substringBefore(' ')
        val output = socket.getOutputStream()
        if (path != "/media") {
            output.write(errorResponseHeader(404).toByteArray(StandardCharsets.US_ASCII))
            return
        }

        val range = requestHeaders["range"]?.toByteRange(totalSize)
        if (range == null) {
            output.write(errorResponseHeader(416).toByteArray(StandardCharsets.US_ASCII))
            return
        }
        highestRequestedByte.accumulateAndGet(range.last) { current, requested -> maxOf(current, requested) }

        if (isRangeCached(range)) {
            writeRangeHeader(output, range, range.length)
            writeCachedRange(output, range)
            return
        }

        val bytes = fetchRemoteRange(range) ?: run {
            output.write(errorResponseHeader(502).toByteArray(StandardCharsets.US_ASCII))
            return
        }
        writeRangeHeader(output, range, bytes.size.toLong())
        output.write(bytes)
    }

    private fun fetchChunk(chunkIndex: Int) {
        val range = chunkRange(chunkIndex)
        synchronized(cacheLock) {
            if (chunkStates[chunkIndex] == ChunkState.DONE) return
            chunkStates[chunkIndex] = ChunkState.DOWNLOADING
            publishProgressInfo()
        }
        val bytes = fetchRemoteRange(range)
        if (bytes == null || bytes.size.toLong() != range.length || closed.get()) {
            synchronized(cacheLock) {
                chunkStates[chunkIndex] = ChunkState.NONE
                publishProgressInfo()
            }
            return
        }

        val temporaryPath = chunkPath(chunkIndex).resolveSibling("$chunkIndex.part")
        val cached = runCatching {
            Files.write(temporaryPath, bytes)
            Files.move(temporaryPath, chunkPath(chunkIndex), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { e ->
            logger.warn(e) { "Failed to persist HTTP range cache chunk $chunkIndex" }
        }.isSuccess
        synchronized(cacheLock) {
            chunkStates[chunkIndex] = if (cached) ChunkState.DONE else ChunkState.NONE
            publishProgressInfo()
        }
    }

    private fun fetchRemoteRange(range: ByteRange): ByteArray? = runBlocking {
        runCatching {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(remoteUri.toString()) {
                    this@LocalHttpRangeSession.headers.forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=${range.first}-${range.last}")
                }
                if (response.status.value != 206) null else response.body<ByteArray>()
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to fetch HTTP range ${range.first}-${range.last}" }
            null
        }
    }

    private fun isRangeCached(range: ByteRange): Boolean = synchronized(cacheLock) {
        (chunkIndex(range.first)..chunkIndex(range.last)).all { index ->
            chunkStates[index] == ChunkState.DONE && Files.exists(chunkPath(index))
        }
    }

    private fun writeCachedRange(output: java.io.OutputStream, range: ByteRange) {
        for (chunkIndex in chunkIndex(range.first)..chunkIndex(range.last)) {
            val chunk = chunkRange(chunkIndex)
            val startOffset = maxOf(range.first, chunk.first) - chunk.first
            val endOffset = minOf(range.last, chunk.last) - chunk.first
            Files.newInputStream(chunkPath(chunkIndex)).use { input ->
                input.skipNBytes(startOffset)
                var remaining = endOffset - startOffset + 1
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun writeRangeHeader(output: java.io.OutputStream, range: ByteRange, contentLength: Long) {
        output.write(
            buildString {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(contentLength).append("\r\n")
                append("Content-Range: bytes ").append(range.first).append('-').append(range.last)
                    .append('/').append(totalSize).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    private fun errorResponseHeader(status: Int): String = buildString {
        append("HTTP/1.1 ").append(status).append(" Error\r\n")
        append("Content-Length: 0\r\n")
        append("Connection: close\r\n\r\n")
    }

    private fun publishProgressInfo() {
        _cacheProgressInfoFlow.value = createProgressInfo()
    }

    private fun createProgressInfo(): MediaCacheProgressInfo = MediaCacheProgressInfo(
        chunkWeights = floatListOf(
            *(Array(chunkCount) { index -> (chunkRange(index).length.toDouble() / totalSize).toFloat() }.toFloatArray()),
        ),
        chunkStates = chunkStates.toList(),
    )

    private fun chunkIndex(position: Long): Int = (position / HTTP_CACHE_CHUNK_SIZE).toInt()

    private fun chunkRange(index: Int): ByteRange {
        val start = index.toLong() * HTTP_CACHE_CHUNK_SIZE
        return ByteRange(start, minOf(start + HTTP_CACHE_CHUNK_SIZE - 1, totalSize - 1))
    }

    private fun chunkPath(index: Int): Path = cacheDir.resolve("$index.bin")
}

private data class ByteRange(val first: Long, val last: Long) {
    val length: Long get() = last - first + 1
}

private fun String.toByteRange(totalSize: Long): ByteRange? {
    val value = removePrefix("bytes=").substringBefore(',').trim()
    val separator = value.indexOf('-')
    if (separator < 0) return null
    val first = value.substring(0, separator).toLongOrNull() ?: return null
    val requestedLast = value.substring(separator + 1).toLongOrNull() ?: totalSize - 1
    if (first !in 0 until totalSize || requestedLast < first) return null
    return ByteRange(first, minOf(requestedLast, totalSize - 1))
}

private fun URI.isHttpUri(): Boolean = scheme?.lowercase() in setOf("http", "https")

private const val HTTP_CACHE_CHUNK_SIZE = 2L * 1024 * 1024

private class LocalHlsPlaylistSession(
    initialPlaylistContent: LocalPlaylistContent,
    private val initialPlaylistIsMaster: Boolean,
    private val headers: Map<String, String>,
    private val httpClientProvider: HttpClientProvider,
    private val filterSegments: Boolean,
    private val cacheSegments: Boolean,
) : HlsPlaybackProxySession {
    private val closed = AtomicBoolean(false)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val nextRouteId = AtomicInteger(1)
    private val remotePlaylistRoutes = ConcurrentHashMap<String, URI>()
    private val assetRoutes = ConcurrentHashMap<String, CachedAsset>()
    private val prefetchRoutes = CopyOnWriteArrayList<String>()
    private val prefetchRequested = AtomicBoolean(false)
    private val prefetchStarted = AtomicBoolean(false)
    private val isVod = AtomicBoolean(false)
    private val progressPlaylistRegistered = AtomicBoolean(false)
    private val cacheLock = Any()
    private val progressAssets = CopyOnWriteArrayList<CachedAsset>()
    private val _cacheProgressInfoFlow = MutableStateFlow(MediaCacheProgressInfo.Empty)
    private val cacheDir: Path? = if (cacheSegments) Files.createTempDirectory("animeko-hls-") else null
    private val initialContent = rewritePlaylist(initialPlaylistContent, initialPlaylistIsMaster)

    override val cacheProgressInfoFlow: StateFlow<MediaCacheProgressInfo> = _cacheProgressInfoFlow.asStateFlow()

    val playlistUri: String = "http://127.0.0.1:${serverSocket.localPort}/playlist.m3u8"

    private val thread = thread(
        name = "HlsPlaylistProxy-${serverSocket.localPort}",
        isDaemon = true,
        start = true,
    ) {
        while (!closed.get()) {
            try {
                serverSocket.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    val requestLine = reader.readLine()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val path = requestLine
                        ?.substringAfter(" ", missingDelimiterValue = "")
                        ?.substringBefore(" ", missingDelimiterValue = "")
                        ?.substringBefore("?")

                    socket.getOutputStream().use { output ->
                        when (val response = responseFor(path ?: "/playlist.m3u8")) {
                            is LocalResponse.Text -> {
                                val bytes = response.content.toByteArray(StandardCharsets.UTF_8)
                                output.write(responseHeader("application/vnd.apple.mpegurl; charset=utf-8", bytes.size).toByteArray(StandardCharsets.US_ASCII))
                                output.write(bytes)
                            }

                            is LocalResponse.Binary -> {
                                output.write(responseHeader("application/octet-stream", response.content.size).toByteArray(StandardCharsets.US_ASCII))
                                output.write(response.content)
                            }

                            null -> output.write(errorResponseHeader().toByteArray(StandardCharsets.US_ASCII))
                        }
                        output.flush()
                    }
                }
            } catch (e: SocketException) {
                if (!closed.get()) {
                    logger.warn(e) { "Failed to serve HLS playlist request" }
                }
            } catch (e: IOException) {
                if (!closed.get()) {
                    logger.warn(e) { "Failed to serve HLS playlist request" }
                }
            }
        }
    }

    override fun onPlaybackStateChanged(state: PlaybackState) {
        if (state != PlaybackState.PAUSED || !cacheSegments) return
        prefetchRequested.set(true)
        startPrefetchIfReady()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            serverSocket.close()
            synchronized(cacheLock) {
                cacheDir?.let(::deleteDirectory)
            }
        }
    }

    @Suppress("unused")
    private fun keepThreadReachable(): Thread = thread

    private fun responseFor(path: String): LocalResponse? {
        if (path == "/playlist.m3u8") {
            return LocalResponse.Text(initialContent.content)
        }
        remotePlaylistRoutes[path]?.let { remoteUri ->
            val content = fetchRemotePlaylist(remoteUri) ?: return null
            return LocalResponse.Text(content)
        }
        assetRoutes[path]?.let { asset ->
            return fetchAsset(asset)?.let(LocalResponse::Binary)
        }
        return null
    }

    private fun fetchRemotePlaylist(uri: URI): String? {
        val content = runBlocking {
            runCatching {
                httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                    val response = get(uri.toString()) {
                        this@LocalHlsPlaylistSession.headers.forEach { (name, value) -> header(name, value) }
                    }
                    val finalUri = runCatching { URI(response.call.request.url.toString()) }.getOrDefault(uri)
                    LocalPlaylistContent(response.bodyAsText(), finalUri)
                }
            }.getOrElse { e ->
                logger.warn(e) { "Failed to fetch HLS playlist $uri" }
                null
            }
        } ?: return null

        val filterResult = if (filterSegments) {
            runCatching { HlsManifestFilter.filter(content.content, content.baseUri.toString()) }
                .getOrElse { e ->
                    logger.warn(e) { "Failed to filter HLS playlist $uri" }
                    null
                }
        } else {
            null
        }
        val isMaster = filterResult?.status == HlsManifestFilterStatus.Unsupported &&
            filterResult.reason == "master_playlist"
        val rewritten = content.copy(
            content = if (filterResult?.status == HlsManifestFilterStatus.Filtered) filterResult.content else content.content,
        )
        return rewritePlaylist(rewritten, isMaster).content
    }

    private fun rewritePlaylist(content: LocalPlaylistContent, isMaster: Boolean): LocalPlaylistContent {
        return if (isMaster) {
            content.copy(content = content.content.rewriteMasterPlaylistUris(content.baseUri))
        } else {
            if (content.content.contains("#EXT-X-ENDLIST")) {
                isVod.set(true)
                startPrefetchIfReady()
            }
            content.copy(content = content.content.rewriteMediaPlaylistUris(content.baseUri))
        }
    }

    private fun String.rewriteMasterPlaylistUris(baseUri: URI): String {
        return lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = baseUri.resolveIfRelative(match.groupValues[2])
                        match.groupValues[1] + localPlaylistUri(uri) + match.groupValues[3]
                    }
                }

                line.startsWith("#") -> line.replace(URI_ATTRIBUTE_REGEX) { match ->
                    val uri = baseUri.resolveIfRelative(match.groupValues[2])
                    match.groupValues[1] + uri + match.groupValues[3]
                }

                line.isBlank() -> line
                else -> localPlaylistUri(baseUri.resolveIfRelative(line))
            }
        } + if (endsWith('\n')) "\n" else ""
    }

    private fun String.rewriteMediaPlaylistUris(baseUri: URI): String {
        val includeInProgress = progressPlaylistRegistered.compareAndSet(false, true)
        var nextSegmentDurationSeconds: Double? = null
        return lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#EXTINF:") -> {
                    nextSegmentDurationSeconds = line.substringAfter(":").substringBefore(",").toDoubleOrNull()
                    line
                }

                line.startsWith("#") -> line.replace(URI_ATTRIBUTE_REGEX) { match ->
                    val uri = baseUri.resolveIfRelative(match.groupValues[2])
                    val rewrittenUri = if (line.isMediaAssetTag()) localAssetUri(uri) else uri
                    match.groupValues[1] + rewrittenUri + match.groupValues[3]
                }

                else -> {
                    val durationSeconds = nextSegmentDurationSeconds
                    nextSegmentDurationSeconds = null
                    localAssetUri(baseUri.resolveIfRelative(line), durationSeconds, includeInProgress)
                }
            }
        } + if (endsWith('\n')) "\n" else ""
    }

    private fun localPlaylistUri(remoteUri: String): String {
        val route = "/playlist/${nextRouteId.getAndIncrement()}.m3u8"
        remotePlaylistRoutes[route] = URI(remoteUri)
        return "http://127.0.0.1:${serverSocket.localPort}$route"
    }

    private fun localAssetUri(
        remoteUri: String,
        durationSeconds: Double? = null,
        includeInProgress: Boolean = false,
    ): String {
        if (!cacheSegments) return remoteUri
        val route = "/asset/${nextRouteId.getAndIncrement()}"
        val cachePath = cacheDir!!.resolve("${route.substringAfterLast('/')}.bin")
        val asset = CachedAsset(URI(remoteUri), cachePath, durationSeconds)
        assetRoutes[route] = asset
        prefetchRoutes += route
        if (includeInProgress && durationSeconds != null) {
            progressAssets += asset
            updateCacheProgressInfo()
        }
        return "http://127.0.0.1:${serverSocket.localPort}$route"
    }

    private fun startPrefetchIfReady() {
        if (!prefetchRequested.get() || !isVod.get() || !prefetchStarted.compareAndSet(false, true)) return
        thread(name = "HlsPausePrefetch-${serverSocket.localPort}", isDaemon = true) {
            logger.info { "Started paused HLS VOD prefetch for ${prefetchRoutes.size} assets" }
            for (route in prefetchRoutes) {
                if (closed.get()) return@thread
                val asset = assetRoutes[route] ?: continue
                fetchAsset(asset)
            }
            logger.info { "Finished paused HLS VOD prefetch" }
        }
    }

    private fun fetchAsset(asset: CachedAsset): ByteArray? = synchronized(asset.lock) {
        if (closed.get()) return@synchronized null
        if (Files.exists(asset.cachePath)) {
            updateAssetState(asset, ChunkState.DONE)
            return@synchronized runCatching { Files.readAllBytes(asset.cachePath) }.getOrNull()
        }

        updateAssetState(asset, ChunkState.DOWNLOADING)
        val bytes = runBlocking {
            runCatching {
                httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                    get(asset.remoteUri.toString()) {
                        this@LocalHlsPlaylistSession.headers.forEach { (name, value) -> header(name, value) }
                    }.body<ByteArray>()
                }
            }.getOrElse { e ->
                logger.warn(e) { "Failed to fetch HLS asset ${asset.remoteUri}" }
                null
            }
        } ?: run {
            updateAssetState(asset, ChunkState.NONE)
            return@synchronized null
        }

        var cached = false
        if (!closed.get()) {
            synchronized(cacheLock) {
                if (!closed.get()) {
                    val temporaryPath = asset.cachePath.resolveSibling(asset.cachePath.fileName.toString() + ".part")
                    runCatching {
                        Files.write(temporaryPath, bytes)
                        Files.move(temporaryPath, asset.cachePath, StandardCopyOption.REPLACE_EXISTING)
                        cached = true
                    }.onFailure { e ->
                        logger.warn(e) { "Failed to persist temporary HLS asset ${asset.remoteUri}" }
                    }
                }
            }
        }
        updateAssetState(asset, if (cached) ChunkState.DONE else ChunkState.NONE)
        return@synchronized bytes
    }

    private fun updateAssetState(asset: CachedAsset, state: ChunkState) {
        if (asset.state == state) return
        asset.state = state
        if (progressAssets.contains(asset)) {
            updateCacheProgressInfo()
        }
    }

    private fun updateCacheProgressInfo() {
        val assets = progressAssets.toList()
        if (assets.isEmpty()) {
            _cacheProgressInfoFlow.value = MediaCacheProgressInfo.Empty
            return
        }

        val totalDuration = assets.sumOf { (it.durationSeconds ?: 0.0).coerceAtLeast(0.0) }
        val weights = if (totalDuration > 0.0) {
            assets.map { ((it.durationSeconds ?: 0.0).coerceAtLeast(0.0) / totalDuration).toFloat() }
        } else {
            List(assets.size) { 1f / assets.size }
        }
        _cacheProgressInfoFlow.value = MediaCacheProgressInfo(
            chunkWeights = floatListOf(*weights.toFloatArray()),
            chunkStates = assets.map { it.state },
        )
    }

    private fun responseHeader(contentType: String, contentLength: Int): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(contentLength).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    private fun errorResponseHeader(): String {
        return buildString {
            append("HTTP/1.1 502 Bad Gateway\r\n")
            append("Content-Length: 0\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    companion object {
        fun media(
            content: String,
            baseUri: URI,
            headers: Map<String, String>,
            httpClientProvider: HttpClientProvider,
            cacheSegments: Boolean,
        ): LocalHlsPlaylistSession {
            return LocalHlsPlaylistSession(
                initialPlaylistContent = LocalPlaylistContent(content, baseUri),
                initialPlaylistIsMaster = false,
                headers = headers,
                httpClientProvider = httpClientProvider,
                filterSegments = false,
                cacheSegments = cacheSegments,
            )
        }

        fun master(
            content: String,
            baseUri: URI,
            headers: Map<String, String>,
            httpClientProvider: HttpClientProvider,
            filterSegments: Boolean,
            cacheSegments: Boolean,
        ): LocalHlsPlaylistSession {
            return LocalHlsPlaylistSession(
                initialPlaylistContent = LocalPlaylistContent(content, baseUri),
                initialPlaylistIsMaster = true,
                headers = headers,
                httpClientProvider = httpClientProvider,
                filterSegments = filterSegments,
                cacheSegments = cacheSegments,
            )
        }
    }
}

private sealed interface LocalResponse {
    data class Text(val content: String) : LocalResponse
    data class Binary(val content: ByteArray) : LocalResponse
}

private class CachedAsset(
    val remoteUri: URI,
    val cachePath: Path,
    val durationSeconds: Double?,
) {
    val lock = Any()

    @Volatile
    var state: ChunkState = ChunkState.NONE
}

private fun deleteDirectory(path: Path) {
    runCatching {
        Files.walk(path).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }.onFailure { e ->
        logger.warn(e) { "Failed to remove temporary HLS cache $path" }
    }
}

private data class LocalPlaylistContent(
    val content: String,
    val baseUri: URI,
)

private fun String.isCandidateHlsUri(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && lowercase().contains(".m3u8")
}

private fun URI.resolveIfRelative(uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    return if (parsed.isAbsolute) uri else resolve(parsed).toString()
}

private val URI_ATTRIBUTE_REGEX = Regex("""(URI=")([^"]+)(")""")

private fun String.isMediaAssetTag(): Boolean {
    return startsWith("#EXT-X-KEY:") ||
        startsWith("#EXT-X-MAP:") ||
        startsWith("#EXT-X-PART:") ||
        startsWith("#EXT-X-PRELOAD-HINT:")
}

private val logger = me.him188.ani.utils.logging.logger<PlatformHlsPlaybackPreparer>()

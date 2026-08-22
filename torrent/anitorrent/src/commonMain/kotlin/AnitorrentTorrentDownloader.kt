/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.anitorrent.session.TorrentAddInfo
import me.him188.ani.app.torrent.anitorrent.session.TorrentHandle
import me.him188.ani.app.torrent.anitorrent.session.TorrentManagerSession
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.peer.PeerFilter
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.createTempFile
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.io.writeText
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

internal expect fun createAnitorrentTorrentDownloader(
    rootDataDirectory: SystemPath,
    httpFileDownloader: HttpFileDownloader,
    torrentDownloaderConfig: TorrentDownloaderConfig,
    parentCoroutineContext: CoroutineContext,
): AnitorrentTorrentDownloader<*, *>


abstract class AnitorrentTorrentDownloader<THandle : TorrentHandle, TAddInfo : TorrentAddInfo>(
    /**
     * 目录结构:
     * ```
     * rootDataDirectory
     *  |- torrentFiles
     *      |- <uri hash>.txt
     *  |- pieces
     *      |- <uri hash>
     *          |- [libtorrent save files]
     *          |- fastresume
     * ```
     *
     * 其中 uri hash 可能是 magnet URI 的 hash, 也可能是 HTTP URL 的 hash, 取决于 [startDownload] 时提供的是什么.
     */
    rootDataDirectory: SystemPath,
    private val httpFileDownloader: HttpFileDownloader,
    parentCoroutineContext: CoroutineContext,
) : TorrentDownloader, SynchronizedObject() {
    protected abstract val native: TorrentManagerSession<THandle, TAddInfo> // must hold reference.
    protected var filter: PeerFilter? = null
        private set

    /**
     * 用户设置的额外 tracker, 在 [startDownload] 时与内置 tracker 一起添加.
     * 只影响之后开始的下载, 不影响已经开始的.
     */
    @Volatile
    var extraTrackers: List<String> = emptyList()

    companion object {
        private const val FAST_RESUME_FILENAME = "fastresume"

        /**
         * `internal` since used by [createAnitorrentTorrentDownloader]
         */
        @PublishedApi
        internal val logger = logger<AnitorrentTorrentDownloader<*, *>>()
    }

    protected val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    // key is uri hash
    // must be thread-safe
    val openSessions = MutableStateFlow<Map<String, AnitorrentDownloadSession>>(emptyMap())

    override val totalStats: Flow<TorrentDownloader.Stats> = openSessions.flatMapLatest { map ->
        // TODO: 这个信息应该能用 libtorrent 获得, 性能应该更好, 但是要考虑它的 downloadedBytes. LT 的可能暂停了就没了

        val statsFlows = map.values.map {
            // 保证至少 emit 一个, 因为 combine 时如果有一个不 emit, 其他的就会等着
            it.sessionStats.onStart { emit(null) }
        }
        if (statsFlows.isEmpty()) {
            return@flatMapLatest flowOf(TorrentDownloader.Stats(0, 0, 0, 0, 0, 0f))
        }

        combine(statsFlows) { sessions ->
            val totalSize = sessions.sumOf { it?.totalSizeRequested ?: 0L }
            val downloadedBytes = sessions.sumOf { it?.downloadedBytes ?: 0L }
            TorrentDownloader.Stats(
                totalSize = totalSize,
                downloadedBytes = downloadedBytes,
                downloadSpeed = sessions.sumOf { it?.downloadSpeed ?: 0L },
                uploadedBytes = sessions.sumOf { it?.uploadedBytes ?: 0L },
                uploadSpeed = sessions.sumOf { it?.uploadSpeed ?: 0L },
                downloadProgress = if (totalSize == 0L) 0f else downloadedBytes.toFloat() / totalSize,
            )
        }
    }

    override val vendor: TorrentLibInfo = TorrentLibInfo(
        vendor = "Anitorrent",
        version = "1.0.0",
        supportsStreaming = true,
    )

    /**
     * 在 [startDownload] 时初始化, 用于缓存在调用 native startDownload 后, 到 [openSessions] 更新之前的事件.
     * 否则将会丢失事件.
     */
    @Volatile
    protected var handleTaskBuffer: DisposableTaskQueue<AnitorrentTorrentDownloader<THandle, TAddInfo>>? = null

    /**
     * Note: can be called concurrently,
     * from [withHandleTaskQueue] or `newEventListener`
     */
    protected inline fun dispatchToSession(
        id: HandleId,
        crossinline block: (AnitorrentDownloadSession) -> Unit // will be inlined twice, for good amortized performance
    ): Unit = synchronized(this) {
        // contention is very low in most cases, except for when we are creating a new session.

        try {
            openSessions.value.values.find { it.handleId == id }?.let {
                block(it)
                return
            }
            // 这个 handle 仍然在创建中, 需要缓存 block, 延迟执行

            val handleTaskBuffer = handleTaskBuffer
            if (handleTaskBuffer == null) {
                logger.warn {
                    "Session not found for handleId $id while handleTaskBuffer is not set. We are missing event"
                }
                return
            }
            handleTaskBuffer.add {
                // this block does not capture anything

                // Now we should have the session since the startDownload is locked
                openSessions.value.values.find { it.handleId == id }?.let {
                    block(it)
                    return@add
                }
                logger.warn { "A delayed task failed to find session on execute. handleId=$id" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error while handling event" }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo {
        if (uri.startsWith("http", ignoreCase = true)) {
            val cacheFile = getHttpTorrentFileCacheFile(uri)
            if (cacheFile.exists()) {
                val data = cacheFile.readText().hexToByteArray()
                logger.info { "HTTP torrent file '${uri}' found in cache: $cacheFile, length=${data.size}" }
                return AnitorrentAddTorrentInfo.encode(
                    AnitorrentAddTorrentInfo(
                        AnitorrentTorrentData.TorrentFile(data),
                        httpTorrentFilePath = cacheFile.absolutePath,
                    ),
                )
            }
            logger.info { "Fetching http url: $uri" }
            val data = httpFileDownloader.download(uri)
            logger.info { "Fetching http url success, file length = ${data.size}" }
            cacheFile.writeText(data.toHexString())
            logger.info { "Saved cache file: $cacheFile" }
            return AnitorrentAddTorrentInfo.encode(
                AnitorrentAddTorrentInfo(
                    AnitorrentTorrentData.TorrentFile(data),
                    httpTorrentFilePath = cacheFile.absolutePath,
                ),
            )
        }

        require(uri.startsWith("magnet")) { "Expected uri to start with \"magnet\": $uri" }
        return AnitorrentAddTorrentInfo.encode(
            AnitorrentAddTorrentInfo(
                AnitorrentTorrentData.MagnetUri(uri),
                httpTorrentFilePath = null,
            ),
        )
    }

    private val httpTorrentFileCacheDir = rootDataDirectory.resolve("torrentFiles").apply {
        createDirectories()
    }

    private fun getHttpTorrentFileCacheFile(uri: String): SystemPath {
        return httpTorrentFileCacheDir.resolve(uri.hashCode().toString() + ".txt")
    }

    /**
     *  Don't change this directory.
     *
     *  TODO: move this to [TorrentDownloader].
     *
     *  @see TorrentMediaCacheEngine.modifyMediaCacheDirectory
     */
    private val downloadCacheDir = rootDataDirectory.resolve("pieces").apply {
        createDirectories()
    }

    private val sessionsLock = Mutex()

    private suspend inline fun <R> withHandleTaskQueue(crossinline block: suspend () -> R): R =
        sessionsLock.withLock { // 必须只能同时有一个任务在添加. see eventListener
            val queue = DisposableTaskQueue(this)
            check(handleTaskBuffer == null) { "handleTaskBuffer is not null" }
            handleTaskBuffer = queue
            return try {
                kotlin.runCatching { block() }
                    .also {
                        check(handleTaskBuffer == queue) {
                            "handleTaskBuffer changed while executing block"
                        }
                    }
                    .onSuccess {
                        try {
                            val size = queue.runAndDispose()
                            logger.info { "withHandleTaskQueue: executed $size delayed tasks" }
                        } catch (e: Exception) {
                            throw IllegalStateException(
                                "Failed to run delayed tasks during withHandleTaskQueue, see cause",
                                e,
                            )
                        }
                    }
                    .getOrThrow() // rethrow exception
            } finally {
                // May drop all queued tasks on exception
                handleTaskBuffer = null
            }
        }

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
    ): TorrentSession = withHandleTaskQueue {
        // 这个函数的 native 部分跑得也都很快, 整个函数十几毫秒就可以跑完, 所以 lock 也不会影响性能 (刚启动时需要尽快恢复 resume)

        val info = AnitorrentAddTorrentInfo.decodeFrom(data)
        val saveDir = getSaveDirForTorrent(data)
        val fastResumeFile = saveDir.resolve(FAST_RESUME_FILENAME)

        openSessions.value[data.data.contentHashCode().toString()]?.let {
            logger.info { "Found existing session" }
            return@withHandleTaskQueue it
        }

        val handle = native.createTorrentHandle()
        val addInfo = native.createTorrentAddInfo()
        when (info.data) {
            is AnitorrentTorrentData.MagnetUri -> {
                addInfo.setMagnetUri(info.data.uri)
                logger.info { "Creating a session using magnetUri. length=${info.data.uri.length}" }
            }

            is AnitorrentTorrentData.TorrentFile -> {
                withContext(Dispatchers.IO_) {
                    val tempFile = SystemPaths.createTempFile("anitorrent", ".torrent")
                    tempFile.writeBytes(info.data.data)
                    addInfo.setTorrentFilePath(tempFile.absolutePath)
                }
                logger.info { "Creating a session using torrent file. data length=${info.data.data.size}" }
            }
        }

        if (fastResumeFile.exists()) {
            logger.info { "start_download: including fastResumeFile: ${fastResumeFile.absolutePath}" }
            addInfo.setResumeDataPath(fastResumeFile.absolutePath)
        }

        // start_download 之后它就会开始发射 event
        if (!native.startDownload(handle, addInfo, saveDir.path)) {
            throw IllegalStateException("Failed to start download, native failed")
        }

        return@withHandleTaskQueue AnitorrentDownloadSession(
            handle,
            saveDir,
            fastResumeFile = fastResumeFile,
            onClose = { native.releaseHandle(handle) },
            onPostClose = { openSessions.value -= data.data.contentHashCode().toString() },
            onDelete = {
                scope.launch {
                    // http 下载的 .torrent 文件保存在全局路径, 需要删除
                    info.httpTorrentFilePath?.let(::Path)?.let { cacheFile ->
                        withContext(Dispatchers.IO_) {
                            if (cacheFile.inSystem.exists()) {
                                cacheFile.inSystem.delete()
                            }
                        }
                    }
                    // fast resume 保存在 saveDir 内, 已经被删除
                }
            },
            parentCoroutineContext = parentCoroutineContext,
        ).also {
            openSessions.value += data.data.contentHashCode().toString() to it // 放进去之后才能处理 alert
            val trackers = (builtinTrackers + extraTrackers).distinct()
            logger.info { "[${it.handleId}] AnitorrentDownloadSession created, adding ${trackers.size} trackers (${extraTrackers.size} extra)" }
            for (tracker in trackers) {
                handle.addTracker(tracker, 0, 0)
            }
            native.resume()
        }
    }

    fun setPeerFilter(filter: PeerFilter) {
        this.filter = filter
    }

    /**
     *  Don't change this.
     *
     *  TODO: move this to [TorrentDownloader].
     *
     *  @see TorrentMediaCacheEngine.modifyMediaCacheDirectory
     */
    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): SystemPath =
        downloadCacheDir.resolve(data.data.contentHashCode().toString())

    override fun listSaves(): List<SystemPath> {
        return downloadCacheDir.list().toList().map { it.inSystem }
    }

    fun applyConfig(config: TorrentDownloaderConfig) {
        extraTrackers = config.extraTrackers
        native.applyConfig(config)
    }

    override fun close() {
        logger.info { "AnitorrentDownloadSession closing" }
        scope.cancel()
        httpFileDownloader.close()
    }
}

typealias HandleId = Long

private val builtinTrackers by lazy {
    """
http://1337.abcvg.info:80/announce
http://bt1.archive.org:6969/announce
http://bt2.archive.org:6969/announce
http://ipv4announce.sktorrent.eu:6969/announce
http://nyaa.tracker.wf:7777/announce
http://torrentsmd.com:8080/announce
http://tracker.bt4g.com:2095/announce
http://tracker.dhitechnical.com:6969/announce
http://tracker.mywaifu.best:6969/announce
http://tracker.renfei.net:8080/announce
http://tracker.waaa.moe:6969/announce
http://tracker810.xyz:11450/announce
http://www.wareztorrent.com:80/announce
https://004430.xyz:443/announce
https://1337.abcvg.info:443/announce
https://ht.therarbg.to:443/announce
https://t.213891.xyz:443/announce
https://tr.abiir.top:443/announce
https://tr.abir.ga:443/announce
https://tr.zukizuki.org:443/announce
https://tracker.7471.top:443/announce
https://tracker.bt4g.com:443/announce
https://tracker.foreverpirates.co:443/announce
https://tracker.gcrenwp.top:443/announce
https://tracker.kuroy.me:443/announce
https://tracker.leechshield.link:443/announce
https://tracker.linvk.com:443/announce
https://tracker.nekomi.cn:443/announce
https://tracker.pmman.tech:443/announce
https://tracker.yemekyedim.com:443/announce
https://tracker.zhuqiy.com:443/announce
https://tracker1.520.jp:443/announce
udp://bittorrent-tracker.e-n-c-r-y-p-t.net:1337/announce
udp://evan.im:6969/announce
udp://explodie.org:6969/announce
udp://ipv6.govt.hu:6969/announce
udp://mail.segso.net:6969/announce
udp://martin-gebhardt.eu:25/announce
udp://ns575949.ip-51-222-82.net:6969/announce
udp://obey.torrentonline.cc:42069/announce
udp://open.demonii.com:1337/announce
udp://open.ftorrent.com:443/announce
udp://open.stealth.si:80/announce
udp://open.tracker.ink:6969/announce
udp://opentor.org:2710/announce
udp://p4p.arenabg.com:1337/announce
udp://retracker.hotplug.ru:2710/announce
udp://t.overflow.biz:6969/announce
udp://torrent.tracker.durukanbal.com:6969/announce
udp://torrentclub.online:1984/announce
udp://torrentclub.online:54123/announce
udp://tr.btube3.com:2010/announce
udp://tr3.ysagin.top:2715/announce
udp://tracker-udp.gbitt.info:80/announce
udp://tracker.0x7c0.com:6969/announce
udp://tracker.aruku.ovh:8081/announce
udp://tracker.auctor.tv:6969/announce
udp://tracker.breizh.pm:6969/announce
udp://tracker.cn.nyaa.net:6969/announce
udp://tracker.corpscorp.online:80/announce
udp://tracker.cynma.tv:6969/announce
udp://tracker.dler.com:6969/announce
udp://tracker.dler.org:6969/announce
udp://tracker.farted.net:6969/announce
udp://tracker.gmi.gd:6969/announce
udp://tracker.ilibr.org:6969/announce
udp://tracker.k.vu:6969/announce
udp://tracker.nexusstream.eu:6969/announce
udp://tracker.nyaa.net:6969/announce
udp://tracker.opentrackr.com:6969/announce
udp://tracker.opentrackr.org:1337/announce
udp://tracker.peerfect.org:6969/announce
udp://tracker.publictracker.xyz:6969/announce
udp://tracker.qu.ax:6969/announce
udp://tracker.skyts.net:6969/announce
udp://tracker.teambelgium.net:6969/announce
udp://tracker.torrent.eu.org:451/announce
udp://whybother.torrentonline.cc:42069/announce
udp://zer0day.ch:1337/announce
wss://tracker.openwebtorrent.com:443/announce
""".trimIndent().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}

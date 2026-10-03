/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import me.him188.ani.app.domain.media.cache.engine.EnsureTorrentEngineIsAccessible
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.UnsafeTorrentEngineAccessApi
import me.him188.ani.app.domain.media.cache.engine.withServiceRequest
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.torrent.pikpak.CloudReadiness
import me.him188.ani.torrent.pikpak.PartialListing
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.MediaExtraFiles
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Resolver chains choose by supports(), not by success. Cloud failures need an explicit BT fallback.
class TorrentMediaResolver(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val fallback: MediaResolver? = null,
    private val cloudOpenTimeout: Duration = CLOUD_OPEN_TIMEOUT,
    // Injected so a test can keep the open and its timeout on one virtual clock. With a real IO
    // dispatcher the timeout races the fake open, and a slow runner loses that race.
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : MediaResolver {
    override fun supports(media: Media): Boolean {
        if (!engine.isSupported) return false
        return media.download is ResourceLocation.HttpTorrentFile || media.download is ResourceLocation.MagnetLink
    }

    @Throws(MediaResolutionException::class, CancellationException::class)
    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withServiceRequest("TorrentMediaResolver#$this-resolve:${media.mediaId}") {
            val downloader = try {
                engine.getDownloader()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                resolveWithFallback(media, episode, e)?.let { return it }
                throw MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
            }

            return when (val location = media.download) {
                is ResourceLocation.HttpTorrentFile,
                is ResourceLocation.MagnetLink
                    -> {
                    try {
                        TorrentMediaDataProvider(
                            engine,
                            engineAccess = engineAccess,
                            encodedTorrentInfo = downloader.fetchTorrent(location.uri),
                            episodeMetadata = episode,
                            extraFiles = media.extraFiles.toMediampMediaExtraFiles(),
                            fallback = fallback?.takeIf { it.supports(media) }?.let {
                                suspend { it.resolve(media, episode) }
                            },
                            cloudOpenTimeout = cloudOpenTimeout,
                            ioDispatcher = ioDispatcher,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        resolveWithFallback(media, episode, e)?.let { return it }
                        throw when (e) {
                            is FetchTorrentTimeoutException ->
                                MediaResolutionException(ResolutionFailures.FETCH_TIMEOUT)

                            is IOException ->
                                MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)

                            else -> MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
                        }
                    }
                }

                else -> throw UnsupportedMediaException(media)
            }
        }
    }

    private suspend fun resolveWithFallback(
        media: Media,
        episode: EpisodeMetadata,
        cause: Throwable,
    ): MediaDataProvider<*>? {
        val fallback = fallback ?: return null
        if (!fallback.supports(media)) return null
        logger.warn(cause) { "${engine.type} failed to resolve $media, falling back to $fallback" }
        return fallback.resolve(media, episode)
    }

    companion object {
        private val logger = logger<TorrentMediaResolver>()

        private val DEFAULT_VIDEO_EXTENSIONS =
            setOf("mp4", "mkv", "avi", "mpeg", "mov", "flv", "wmv", "webm", "rm", "rmvb")

        /**
         * 黑名单词, 包含这些词的文件会被放到最后.
         *
         * 黑名单也有顺序, 在黑名单中的越靠前越容易被选择.
         */
        @Suppress("RegExpRedundantEscape")
        private val BLACKLIST_WORDS = // 性能还可以, regex 只是让 70ms 变成了 150ms (不过它可能在指数的复杂度)
            setOf(
                Regex("""\[SP[0-9]*\]"""),
                Regex("""\[OVA[0-9]*\]"""),
                // SP 和 OVA 要放到前面, 因为用户可能就是要看这个
                Regex("""PV[0-9]*"""),
                Regex("""NCOP[0-9]*"""),
                Regex("""NCED[0-9]*"""),
                Regex("""OP[0-9]+"""),
                Regex("""ED[0-9]+"""), // 必须匹配数字防止名字带有 OP ED 的情况
                Regex("""\[OP[0-9]*\]"""),
                Regex("""\[ED[0-9]*\]"""),
                Regex("""\[CM[0-9]*\]"""),
            )

        /**
         * @param episodeSort 在系列中的集数, 例如第二季的第一集为 26
         * @param episodeEp 在当前季度中的集数, 例如第二季的第一集为 01
         */
        fun <T> selectVideoFileEntry(
            entries: List<T>,
            getPath: T.() -> String,
            episodeTitles: List<String>,
            episodeSort: EpisodeSort,
            episodeEp: EpisodeSort?,
            videoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
            listingComplete: Boolean = true,
        ): T? {
            // Filter by file extension
            val videos = entries
                .filterTo(ArrayList(entries.size)) {
                    videoExtensions.any { fileType -> it.getPath().endsWith(fileType, ignoreCase = true) }
                }

            videos.sortByDescending {
                BLACKLIST_WORDS.forEachIndexed { index, blacklistWord ->
                    if (it.getPath().contains(blacklistWord)) {
                        return@sortByDescending -index // 包含黑名单词的放到最后
                    }
                }
                1
            }

            // Find by name match
            for (episodeTitle in episodeTitles) {
                val entry = videos.singleOrNull {
                    it.getPath().contains(episodeTitle, ignoreCase = true)
                }
                if (entry != null) return entry
            }

            // 解析标题匹配集数
            val parsedTitles = buildMap { // similar to `associateWith`, but ignores nulls
                for (entry in videos) {
                    val title = RawTitleParser.getDefault()
                        .parse(
                            entry.getPath()
                                .substringBeforeLast(".")
                                .substringAfterLast("\\")
                                .substringAfterLast("/"),
                            null,
                        )
                        .episodeRange
                    if (title != null) { // difference between `associateWith`
                        put(entry, title)
                    }
                }
            }
            // 优先按系列集数 sort 匹配 (数字较大)
            if (parsedTitles.isNotEmpty()) {
                parsedTitles.entries.firstOrNull {
                    it.value.contains(episodeSort, allowSeason = false) // 季度全集在匹配文件时是无意义的
                }?.key?.let { return it }
            }
            // 然后按季度集数 ep 匹配
            if (episodeEp != null && parsedTitles.isNotEmpty()) {
                parsedTitles.entries.firstOrNull {
                    it.value.contains(episodeEp, allowSeason = false)
                }?.key?.let { return it }
            }

            // 解析失败, 尽可能匹配一个
            episodeSort.toString().let { number ->
                videos.firstOrNull { it.getPath().contains(number, ignoreCase = true) }
                    ?.let { return it }
            }

            // A lone imported episode is not evidence that the torrent contains only that episode.
            if (!listingComplete) return null
            return videos.firstOrNull()
        }
    }
}

class IncompleteFileListingException(message: String) : Exception(message)

class CloudNotReadyException(message: String) : Exception(message)

// Budget for the whole cloud open: the session (a sign-in and a magnet lookup on first play), the
// file listing, and the readiness check (the file object, the variant and the signed link). Long
// enough to cover a cold start that works, short enough that a network which cannot reach the cloud
// sends playback to the local BT engine instead of a spinner.
private val CLOUD_OPEN_TIMEOUT = 15.seconds

/**
 * Marker for [MediaDataProvider]s backed by a torrent engine, local or cloud.
 */
interface TorrentBackedMediaDataProvider {
    /**
     * The engine about to serve playback. [TorrentMediaData.engineKey] is the one that ended up
     * serving it: opening may still fall back from the cloud to the local engine.
     */
    val engineKey: MediaCacheEngineKey
}

class TorrentMediaDataProvider(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val encodedTorrentInfo: EncodedTorrentInfo,
    private val episodeMetadata: EpisodeMetadata,
    override val extraFiles: MediaExtraFiles,
    private val fallback: (suspend () -> MediaDataProvider<*>)? = null,
    private val cloudOpenTimeout: Duration = CLOUD_OPEN_TIMEOUT,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : MediaDataProvider<MediaData>, TorrentBackedMediaDataProvider {
    override val engineKey: MediaCacheEngineKey get() = MediaCacheEngineKey(engine.type.id)

    @OptIn(ExperimentalStdlibApi::class)
    val uri: String by lazy {
        "torrent://${encodedTorrentInfo.data.toHexString().take(32) + "..."}"
    }

    @Throws(MediaSourceOpenException::class, CancellationException::class)
    override suspend fun open(scopeForCleanup: CoroutineScope): MediaData {
        // 注意, 这个函数须支持 cancellation. 它会在任意时刻被取消.

        logger.info {
            "TorrentVideoSource '${episodeMetadata.title}' opening a VideoData"
        }

        val requestToken = "TorrentMediaDataProvider#$this-open:${encodedTorrentInfo.data}"
        // 使用 MediaDataProvider.open 通常是在播放临时 BT 源, 在下面的 onClose 里再释放.
        // 也就是说进入从开启这个 MediaData 开始, 到下面 onClose 释放期间, 需要始终保持 BT 服务可用.
        @OptIn(UnsafeTorrentEngineAccessApi::class)
        engineAccess.requestService(requestToken, true)

        var torrentSession: TorrentSession? = null
        var fileHandle: TorrentFileHandle? = null
        val handle = try {
            // 云端打开的三步共用一个预算: 建 session (首播要登录并解析磁力), 列文件选文件, 等云端就绪.
            // 任何一步卡住都要在预算内回退到本地 BT 引擎, 单独给最后一步计时的话前两步卡住就永远等不到回退.
            // withTimeoutOrNull 而不是 withTimeout: 后者抛 CancellationException, 下面的 catch 会把它
            // 当作用户取消重抛, 回退就走不到了.
            suspend fun openCloud(): TorrentFileHandle {
                val downloader = engine.getDownloader()
                return withContext(ioDispatcher) {
                    logger.info {
                        "TorrentVideoSource '${episodeMetadata.title}' waiting for files"
                    }
                    val session = downloader.startDownload(encodedTorrentInfo)
                    torrentSession = session
                    val files = session.getFiles()

                    val listingComplete = (session as? PartialListing)?.listingComplete ?: true
                    val selected = TorrentMediaResolver.selectVideoFileEntry(
                        files,
                        { fileName },
                        listOf(episodeMetadata.title),
                        episodeSort = episodeMetadata.sort,
                        episodeEp = episodeMetadata.ep,
                        listingComplete = listingComplete,
                    )
                    selected?.also {
                        logger.info {
                            "TorrentVideoSource selected file: ${it.fileName}"
                        }
                    }?.createHandle()?.also { handle ->
                        fileHandle = handle
                        handle.resume(FilePriority.HIGH)
                        // 云端这条路在 SDK 内部重试, 网络断了也不会在这里抛错, 只会一直等下去.
                        (selected as? CloudReadiness)?.ensureCloudReady()
                    } ?: run {
                        val diagnosis = """
                                Torrent files: ${files.joinToString { it.fileName }}
                                Episode metadata: $episodeMetadata
                            """.trimIndent()

                        if (listingComplete) {
                            throw MediaSourceOpenException(OpenFailures.NO_MATCHING_FILE, diagnosis)
                        }
                        throw IncompleteFileListingException(
                            "${engine.type} lists only part of the torrent. $diagnosis",
                        )
                    }
                }
            }

            // 超时预算用于及时切换到备用引擎。没有备用引擎时继续等待云端，避免丢弃仍可完成的解析。
            if (fallback == null) {
                openCloud()
            } else {
                // 预算用尽也走下面的 catch 去回退, 所以不能是 MediaSourceOpenException: 那个会被原样抛给播放器.
                withTimeoutOrNull(cloudOpenTimeout) { openCloud() } ?: throw CloudNotReadyException(
                    "${engine.type} did not open '${episodeMetadata.title}' within $cloudOpenTimeout",
                )
            }
        } catch (ex: Exception) {
            // 预算用尽表现为协程被取消, 此时 close 这样的挂起调用会立刻抛出, 清理必须在 NonCancellable 里做.
            withContext(NonCancellable) {
                try {
                    fileHandle?.close()
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to release the ${engine.type} file handle" }
                }
            }
            // 如果上面发生了异常或被取消, 下面的 onClose 就永远不会被调用, 需要手动释放.
            @OptIn(UnsafeTorrentEngineAccessApi::class)
            engineAccess.requestService(requestToken, false)

            if (ex is CancellationException || ex is MediaSourceOpenException) throw ex
            val fallback = this.fallback ?: throw when (ex) {
                is IncompleteFileListingException ->
                    MediaSourceOpenException(OpenFailures.NO_MATCHING_FILE, ex.message.orEmpty(), ex)

                // 没有回退时这个异常是用户唯一能看到的东西, 原样抛出会在播放器上显示为「未知错误」.
                is IOException -> MediaResolutionException(ResolutionFailures.NETWORK_ERROR, ex)

                else -> ex
            }
            logger.warn(ex) { "${engine.type} failed to open '${episodeMetadata.title}', falling back" }
            // 本次申请的 handle 已经在上面释放, 没有别的使用者的话这个 session 不会有人再关它.
            torrentSession?.let { session ->
                try {
                    session.closeIfNotInUse()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to close ${engine.type} session before falling back" }
                }
            }
            return fallback().open(scopeForCleanup)
        }

        return TorrentMediaData(
            handle,
            engineKey = engineKey,
            session = torrentSession,
            onClose = {
                logger.info {
                    "TorrentVideoSource '${episodeMetadata.title}' closing"
                }
                scopeForCleanup.launch(NonCancellable + CoroutineName("TorrentMediaDataProvider.close")) {
                    try {
                        handle.close()
                    } finally {
                        // 对应了上面的 requestUseEngine(true)
                        @OptIn(UnsafeTorrentEngineAccessApi::class)
                        engineAccess.requestService(requestToken, false)
                    }
                }
            },
        )
    }

    override fun toString(): String = "TorrentVideoSource(uri=$uri, episodeMetadata=${episodeMetadata})"

    companion object {
        private val logger = logger<TorrentMediaDataProvider>()
    }
}

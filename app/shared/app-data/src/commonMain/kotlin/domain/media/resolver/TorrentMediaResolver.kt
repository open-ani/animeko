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
import kotlinx.io.IOException
import me.him188.ani.app.domain.media.cache.engine.EnsureTorrentEngineIsAccessible
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.UnsafeTorrentEngineAccessApi
import me.him188.ani.app.domain.media.cache.engine.withEngineAccessible
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException

class TorrentMediaResolver(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
) : MediaResolver {
    override fun supports(media: Media): Boolean {
        if (!engine.isSupported) return false
        return media.download is ResourceLocation.HttpTorrentFile || media.download is ResourceLocation.MagnetLink
    }

    @Throws(MediaResolutionException::class, CancellationException::class)
    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withEngineAccessible("TorrentMediaResolver#$this-resolve:${media.mediaId}") {
            val downloader = try {
                engine.getDownloader()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
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
                        )
                    } catch (e: FetchTorrentTimeoutException) {
                        throw MediaResolutionException(ResolutionFailures.FETCH_TIMEOUT)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        throw MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)
                    } catch (e: Exception) {
                        throw MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
                    }
                }

                else -> throw UnsupportedMediaException(media)
            }
        }
    }

    companion object {
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
                    val title = RawTitleParser.getDefault().parse(
                        entry.getPath().substringBeforeLast("."),
                        null,
                    ).episodeRange
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

            return videos.firstOrNull()
        }
    }
}

class TorrentMediaDataProvider(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val encodedTorrentInfo: EncodedTorrentInfo,
    private val episodeMetadata: EpisodeMetadata,
    override val extraFiles: org.openani.mediamp.source.MediaExtraFiles,
) : MediaDataProvider<TorrentMediaData> {
    @OptIn(ExperimentalStdlibApi::class)
    val uri: String by lazy {
        "torrent://${encodedTorrentInfo.data.toHexString().take(32) + "..."}"
    }

    @Throws(MediaSourceOpenException::class, CancellationException::class)
    override suspend fun open(scopeForCleanup: CoroutineScope): TorrentMediaData {
        // 注意, 这个函数须支持 cancellation. 它会在任意时刻被取消.

        logger.info {
            "TorrentVideoSource '${episodeMetadata.title}' opening a VideoData"
        }

        val requestToken = "TorrentMediaDataProvider#$this-open:${encodedTorrentInfo.data}"
        // 使用 MediaDataProvider.open 通常是在播放临时 BT 源, 在下面的 onClose 里再释放.
        // 也就是说进入从开启这个 MediaData 开始, 到下面 onClose 释放期间, 需要始终保持 BT 服务可用.
        @OptIn(UnsafeTorrentEngineAccessApi::class)
        engineAccess.requestUseEngine(requestToken, true)

        val downloader = engine.getDownloader()
        val handle = withContext(Dispatchers.IO) {
            logger.info {
                "TorrentVideoSource '${episodeMetadata.title}' waiting for files"
            }
            val files = downloader.startDownload(encodedTorrentInfo)
                .getFiles()

            TorrentMediaResolver.selectVideoFileEntry(
                files,
                { pathInTorrent },
                listOf(episodeMetadata.title),
                episodeSort = episodeMetadata.sort,
                episodeEp = episodeMetadata.ep,
            )?.also {
                logger.info {
                    "TorrentVideoSource selected file: ${it.pathInTorrent}"
                }
            }?.createHandle()?.also {
                it.resume(FilePriority.HIGH)
            } ?: throw MediaSourceOpenException(
                OpenFailures.NO_MATCHING_FILE,
                """
                                Torrent files: ${files.joinToString { it.pathInTorrent }}
                                Episode metadata: $episodeMetadata
                            """.trimIndent(),
            )
        }
        return TorrentMediaData(
            handle,
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
                        engineAccess.requestUseEngine(requestToken, false)
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

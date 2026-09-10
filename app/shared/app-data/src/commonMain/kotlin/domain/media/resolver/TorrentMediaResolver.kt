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
import me.him188.ani.app.domain.media.cache.engine.withServiceRequest
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException

class TorrentMediaResolver(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val fileOverrideStore: TorrentFileOverrideStore = TorrentFileOverrideStore.Default,
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
                throw MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
            }

            return when (val location = media.download) {
                is ResourceLocation.HttpTorrentFile,
                is ResourceLocation.MagnetLink
                    -> {
                    val encodedTorrentInfo = try {
                        downloader.fetchTorrent(location.uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw when (e) {
                            is FetchTorrentTimeoutException ->
                                MediaResolutionException(ResolutionFailures.FETCH_TIMEOUT)

                            is IOException -> MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)
                            else -> MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
                        }
                    }
                    TorrentMediaDataProvider(
                        engine,
                        engineAccess = engineAccess,
                        encodedTorrentInfo = encodedTorrentInfo,
                        episodeMetadata = episode,
                        extraFiles = media.extraFiles.toMediampMediaExtraFiles(),
                        // 用户手动指定的文件优先: 它是在自动匹配失败之后选的, 缓存记录里
                        // 记下来的路径可能还是上一次匹配的结果.
                        preferredPathInTorrent = fileOverrideStore.get(media, episode.sort)
                            ?: (media as? CachedMedia)?.cacheProperties?.pathInTorrent,
                    )
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
         * 匹配不上时只在种子里只有一个视频文件的情况下退回那个文件.
         *
         * 多个视频文件时匹配不上就是 `null`: 整季包对任何集数都退回第一个文件, 等于声称包里有这一集,
         * 于是 SP 会静默播成第 01 集. 这种时候要让上层报 [OpenFailures.NO_MATCHING_FILE] 并让用户自己挑.
         *
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
            val videos = sortVideoEntries(entries, getPath, videoExtensions)

            matchVideoFileEntry(videos, episodeTitles, episodeSort, episodeEp)?.let { return it }

            // 单文件种子的文件名可能完全解析不出集数 (例如只有作品名), 这时它就是要播的那个.
            return videos.singleOrNull()?.entry
        }

        /**
         * 种子里的视频文件, 顺序与 [selectVideoFileEntry] 的候选顺序一致.
         *
         * 匹配失败后要把这份清单交给用户挑, 挑的顺序应当和程序原本的偏好一致.
         */
        fun <T> listVideoFileEntries(
            entries: List<T>,
            getPath: T.() -> String,
            episodeSort: EpisodeSort? = null,
            videoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
        ): List<T> {
            val sorted = sortVideoEntries(entries, getPath, videoExtensions)
            // 挑 SP 时把同类文件放到最前, 正片和 NCOP 一类往后排.
            val kind = episodeSort?.let { TorrentFileLabel.kindOf(it) }
                ?: return sorted.map { it.entry }
            return sorted.sortedBy { if (it.label?.kind == kind) 0 else 1 }.map { it.entry }
        }

        /**
         * 种子里有没有 [episodeSort] 这一类的视频文件. 正片与 MAD 没有类别, 返回 `false`.
         *
         * 判断经过与 [selectVideoFileEntry] 相同的根目录剥离, 直接对路径调 [TorrentFileLabel.of]
         * 会把 anitorrent 路径里的根目录当成一层目录.
         */
        fun <T> hasVideoFileOfKind(
            entries: List<T>,
            getPath: T.() -> String,
            episodeSort: EpisodeSort,
            videoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
        ): Boolean {
            val kind = TorrentFileLabel.kindOf(episodeSort) ?: return false
            return sortVideoEntries(entries, getPath, videoExtensions).any { it.label?.kind == kind }
        }

        /**
         * 与 [selectVideoFileEntry] 用同一套标题与集数匹配, 但不做单文件兜底.
         *
         * 用于「这个种子里到底有没有这一集」的判断: 单文件兜底会让只有一个视频的种子对任何集数都声称匹配.
         */
        fun <T> selectVideoFileEntryExact(
            entries: List<T>,
            getPath: T.() -> String,
            episodeTitles: List<String>,
            episodeSort: EpisodeSort,
            episodeEp: EpisodeSort?,
            videoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
        ): T? = matchVideoFileEntry(
            sortVideoEntries(entries, getPath, videoExtensions),
            episodeTitles, episodeSort, episodeEp,
        )

        /**
         * 一个候选文件. [path] 是剥掉种子根目录之后的种子内路径, [label] 按它算出.
         */
        private class Candidate<T>(val entry: T, val path: String) {
            val label: TorrentFileLabel? = TorrentFileLabel.of(path)
            val fileName: String get() = path.substringAfterLast('/')
        }

        /**
         * 剥掉所有条目共有的首段目录.
         *
         * 根目录名列的是整包的内容清单, `Title - TV + OVA + SP` 末尾正好是 SP, 让它参与类别判定会把
         * 整包连正片一起当成花絮.
         *
         * 按「所有文件共有」而不是「第一段」判, 因为路径带不带根目录取决于引擎: anitorrent 的
         * pathInTorrent 是 libtorrent 的 file_path, 带根目录; 把云盘目录当种子看的引擎通常已经把它剥掉了.
         * 前者根目录必然共有, 后者没有根目录也就没有共有段, 两边由此对齐.
         */
        private fun <T> withoutCommonRoot(entries: List<T>, getPath: T.() -> String): List<Candidate<T>> {
            val paths = entries.map { it.getPath().replace('\\', '/') }
            val root = paths.firstOrNull()?.substringBefore('/', missingDelimiterValue = "").orEmpty()
            val shared = root.isNotEmpty() && paths.all { it.startsWith("$root/") }
            return entries.mapIndexed { index, entry ->
                Candidate(entry, if (shared) paths[index].removePrefix("$root/") else paths[index])
            }
        }

        private fun <T> sortVideoEntries(
            entries: List<T>,
            getPath: T.() -> String,
            videoExtensions: Set<String>,
        ): List<Candidate<T>> {
            // Filter by file extension
            val videos = withoutCommonRoot(entries, getPath)
                .filterTo(ArrayList(entries.size)) {
                    videoExtensions.any { fileType -> it.path.endsWith(fileType, ignoreCase = true) }
                }

            videos.sortByDescending {
                BLACKLIST_WORDS.forEachIndexed { index, blacklistWord ->
                    if (it.path.contains(blacklistWord)) {
                        return@sortByDescending -index // 包含黑名单词的放到最后
                    }
                }
                1
            }
            return videos
        }

        private fun <T> matchVideoFileEntry(
            videos: List<Candidate<T>>,
            episodeTitles: List<String>,
            episodeSort: EpisodeSort,
            episodeEp: EpisodeSort?,
        ): T? {
            // Find by name match
            for (episodeTitle in episodeTitles) {
                if (episodeTitle.isEmpty()) continue
                val entry = videos.singleOrNull {
                    it.fileName.contains(episodeTitle, ignoreCase = true)
                }
                if (entry != null) return entry.entry
            }

            // 特别篇不按编号自动匹配. 发布方的序号与 Bangumi 的序号是两套体系: 無職転生 II 唯一的 SP 叫
            // S00E02; 巨人的 OAD02 是 3.25 话早于 OAD01; 孤独摇滚包里的 S00E01 是 11.5 话, Bangumi 的 SP01
            // 却是 ABEMA 特番. 编号相等也可能是巧合, 播错了用户没有入口纠正, 所以只认标题命中 (上面) 和
            // 用户的选择 (记录里的 pathInTorrent), 其余交给用户挑, 挑一次落记录.
            // 唯一的例外是集数本身没有编号 (Bangumi 只叫它 "SP") 而包里同类文件只有一个, 这不是按编号对.
            if (episodeSort is EpisodeSort.Special) {
                val kind = TorrentFileLabel.kindOf(episodeSort) ?: return null
                if (episodeSort.number != null) return null
                return videos.singleOrNull { it.label?.kind == kind }?.entry
            }

            // 正片只在没有特别篇标签的文件里找: Moozzi2 的 `[SP01] NCOP - 02` 能解析出 02, 但它不是第 2 集.
            val mainStory = videos.filter { it.label == null }

            // 解析标题匹配集数
            val parsedTitles = buildMap { // similar to `associateWith`, but ignores nulls
                for (candidate in mainStory) {
                    val title = RawTitleParser.getDefault()
                        .parse(candidate.fileName.substringBeforeLast("."), null)
                        .episodeRange
                    if (title != null) { // difference between `associateWith`
                        put(candidate, title)
                    }
                }
            }
            // 优先按系列集数 sort 匹配 (数字较大)
            parsedTitles.entries.firstOrNull {
                // 季度全集在匹配文件时是无意义的. 正片不接受 special 等价, 否则正片 01 会命中 S00E01.
                it.value.contains(episodeSort, allowSeason = false, allowSpecial = false)
            }?.key?.let { return it.entry }
            // 然后按季度集数 ep 匹配
            if (episodeEp != null) {
                parsedTitles.entries.firstOrNull {
                    it.value.contains(episodeEp, allowSeason = false, allowSpecial = false)
                }?.key?.let { return it.entry }
            }

            // 解析不出集数时拿数字本身去找, 但要求它是一段完整的数字: 08 不能落在 [1080P] 里面.
            if (episodeSort.number == null) return null
            val standalone = Regex("""(?<!\d)0*${Regex.escape(episodeSort.toString())}(?!\d)""")
            // 只看文件名: `Season 1/` 一类目录里的数字会让整个目录对第 1 集都命中.
            return mainStory.firstOrNull { standalone.containsMatchIn(it.fileName) }?.entry
        }
    }
}

/**
 * Marker for [MediaDataProvider]s backed by a local BitTorrent engine.
 */
interface TorrentBackedMediaDataProvider

class TorrentMediaDataProvider(
    private val engine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val encodedTorrentInfo: EncodedTorrentInfo,
    private val episodeMetadata: EpisodeMetadata,
    override val extraFiles: org.openani.mediamp.source.MediaExtraFiles,
    /**
     * 已经确定的文件路径 ([TorrentFileEntry.pathInTorrent]). 整季包的派生命中在查询阶段就匹配过一次,
     * 这里直接用那个结果, 免得同一份清单再匹配一遍还可能匹配到别的文件.
     */
    private val preferredPathInTorrent: String? = null,
) : MediaDataProvider<TorrentMediaData>, TorrentBackedMediaDataProvider {
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
        engineAccess.requestService(requestToken, true)

        val handle = try {
            val downloader = engine.getDownloader()
            withContext(Dispatchers.IO_) {
                logger.info {
                    "TorrentVideoSource '${episodeMetadata.title}' waiting for files"
                }
                val files = downloader.startDownload(encodedTorrentInfo).getFiles()

                val selected = preferredPathInTorrent
                    ?.let { path -> files.firstOrNull { it.pathInTorrent == path } }
                    ?: TorrentMediaResolver.selectVideoFileEntry(
                        files,
                        { pathInTorrent },
                        listOf(episodeMetadata.title),
                        episodeSort = episodeMetadata.sort,
                        episodeEp = episodeMetadata.ep,
                    )

                selected?.also {
                    logger.info {
                        "TorrentVideoSource selected file: ${it.fileName}"
                    }
                }?.createHandle()?.also {
                    it.resume(FilePriority.HIGH)
                } ?: throw MediaSourceOpenException(
                    OpenFailures.NO_MATCHING_FILE,
                    """
                                Torrent files: ${files.joinToString { it.fileName }}
                                Episode metadata: $episodeMetadata
                            """.trimIndent(),
                    filesInTorrent = TorrentMediaResolver
                        .listVideoFileEntries(files, { pathInTorrent }, episodeMetadata.sort)
                        .map { it.pathInTorrent },
                )
            }
        } catch (ex: Exception) {
            // 如果上面发生了异常或被取消, 下面的 onClose 就永远不会被调用, 需要手动释放.
            @OptIn(UnsafeTorrentEngineAccessApi::class)
            engineAccess.requestService(requestToken, false)

            throw ex // just re-throw it
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

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.files
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent

/**
 * Since 4.9, Default directory of torrent cache is changed to external/shared storage and
 * cannot be changed. This is the workaround for startup migration.
 *
 * Since 4.11, Default directory of web m3u cache is changed to external/shared storage and
 * cannot be changed. This is the workaround for startup migration.
 *
 * This class should be called only `AniApplication.Instance.requiresTorrentCacheMigration` is true,
 * which means we are going to migrate torrent caches from internal storage to shared/external storage.
 */
class MediaCacheMigrator(
    private val context: ContextMP,
    private val metadataStore: DataStore<List<MediaCacheSave>>,
    private val m3u8DownloaderStore: DataStore<List<DownloadState>>,
    private val mediaCacheManager: MediaCacheManager,
    private val settingsRepo: SettingsRepository,
    private val appTerminator: AppTerminator,
    private val migrateTorrent: StateFlow<Boolean>,
    private val migrateWebM3u: StateFlow<Boolean>,

    private val getNewBaseSaveDir: suspend () -> SystemPath?,
    private val getPrevTorrentSaveDir: suspend () -> SystemPath
) : KoinComponent {
    private val logger = logger<MediaCacheMigrator>()

    private val _status: MutableStateFlow<Status?> = MutableStateFlow(null)
    val status: StateFlow<Status?> = _status

    @OptIn(DelicateCoroutinesApi::class)
    fun migrate() = GlobalScope.launch(Dispatchers.IO) {
        try {
            _status.value = Status.Init

            val migrateTorrent = migrateTorrent.value
            val migrateWebM3u = migrateWebM3u.value
            logger.info { "[migration] Migration started. torrent: $migrateTorrent, web: $migrateWebM3u" }

            val newBasePath = getNewBaseSaveDir()
            if (newBasePath == null) {
                logger.error { "[migration] Failed to get external files directory while migrating cache." }
                _status.value = Status.Error(IllegalStateException("Shared storage is not currently available."))
                return@launch
            }

            if (migrateTorrent) {
                _status.value = Status.TorrentCache(null, 0, 0)
                // hard-coded directory name before 4.9
                val prevPath = getPrevTorrentSaveDir()
                logger.info { "[migration] Start move torrent cache from $prevPath to $newBasePath" }

                if (prevPath.exists() && prevPath.isDirectory()) {
                    val totalSize = prevPath.actualSize()
                    var migratedSize = 0L

                    prevPath.moveDirectoryRecursively(newBasePath) {
                        _status.value = Status.TorrentCache(it.name, totalSize, migratedSize)
                        migratedSize += it.actualSize()
                    }
                }
                logger.info { "[migration] Move torrent cache complete, destination path: $newBasePath" }

                val torrentStorage = mediaCacheManager.storagesIncludingDisabled
                    .find { it is DataStoreMediaCacheStorage && it.engine is TorrentMediaCacheEngine }

                if (torrentStorage == null) {
                    logger.error("[migration] Failed to get TorrentMediaCacheEngine, it is null.")
                    withContext(Dispatchers.Main) {
                        _status.value =
                            Status.Error(IllegalStateException("Media cache storage with engine TorrentMediaCacheEngine is not found."))
                    }
                    return@launch
                }

                _status.value = Status.Metadata
                metadataStore.updateData { original ->
                    val nonTorrentMetadata = original.filter { it.engine != torrentStorage.engine.engineKey }
                    val torrentMetadata = original.filter { it.engine == torrentStorage.engine.engineKey }

                    nonTorrentMetadata + torrentMetadata.map { save ->
                        save.copy(
                            metadata = torrentStorage.engine
                                .modifyMetadataForMigration(save.metadata, newBasePath.path),
                        )
                    }
                }

                logger.info { "[migration] Migrate metadata of torrent cache complete." }
            }

            if (migrateWebM3u) {
                _status.value = Status.WebM3uCache(null, 0, 0)
                // hard-coded directory name before 4.11
                val prevPath = context.files.dataDir.resolve("web-m3u-cache")
                // new path is also hard coded, see android koin modules.
                val newPath = newBasePath.resolve("web-m3u")
                logger.info { "[migration] Start move web m3u cache from $prevPath to $newPath" }

                if (prevPath.exists() && prevPath.isDirectory()) {
                    val totalSize = prevPath.actualSize()
                    var migratedSize = 0L

                    prevPath.moveDirectoryRecursively(newPath) {
                        _status.value = Status.WebM3uCache(it.name, totalSize, migratedSize)
                        migratedSize += it.actualSize()
                    }
                }
                logger.info { "[migration] Move web m3u cache complete, destination path: $newBasePath" }

                val webStorage = mediaCacheManager.storagesIncludingDisabled
                    .find { it is DataStoreMediaCacheStorage && it.engine is HttpMediaCacheEngine }

                if (webStorage == null) {
                    logger.error("[migration] Failed to get HttpMediaCacheEngine, it is null.")
                    withContext(Dispatchers.Main) {
                        _status.value =
                            Status.Error(IllegalStateException("Media cache storage with engine HttpMediaCacheEngine is not found."))
                    }
                    return@launch
                }

                _status.value = Status.Metadata
                metadataStore.updateData { original ->
                    val nonWebMetadata = original.filter { it.engine != webStorage.engine.engineKey }
                    val webMetadata = original.filter { it.engine == webStorage.engine.engineKey }

                    nonWebMetadata + webMetadata.map { save ->
                        save.copy(
                            metadata = webStorage.engine.modifyMetadataForMigration(save.metadata, newPath.path),
                        )
                    }
                }

                m3u8DownloaderStore.updateData { original ->
                    original.map { state ->
                        state.copy(
                            outputPath = newPath
                                .resolve(state.outputPath.substringAfter("web-m3u-cache"))
                                .absolutePath,
                            segmentCacheDir = newPath
                                .resolve(state.segmentCacheDir.substringAfter("web-m3u-cache"))
                                .absolutePath,
                            segments = state.segments.map { seg ->
                                seg.copy(
                                    tempFilePath = newPath
                                        .resolve(seg.tempFilePath.substringAfter("web-m3u-cache"))
                                        .absolutePath,
                                )
                            },
                        )
                    }
                }

                logger.info { "[migration] Migrate metadata of web m3u cache complete." }
            }

            settingsRepo.mediaCacheSettings.update { copy(saveDir = newBasePath.absolutePath) }
            logger.info { "[migration] Migration success." }

            delay(500)
            appTerminator.exitApp(context, 0)
        } catch (e: Exception) {
            _status.value = Status.Error(e)
            logger.error(e) { "[migration] Failed to migrate torrent cache." }
        }
    }

    sealed interface Status {
        object Init : Status

        sealed class Cache(val currentFile: String?, val totalSize: Long, val migratedSize: Long) : Status

        class TorrentCache(currentFile: String?, totalSize: Long, migratedSize: Long) :
            Cache(currentFile, totalSize, migratedSize)

        class WebM3uCache(currentFile: String?, totalSize: Long, migratedSize: Long) :
            Cache(currentFile, totalSize, migratedSize)

        object Metadata : Status

        class Error(val throwable: Throwable? = null) : Status
    }

}
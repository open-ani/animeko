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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.InvalidMediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.files
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.useDirectoryEntries
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import kotlin.sequences.forEach

/**
 * Since 4.8, metadata of media cache is stored in the datastore. Migration workaround.
 *
 * Since 4.9, Default directory of torrent cache is changed to external/shared storage and
 * cannot be changed. This is the workaround for startup migration.
 *
 * Since 4.11, Default directory of web m3u cache is changed to external/shared storage (Android) and
 * media cache directory (Desktop). This is the workaround for startup migration.
 *
 */
class MediaCacheMigrator(
    private val context: ContextMP,
    private val metadataStore: DataStore<List<MediaCacheSave>>,
    private val m3u8DownloaderStore: DataStore<List<DownloadState>>,
    private val mediaCacheManager: MediaCacheManager,
    private val settingsRepo: SettingsRepository,
    private val appTerminator: AppTerminator,
    private val migrationChecker: MigrationChecker,
    private val getNewBaseSaveDir: suspend () -> SystemPath?,
    private val getLegacyTorrentSaveDir: suspend () -> SystemPath
) : KoinComponent {
    private val logger = logger<MediaCacheMigrator>()

    private val _status: MutableStateFlow<Status?> = MutableStateFlow(null)
    val status: StateFlow<Status?> = _status

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun startupMigrationCheckAndGetIfRequiresMigration(scope: CoroutineScope): Boolean {
        try {
            migrateMediaCacheMetadata() // 无条件尝试迁移元数据

            val migrateTorrent = migrationChecker.requireMigrateTorrentCache()
            val migrateWebM3u = migrationChecker.requireMigrateWebM3uCache()

            val requiresMigration = migrateTorrent || migrateWebM3u
            if (requiresMigration) {
                scope.launch(Dispatchers.IO_) {
                    migrateMediaCacheDownloads(migrateTorrent, migrateWebM3u)
                }
            }

            return requiresMigration
        } catch (e: Exception) {
            logger.error(e) { "[migration] Failed to migrate media cache." }
            return false
        }
    }

    @Suppress("DEPRECATION")
    @OptIn(InvalidMediaCacheEngineKey::class)
    private suspend fun migrateMediaCacheMetadata() {
        val storages = mediaCacheManager.storagesIncludingDisabled
        val legacyMetadataDir = context.files.dataDir.resolve("media-cache")

        storages
            .firstOrNull { it.engine is TorrentMediaCacheEngine }
            .let { storage ->
                if (storage == null) return@let

                val dir = legacyMetadataDir.resolve("anitorrent")
                if (dir.exists() && dir.isDirectory()) {
                    migrateMetadata(metadataStore, storage, dir)
                }
            }
        storages
            .filterIsInstance<DataStoreMediaCacheStorage>()
            .firstOrNull { it.engine is HttpMediaCacheEngine }
            .let { storage ->
                if (storage == null) return@let

                val dir = legacyMetadataDir.resolve("web-m3u")
                if (dir.exists() && dir.isDirectory()) {
                    migrateMetadata(metadataStore, storage, dir)
                }
            }

        // Delete the whole metadata dir
        legacyMetadataDir.deleteRecursively()
    }

    @Suppress("DEPRECATION")
    private suspend fun migrateMediaCacheDownloads(
        migrateTorrent: Boolean,
        migrateWebM3u: Boolean
    ) {
        try {
            _status.value = Status.Init
            logger.info { "[migration] Migration started. torrent: $migrateTorrent, web: $migrateWebM3u" }

            val newBasePath = getNewBaseSaveDir()
            if (newBasePath == null) {
                logger.error { "[migration] Failed to get base directory path of media cache while migrating cache." }
                _status.value =
                    Status.Error(IllegalStateException("Directory path of media cache is not available."))
                return
            }

            if (migrateTorrent) {
                _status.value = Status.TorrentCache(null, 0, 0)
                // hard-coded directory name before 4.9
                val prevPath = getLegacyTorrentSaveDir()
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
                    _status.value =
                        Status.Error(IllegalStateException("Media cache storage with engine TorrentMediaCacheEngine is not found."))
                    return
                }

                logger.info { "[migration] Migrate metadata of torrent cache complete." }
            }

            if (migrateWebM3u) {
                _status.value = Status.WebM3uCache(null, 0, 0)
                val prevPath =
                    context.files.dataDir.resolve(HttpMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR)
                val newPath = newBasePath.resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR)
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
                    _status.value =
                        Status.Error(IllegalStateException("Media cache storage with engine HttpMediaCacheEngine is not found."))
                    return
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

    @Deprecated("Since 4.8, metadata is now stored in the datastore. This method is for migration only.")
    @OptIn(InvalidMediaCacheEngineKey::class)
    private suspend fun migrateMetadata(
        metadataStore: DataStore<List<MediaCacheSave>>,
        storage: MediaCacheStorage,
        dir: SystemPath
    ) = dir.useDirectoryEntries { entries ->
        entries.forEach { file ->
            val save = try {
                DataStoreJson.decodeFromString(LegacyMediaCacheSaveSerializer, file.readText())
                    .copy(engine = storage.engine.engineKey)
            } catch (e: SerializationException) {
                logger.error(e) { "[migration] Failed to deserialize metadata file ${file.name}, ignoring migration." }
                return@useDirectoryEntries
            }

            metadataStore.updateData { originalList ->
                val existing = originalList.indexOfFirst {
                    it.origin.mediaId == save.origin.mediaId &&
                            it.metadata.subjectId == save.metadata.subjectId &&
                            it.metadata.episodeId == save.metadata.episodeId
                }
                if (existing != -1) {
                    logger.warn {
                        "[migration] Duplicated media cache metadata ${originalList[existing].origin.mediaId} found while migrating, " +
                                "override to new ${save.origin.mediaId}, engine: ${save.engine}."
                    }
                    originalList.toMutableList().apply {
                        removeAt(existing)
                        add(save)
                    }
                } else {
                    logger.info { "[migration] Migrating media cache metadata ${save.origin.mediaId}, engine: ${storage.engine.engineKey}." }
                    originalList + save
                }
            }
        }
    }

    sealed interface Status {
        object Init : Status

        sealed class Cache(val currentFile: String?, val totalSize: Long, val migratedSize: Long) :
            Status

        class TorrentCache(currentFile: String?, totalSize: Long, migratedSize: Long) :
            Cache(currentFile, totalSize, migratedSize)

        class WebM3uCache(currentFile: String?, totalSize: Long, migratedSize: Long) :
            Cache(currentFile, totalSize, migratedSize)

        object Metadata : Status

        class Error(val throwable: Throwable? = null) : Status
    }

    interface MigrationChecker {
        suspend fun requireMigrateTorrentCache(): Boolean
        suspend fun requireMigrateWebM3uCache(): Boolean
    }
}
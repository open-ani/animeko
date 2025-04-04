/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import android.content.Context
import android.os.Environment
import android.widget.Toast
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
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent

/**
 * Since 4.9, Default directory of torrent cache is changed to external/shared storage and
 * cannot be changed. This is the workaround for startup migration.
 *
 * This class should be called only `AniApplication.Instance.requiresTorrentCacheMigration` is true,
 * which means we are going to migrate torrent caches from internal storage to shared/external storage.
 */
class TorrentCacheMigrator(
    private val context: Context,
    private val metadataStore: DataStore<List<MediaCacheSave>>,
    private val mediaCacheManager: MediaCacheManager,
    private val settingsRepo: SettingsRepository,
    private val appTerminator: AppTerminator,
) : KoinComponent {
    private val logger = logger<TorrentCacheMigrator>()

    private val _status: MutableStateFlow<Status?> = MutableStateFlow(null)
    val status: StateFlow<Status?> = _status

    @OptIn(DelicateCoroutinesApi::class)
    fun migrateTorrentCache() = GlobalScope.launch(Dispatchers.IO) {
        try {
            _status.value = Status.Init
            // hard-coded directory name before 4.9
            val prevPath = context.filesDir.resolve("torrent-caches").toPath().toKtPath().inSystem
            val newPath = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toPath()?.toKtPath()?.inSystem

            _status.value = Status.Cache(null, 0, 0)
            if (newPath == null) {
                logger.error { "[migration] Failed to get external files dir while migrating cache." }
                _status.value = Status.Error(IllegalStateException("Shared storage is not currently available."))
                return@launch
            }

            logger.info { "[migration] Start move from $prevPath to $newPath" }
            if (prevPath.exists() && prevPath.isDirectory()) {
                val totalSize = prevPath.actualSize()
                var migratedSize = 0L

                prevPath.moveDirectoryRecursively(newPath) {
                    _status.value = Status.Cache(it.name, totalSize, migratedSize)
                    migratedSize += it.actualSize()
                }
            }
            logger.info { "[migration] Move complete." }

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
            logger.info { "[migration] New torrent dir: $newPath" }

            _status.value = Status.Metadata
            metadataStore.updateData { original ->
                val nonTorrentMetadata = original.filter { it.engine != torrentStorage.engine.engineKey }
                val torrentMetadata = original.filter { it.engine == torrentStorage.engine.engineKey }

                nonTorrentMetadata + torrentMetadata.map { save ->
                    save.copy(
                        metadata = torrentStorage.engine
                            .modifyMetadataForMigration(save.metadata, newPath.path),
                    )
                }
            }

            settingsRepo.mediaCacheSettings.update { copy(saveDir = newPath.absolutePath) }
            logger.info { "[migration] Migration success." }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "缓存迁移完成", Toast.LENGTH_SHORT).show()
            }
            delay(500)
            appTerminator.exitApp(context, 0)
        } catch (e: Exception) {
            _status.value = Status.Error(e)
            logger.error(e) { "[migration] Failed to migrate torrent cache." }
        }
    }

    sealed interface Status {
        object Init : Status

        class Cache(val currentFile: String?, val totalSize: Long, val migratedSize: Long) : Status

        object Metadata : Status

        class Error(val throwable: Throwable? = null) : Status
    }

}